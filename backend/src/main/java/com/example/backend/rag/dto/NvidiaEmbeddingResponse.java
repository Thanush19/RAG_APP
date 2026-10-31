package com.example.backend.rag.dto;

import java.util.List;

public record NvidiaEmbeddingResponse(
        List<EmbeddingData> data
) {

    public record EmbeddingData(
            List<Double> embedding
    ) {
    }
}