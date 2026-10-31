package com.example.backend.rag.vector;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.qdrant.client.ConditionFactory.matchKeyword;
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Common.PointId;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;

@Service
@RequiredArgsConstructor
public class QdrantVectorStoreService implements VectorStoreService {

    private final QdrantClient qdrantClient;

    @Value("${app.vector.qdrant.collection-name}")
    private String collectionName;

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

        try {
            qdrantClient
                    .upsertAsync(
                            collectionName,
                            List.of(point)
                    )
                    .get();

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to store chunk vector in Qdrant: " + chunkId,
                    exception
            );
        }
    }

    @Override
    public List<VectorMatch> searchSimilar(
            UUID userId,
            float[] queryEmbedding,
            int topK
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

        SearchPoints request = SearchPoints.newBuilder()
                .setCollectionName(collectionName)
                .addAllVector(vector)
                .setLimit(topK)
                .setFilter(
                        Filter.newBuilder()
                                .addMust(matchKeyword("userId", userId.toString()))
                                .build()
                )
                .build();

        try {
            List<ScoredPoint> points = qdrantClient
                    .searchAsync(request)
                    .get();

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

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while searching Qdrant for user: " + userId,
                    exception
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to search chunk vectors in Qdrant for user: " + userId,
                    exception
            );
        }
    }
}