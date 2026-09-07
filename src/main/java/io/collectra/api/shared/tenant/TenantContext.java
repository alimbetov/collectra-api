package io.collectra.api.shared.tenant;

import java.util.UUID;

public final class TenantContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static UUID requireTenantId() {
        UUID tenantId = CURRENT.get();
        if (tenantId == null) throw new MissingTenantException();
        return tenantId;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
