package com.example.backend.rag.ask;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class RagAnswer {

    private final String answer;

    private final List<RagSource> sources;
}
