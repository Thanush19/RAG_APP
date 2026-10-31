package com.example.backend.common.exception;

public class VectorStoreUnavailableException extends UpstreamServiceException {

    public VectorStoreUnavailableException(Throwable cause) {
        super("Vector store is unavailable", cause);
    }
}
