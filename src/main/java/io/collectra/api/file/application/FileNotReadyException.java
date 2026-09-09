package io.collectra.api.file.application;

import io.collectra.api.file.domain.FileStatus;
import java.util.UUID;

public class FileNotReadyException extends RuntimeException {
    public FileNotReadyException(UUID fileId, FileStatus status) {
        super("File " + fileId + " is not available in status " + status);
    }
}
