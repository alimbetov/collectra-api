package io.collectra.api.integration.infrastructure;

import io.collectra.api.integration.domain.IntegrationSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationSourceRepository extends JpaRepository<IntegrationSource, UUID> {
    List<IntegrationSource> findAllByTenantIdOrderByCodeAsc(UUID tenantId);

    Optional<IntegrationSource> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<IntegrationSource> findByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
