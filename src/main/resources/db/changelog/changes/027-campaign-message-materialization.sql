--liquibase formatted sql

--changeset collectra:027-campaign-message-materialization
CREATE TABLE campaign_run_template_bindings (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    campaign_run_id UUID NOT NULL REFERENCES campaign_runs(id) ON DELETE CASCADE,
    requested_locale VARCHAR(35) NOT NULL,
    resolved_locale VARCHAR(35) NOT NULL,
    template_version_id UUID NOT NULL REFERENCES template_versions(id),
    resolution_source VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uk_campaign_run_template_binding_locale
        UNIQUE (campaign_run_id, requested_locale),
    CONSTRAINT ck_campaign_run_template_binding_requested_locale
        CHECK (BTRIM(requested_locale) <> ''),
    CONSTRAINT ck_campaign_run_template_binding_resolved_locale
        CHECK (BTRIM(resolved_locale) <> ''),
    CONSTRAINT ck_campaign_run_template_binding_resolution_source
        CHECK (resolution_source IN ('EXACT','LOCALE_FALLBACK','TENANT_DEFAULT','PLATFORM_DEFAULT'))
);

CREATE INDEX idx_campaign_run_template_bindings_tenant_run
    ON campaign_run_template_bindings(tenant_id, campaign_run_id);
