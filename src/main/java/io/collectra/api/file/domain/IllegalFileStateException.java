package io.collectra.api.file.domain;

public class IllegalFileStateException extends RuntimeException {
    public IllegalFileStateException(String message) {
        super(message);
    }
}
