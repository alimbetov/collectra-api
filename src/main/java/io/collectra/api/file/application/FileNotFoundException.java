package io.collectra.api.file.application;

import java.util.UUID;

public class FileNotFoundException extends RuntimeException {
    public FileNotFoundException(UUID fileId) {
        super("File not found: " + fileId);
    }
}
