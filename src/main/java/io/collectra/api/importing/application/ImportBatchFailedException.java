package io.collectra.api.importing.application;

import java.util.UUID;

public class ImportBatchFailedException extends RuntimeException {
    private final UUID batchId;
    private final String errorCode;

    public ImportBatchFailedException(UUID batchId, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.batchId = batchId;
        this.errorCode = errorCode;
    }

    public UUID getBatchId() { return batchId; }
    public String getErrorCode() { return errorCode; }
}
