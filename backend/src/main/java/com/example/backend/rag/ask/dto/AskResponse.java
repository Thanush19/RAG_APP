package com.example.backend.rag.ask.dto;

import com.example.backend.rag.ask.RagSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class AskResponse {

    private final String answer;

    private final List<RagSource> sources;
}
