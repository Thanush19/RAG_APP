package com.example.backend.rag.vector;

import com.example.backend.common.exception.VectorStoreUnavailableException;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Common.PointId;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.ScrollPoints;
import io.qdrant.client.grpc.Points.ScrollResponse;
import io.qdrant.client.grpc.Points.SearchPoints;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static io.qdrant.client.ConditionFactory.matchKeyword;
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

@Service
@RequiredArgsConstructor
public class QdrantVectorStoreService implements VectorStoreService {

    private static final Logger log =
            LoggerFactory.getLogger(QdrantVectorStoreService.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MILLIS = 300;
    private static final int SCROLL_PAGE_SIZE = 256;

    private final QdrantClient qdrantClient;

    @Value("${app.vector.qdrant.collection-name}")
    private String collectionName;

    @Value("${app.vector.qdrant.timeout-seconds:30}")
    private int timeoutSeconds;

    @Override
    public void storeChunk(
            UUID userId,
            UUID documentId,
            UUID chunkId,
            Integer pageNumber,
            float[] embedding
    ) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("Embedding must not be empty");
        }

        PointStruct point = PointStruct.newBuilder()
                .setId(id(chunkId))
                .setVectors(vectors(embedding))
                .putAllPayload(
                        Map.of(
                                "userId", value(userId.toString()),
                                "documentId", value(documentId.toString()),
                                "chunkId", value(chunkId.toString()),
                                "pageNumber", value(pageNumber)
                        )
                )
                .build();

        // Upsert by deterministic chunk ID is idempotent, so retries are safe.
        withRetry(
                "store chunk " + chunkId,
                () -> qdrantClient.upsertAsync(
                        collectionName,
                        List.of(point),
                        Duration.ofSeconds(timeoutSeconds)
                )
        );
    }

    @Override
    public List<VectorMatch> searchSimilar(
            UUID userId,
            float[] queryEmbedding,
            int topK
    ) {
        return searchSimilar(userId, queryEmbedding, topK, null);
    }

    @Override
    public List<VectorMatch> searchSimilar(
            UUID userId,
            float[] queryEmbedding,
            int topK,
            UUID documentIdFilter
    ) {
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            throw new IllegalArgumentException("Query embedding must not be empty");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }

        List<Float> vector = new ArrayList<>(queryEmbedding.length);
        for (float value : queryEmbedding) {
            vector.add(value);
        }

        Filter.Builder filter = Filter.newBuilder()
                .addMust(matchKeyword("userId", userId.toString()));

        if (documentIdFilter != null) {
            filter.addMust(
                    matchKeyword("documentId", documentIdFilter.toString())
            );
        }

        SearchPoints request = SearchPoints.newBuilder()
                .setCollectionName(collectionName)
                .addAllVector(vector)
                .setLimit(topK)
                .setFilter(filter.build())
                .build();

        // Search is latency-sensitive on the user request path:
        // single attempt, bounded by the gRPC deadline.
        List<ScoredPoint> points = await(
                () -> qdrantClient.searchAsync(
                        request,
                        Duration.ofSeconds(timeoutSeconds)
                ),
                "search for user " + userId
        );

        List<VectorMatch> matches = new ArrayList<>(points.size());

        for (ScoredPoint point : points) {
            PointId pointId = point.getId();

            if (!pointId.hasUuid()) {
                continue;
            }

            matches.add(
                    new VectorMatch(
                            UUID.fromString(pointId.getUuid()),
                            point.getScore()
                    )
            );
        }

        return matches;
    }

    @Override
    public void deleteDocumentVectors(
            UUID userId,
            UUID documentId
    ) {
        Filter filter = Filter.newBuilder()
                .addMust(matchKeyword("userId", userId.toString()))
                .addMust(matchKeyword("documentId", documentId.toString()))
                .build();

        // Filtered delete is idempotent, so retries are safe.
        withRetry(
                "delete vectors for document " + documentId,
                () -> qdrantClient.deleteAsync(
                        collectionName,
                        filter,
                        Duration.ofSeconds(timeoutSeconds)
                )
        );
    }

    @Override
    public List<VectorStoreDocumentRef> scanStoredDocumentRefs() {

        Map<UUID, UUID> userIdByDocumentId = new LinkedHashMap<>();

        PointId offset = null;
        boolean firstPage = true;

        while (true) {
            ScrollPoints.Builder builder = ScrollPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .setLimit(SCROLL_PAGE_SIZE)
                    .setWithPayload(
                            WithPayloadSelectorFactory.include(
                                    List.of("userId", "documentId")
                            )
                    );

            if (!firstPage) {
                builder.setOffset(offset);
            }

            ScrollResponse response = await(
                    () -> qdrantClient.scrollAsync(
                            builder.build(),
                            Duration.ofSeconds(timeoutSeconds)
                    ),
                    "scroll collection " + collectionName
            );

            for (RetrievedPoint point : response.getResultList()) {
                String userId = payloadString(point, "userId");
                String documentId = payloadString(point, "documentId");

                if (userId == null || documentId == null) {
                    continue;
                }

                try {
                    userIdByDocumentId.put(
                            UUID.fromString(documentId),
                            UUID.fromString(userId)
                    );

                } catch (IllegalArgumentException ignored) {
                    // Malformed payload cannot be reconciled; sweep skips it.
                }
            }

            if (response.getResultCount() == 0
                    || !response.hasNextPageOffset()) {
                break;
            }

            offset = response.getNextPageOffset();
            firstPage = false;
        }

        List<VectorStoreDocumentRef> refs = new ArrayList<>();

        userIdByDocumentId.forEach((documentId, userId) ->
                refs.add(
                        VectorStoreDocumentRef.builder()
                                .documentId(documentId)
                                .userId(userId)
                                .build()
                )
        );

        return refs;
    }

    private String payloadString(RetrievedPoint point, String key) {
        JsonWithInt.Value payloadValue =
                point.getPayloadOrDefault(key, JsonWithInt.Value.getDefaultInstance());

        return payloadValue.hasStringValue()
                ? payloadValue.getStringValue()
                : null;
    }

    private <T> T await(
            Supplier<? extends Future<T>> callSupplier,
            String operation
    ) {
        try {
            return callSupplier.get().get(timeoutSeconds + 5L, TimeUnit.SECONDS);

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new VectorStoreUnavailableException(exception);

        } catch (ExecutionException | TimeoutException exception) {
            throw new VectorStoreUnavailableException(exception);
        }
    }

    private void withRetry(
            String operation,
            Supplier<? extends Future<?>> callSupplier
    ) {
        long backoffMillis = INITIAL_BACKOFF_MILLIS;
        VectorStoreUnavailableException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                callSupplier.get().get(timeoutSeconds + 5L, TimeUnit.SECONDS);
                return;

            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new VectorStoreUnavailableException(exception);

            } catch (ExecutionException | TimeoutException exception) {
                lastFailure = new VectorStoreUnavailableException(exception);
                log.warn(
                        "Qdrant operation '{}' failed on attempt {}/{}: {}",
                        operation,
                        attempt,
                        MAX_ATTEMPTS,
                        exception.getMessage()
                );

                if (attempt < MAX_ATTEMPTS) {
                    sleep(backoffMillis);
                    backoffMillis *= 2;
                }
            }
        }

        throw lastFailure;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new VectorStoreUnavailableException(exception);
        }
    }
}
