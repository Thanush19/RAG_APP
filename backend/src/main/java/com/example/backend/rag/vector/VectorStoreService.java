package com.example.backend.rag.vector;

import java.util.List;
import java.util.UUID;

public interface VectorStoreService {

    void storeChunk(
            UUID userId,
            UUID documentId,
            UUID chunkId,
            Integer pageNumber,
            float[] embedding
    );

    List<VectorMatch> searchSimilar(
            UUID userId,
            float[] queryEmbedding,
            int topK
    );

    void deleteDocumentVectors(
            UUID userId,
            UUID documentId
    );
}