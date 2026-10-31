package com.example.backend.common.exception;

public abstract class UpstreamServiceException extends RuntimeException {

    protected UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
