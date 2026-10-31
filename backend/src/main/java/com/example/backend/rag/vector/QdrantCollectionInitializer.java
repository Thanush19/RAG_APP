package com.example.backend.rag.vector;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.PayloadSchemaType;
import io.qdrant.client.grpc.Collections.VectorParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QdrantCollectionInitializer {

    private static final int VECTOR_SIZE = 2048;

    private final QdrantClient qdrantClient;

    @Value("${app.vector.qdrant.collection-name}")
    private String collectionName;

    @PostConstruct
    public void initialize() {
        try {
            boolean exists = qdrantClient
                    .collectionExistsAsync(collectionName)
                    .get();

            if (exists) {
                ensureUserIdIndex();
                return;
            }

            qdrantClient
                    .createCollectionAsync(
                            collectionName,
                            VectorParams.newBuilder()
                                    .setSize(VECTOR_SIZE)
                                    .setDistance(Distance.Cosine)
                                    .build()
                    )
                    .get();

            ensureUserIdIndex();

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to initialize Qdrant collection: "
                            + collectionName,
                    exception
            );
        }
    }

    private void ensureUserIdIndex() throws Exception {
        qdrantClient
                .createPayloadIndexAsync(
                        collectionName,
                        "userId",
                        PayloadSchemaType.Keyword,
                        null,
                        true,
                        null,
                        null
                )
                .get();
    }
}