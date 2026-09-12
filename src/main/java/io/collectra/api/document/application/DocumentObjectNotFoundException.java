package io.collectra.api.document.application;

public class DocumentObjectNotFoundException extends RuntimeException {
    public DocumentObjectNotFoundException(String key, Throwable cause) {
        super("Generated document object not found: " + key, cause);
    }
}
