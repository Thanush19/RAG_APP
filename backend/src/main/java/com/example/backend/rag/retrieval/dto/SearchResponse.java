package com.example.backend.rag.retrieval.dto;

import com.example.backend.rag.retrieval.RetrievedChunk;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class SearchResponse {

    private final List<RetrievedChunk> results;
}
