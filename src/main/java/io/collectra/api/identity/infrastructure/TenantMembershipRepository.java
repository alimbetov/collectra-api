package io.collectra.api.identity.infrastructure;

import io.collectra.api.identity.domain.TenantMembership;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, UUID> {
    Optional<TenantMembership> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    Optional<TenantMembership> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select membership from TenantMembership membership where membership.id = :id")
    Optional<TenantMembership> findByIdForUpdate(@Param("id") UUID id);

    List<TenantMembership> findAllByTenantId(UUID tenantId);

    boolean existsByTenantIdAndUserIdAndStatus(UUID tenantId, UUID userId, String status);
}
