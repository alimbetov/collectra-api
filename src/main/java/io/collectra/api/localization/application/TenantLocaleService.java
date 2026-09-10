package io.collectra.api.localization.application;

import io.collectra.api.localization.domain.TenantLocale;
import io.collectra.api.localization.infrastructure.TenantLocaleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantLocaleService {
    private final TenantLocaleRepository tenantLocales;
    private final SupportedLocaleService supportedLocales;

    public TenantLocaleService(
            TenantLocaleRepository tenantLocales,
            SupportedLocaleService supportedLocales) {
        this.tenantLocales = tenantLocales;
        this.supportedLocales = supportedLocales;
    }

    @Transactional(readOnly = true)
    public List<TenantLocale> listEnabled(UUID tenantId) {
        return tenantLocales.findAllByTenantIdAndEnabledTrueOrderBySortOrderAscLocaleAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID tenantId, String locale) {
        String canonical = supportedLocales.canonicalize(locale);
        supportedLocales.requireSupported(canonical);
        return tenantLocales.existsByTenantIdAndLocaleAndEnabledTrue(tenantId, canonical);
    }

    @Transactional(readOnly = true)
    public TenantLocale requireEnabled(UUID tenantId, String locale) {
        String canonical = supportedLocales.canonicalize(locale);
        supportedLocales.requireSupported(canonical);
        return tenantLocales.findByTenantIdAndLocale(tenantId, canonical)
                .filter(TenantLocale::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Locale " + canonical + " is not enabled for tenant " + tenantId));
    }

    @Transactional(readOnly = true)
    public TenantLocale requireDefault(UUID tenantId) {
        return tenantLocales.findByTenantIdAndEnabledTrueAndDefaultLocaleTrue(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Tenant has no enabled default locale: " + tenantId));
    }
}
