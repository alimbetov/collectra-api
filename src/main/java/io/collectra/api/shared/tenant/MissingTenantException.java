package io.collectra.api.shared.tenant;

public class MissingTenantException extends RuntimeException {
    public MissingTenantException() {
        super("Tenant context is required");
    }
}
