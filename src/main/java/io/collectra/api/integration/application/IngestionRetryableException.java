package io.collectra.api.integration.application;

public class IngestionRetryableException extends RuntimeException {
    public IngestionRetryableException(String message, Throwable cause) { super(message, cause); }
}
