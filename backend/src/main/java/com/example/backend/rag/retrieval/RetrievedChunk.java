package com.example.backend.rag.retrieval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class RetrievedChunk {

    private final UUID chunkId;

    private final UUID documentId;

    private final Integer pageNumber;

    private final String text;

    private final float score;
}
