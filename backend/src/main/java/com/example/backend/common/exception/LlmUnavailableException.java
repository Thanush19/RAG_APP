package com.example.backend.common.exception;

public class LlmUnavailableException extends UpstreamServiceException {

    public LlmUnavailableException(Throwable cause) {
        super("LLM service is unavailable", cause);
    }
}
