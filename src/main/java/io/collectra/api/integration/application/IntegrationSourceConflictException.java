package io.collectra.api.integration.application;

import io.collectra.api.shared.error.BusinessConflictException;

public class IntegrationSourceConflictException extends BusinessConflictException {
    public IntegrationSourceConflictException(String code, String message) {
        super(code, message);
    }
}
