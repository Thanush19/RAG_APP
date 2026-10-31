package com.example.backend.rag.vector;

import java.util.UUID;

public interface VectorStoreService {

    void storeChunk(
            UUID userId,
            UUID documentId,
            UUID chunkId,
            Integer pageNumber,
            float[] embedding
    );
}