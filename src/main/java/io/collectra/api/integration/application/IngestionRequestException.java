package io.collectra.api.integration.application;

public class IngestionRequestException extends RuntimeException {
    private final String code;
    public IngestionRequestException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}
