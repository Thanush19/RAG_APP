package com.example.backend.rag.ask.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class StreamErrorPayload {

    private final String code;
    private final String message;
}
