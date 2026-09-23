package io.collectra.api.shared.error;

public class TenantNotFoundException extends RuntimeException {
    public TenantNotFoundException() {
        super("Tenant not found");
    }
}
