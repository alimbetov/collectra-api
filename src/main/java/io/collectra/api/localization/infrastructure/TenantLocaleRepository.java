package io.collectra.api.localization.infrastructure;

import io.collectra.api.localization.domain.TenantLocale;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantLocaleRepository extends JpaRepository<TenantLocale, UUID> {
    List<TenantLocale> findAllByTenantIdAndEnabledTrueOrderBySortOrderAscLocaleAsc(UUID tenantId);

    Optional<TenantLocale> findByTenantIdAndLocale(UUID tenantId, String locale);

    Optional<TenantLocale> findByTenantIdAndEnabledTrueAndDefaultLocaleTrue(UUID tenantId);

    boolean existsByTenantIdAndLocaleAndEnabledTrue(UUID tenantId, String locale);
}
