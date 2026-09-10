package io.collectra.api.localization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "tenant_locales")
public class TenantLocale {
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 35)
    private String locale;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "is_default", nullable = false)
    private boolean defaultLocale;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected TenantLocale() {}

    public TenantLocale(UUID tenantId, String locale, boolean enabled, boolean defaultLocale, int sortOrder) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.locale = locale;
        this.enabled = enabled;
        this.defaultLocale = defaultLocale;
        this.sortOrder = sortOrder;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getLocale() {
        return locale;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDefaultLocale() {
        return defaultLocale;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        if (defaultLocale) {
            throw new IllegalStateException("Default tenant locale cannot be disabled");
        }
        this.enabled = false;
    }

    public void makeDefault() {
        if (!enabled) {
            throw new IllegalStateException("Disabled tenant locale cannot be default");
        }
        this.defaultLocale = true;
    }

    public void clearDefault() {
        this.defaultLocale = false;
    }
}
