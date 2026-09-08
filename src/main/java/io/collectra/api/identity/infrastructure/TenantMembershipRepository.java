package io.collectra.api.identity.infrastructure;

import io.collectra.api.identity.domain.TenantMembership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, UUID> {
    Optional<TenantMembership> findByTenantIdAndUserId(UUID tenantId, UUID userId);
    Optional<TenantMembership> findByIdAndTenantId(UUID id, UUID tenantId);
    List<TenantMembership> findAllByTenantId(UUID tenantId);
}
