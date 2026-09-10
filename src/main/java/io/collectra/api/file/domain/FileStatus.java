package io.collectra.api.file.domain;

public enum FileStatus {
    UPLOADING,
    READY,
    DELETE_PENDING,
    DELETE_FAILED,
    DELETED,
    FAILED,
    QUARANTINED
}
