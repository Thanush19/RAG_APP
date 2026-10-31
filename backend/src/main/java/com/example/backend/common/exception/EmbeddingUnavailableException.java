package com.example.backend.common.exception;

public class EmbeddingUnavailableException extends UpstreamServiceException {

    public EmbeddingUnavailableException(Throwable cause) {
        super("Embedding service is unavailable", cause);
    }
}
