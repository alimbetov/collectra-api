package io.collectra.api.file.application;

public class FileTooLargeException extends RuntimeException {
    public FileTooLargeException(long actualBytes, long maxBytes) {
        super("File size " + actualBytes + " exceeds direct upload limit " + maxBytes);
    }
}
