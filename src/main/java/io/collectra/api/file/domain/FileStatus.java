package io.collectra.api.file.domain;

public enum FileStatus {
    UPLOADING,
    READY,
    DELETE_PENDING,
    DELETED,
    FAILED,
    QUARANTINED
}
