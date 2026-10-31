package com.example.backend.rag.vector;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.PointStruct;

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
}