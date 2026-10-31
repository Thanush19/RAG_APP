package com.example.backend.rag.embedding;

public interface EmbeddingService {

    float[] embedPassage(String text);

    float[] embedQuery(String text);
}