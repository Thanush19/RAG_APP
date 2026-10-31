package com.example.backend.common.exception;

import com.example.backend.common.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {

        List<ApiErrorResponse.FieldError> errors =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(error -> new ApiErrorResponse.FieldError(
                                error.getField(),
                                error.getDefaultMessage()
                        ))
                        .toList();

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed",
                request,
                errors
        );
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEntityNotFound(
            EntityNotFoundException exception,
            HttpServletRequest request
    ) {

        return buildResponse(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            ConflictException exception,
            HttpServletRequest request
    ) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "CONFLICT",
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailAlreadyRegistered(
            EmailAlreadyRegisteredException exception,
            HttpServletRequest request
    ) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_REGISTERED",
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleUpstreamUnavailable(
            UpstreamServiceException exception,
            HttpServletRequest request
    ) {

        log.warn(
                "Upstream dependency failure. requestId={}, path={}, cause={}",
                MDC.get("requestId"),
                request.getRequestURI(),
                exception.getMessage()
        );

        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "UPSTREAM_UNAVAILABLE",
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(DocumentProcessingException.class)
    public ResponseEntity<ApiErrorResponse> handleDocumentProcessing(
            DocumentProcessingException exception,
            HttpServletRequest request
    ) {

        log.error(
                "Document ingestion failed. requestId={}, path={}",
                MDC.get("requestId"),
                request.getRequestURI(),
                exception
        );

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "DOCUMENT_PROCESSING_FAILED",
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {

        log.error(
                "Unhandled exception. requestId={}, method={}, path={}",
                MDC.get("requestId"),
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                request,
                List.of()
        );
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<ApiErrorResponse.FieldError> errors
    ) {

        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                errors,
                MDC.get("requestId")
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }
}