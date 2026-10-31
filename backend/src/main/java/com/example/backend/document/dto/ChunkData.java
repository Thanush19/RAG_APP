package com.example.backend.document.dto;

public record ChunkData(
        Integer pageNumber,
        String text
) {
}