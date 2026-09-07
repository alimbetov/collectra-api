package io.collectra.api.tenant.infrastructure;

import io.collectra.api.tenant.domain.Tenant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    boolean existsBySlugIgnoreCase(String slug);
}
