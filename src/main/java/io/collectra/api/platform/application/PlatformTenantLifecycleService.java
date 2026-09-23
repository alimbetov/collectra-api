package io.collectra.api.platform.application;

import io.collectra.api.audit.application.SecurityAuditService;
import io.collectra.api.identity.infrastructure.RefreshSessionRepository;
import io.collectra.api.shared.error.BusinessConflictException;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformTenantLifecycleService {
    private final TenantRepository tenants;
    private final RefreshSessionRepository sessions;
    private final SecurityAuditService audit;

    public PlatformTenantLifecycleService(
            TenantRepository tenants,
            RefreshSessionRepository sessions,
            SecurityAuditService audit) {
        this.tenants = tenants;
        this.sessions = sessions;
        this.audit = audit;
    }

    @Transactional
    public Tenant changeStatus(
            UUID actorId,
            UUID tenantId,
            boolean active,
            long expectedRevision,
            String reason) {
        Tenant tenant =
                tenants
                        .findByIdForUpdate(tenantId)
                        .orElseThrow(() -> new NoSuchElementException("Tenant not found"));

        if (tenant.getVersion() != expectedRevision) {
            throw new BusinessConflictException(
                    "VERSION_CONFLICT",
                    "Tenant revision conflict: expected "
                            + expectedRevision
                            + " but was "
                            + tenant.getVersion());
        }

        if (active) {
            if (tenant.active()) {
                throw new BusinessConflictException(
                        "INVALID_TENANT_STATUS_TRANSITION", "Tenant is already active");
            }
            tenant.activate();
            audit.appendTransactional(
                    tenantId,
                    "PLATFORM_USER",
                    actorId,
                    "TENANT_ACTIVATED",
                    "SUCCEEDED",
                    reason);
        } else {
            if (!tenant.active()) {
                throw new BusinessConflictException(
                        "INVALID_TENANT_STATUS_TRANSITION", "Tenant is already blocked");
            }
            tenant.block();
            sessions.revokeAllByTenantId(tenantId);
            audit.appendTransactional(
                    tenantId,
                    "PLATFORM_USER",
                    actorId,
                    "TENANT_BLOCKED",
                    "SUCCEEDED",
                    reason);
        }
        return tenant;
    }
}
