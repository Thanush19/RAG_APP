package com.example.backend.rag.vector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class VectorMatch {

    private final UUID chunkId;

    private final float score;
}
