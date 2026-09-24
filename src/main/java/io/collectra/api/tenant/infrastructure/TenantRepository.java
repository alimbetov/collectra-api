package io.collectra.api.tenant.infrastructure;

import io.collectra.api.tenant.domain.Tenant;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    boolean existsBySlugIgnoreCase(String slug);

    Optional<Tenant> findBySlugIgnoreCase(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select tenant from Tenant tenant where tenant.id = :tenantId")
    Optional<Tenant> findByIdForUpdate(@Param("tenantId") UUID tenantId);
}
