--liquibase formatted sql

--changeset collectra:021-tenant-locales
CREATE TABLE tenant_locales (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    locale VARCHAR(35) NOT NULL REFERENCES supported_locales(code),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_tenant_locale UNIQUE (tenant_id, locale)
);

CREATE UNIQUE INDEX uk_tenant_locales_single_default
    ON tenant_locales(tenant_id)
    WHERE is_default = TRUE;

CREATE INDEX idx_tenant_locales_enabled_order
    ON tenant_locales(tenant_id, enabled, sort_order, locale);
