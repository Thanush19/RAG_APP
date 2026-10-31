package com.example.backend.rag.ask;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class RagSource {

    private final UUID chunkId;

    private final UUID documentId;

    private final Integer pageNumber;

    private final float score;
}
