--liquibase formatted sql

--changeset collectra:025-campaign-core
CREATE TABLE campaigns (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    template_version_id UUID NOT NULL REFERENCES template_versions(id),
    channel VARCHAR(30) NOT NULL,
    scheduled_at TIMESTAMPTZ,
    selection_criteria JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_campaign_status CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED'))
);
CREATE INDEX idx_campaigns_tenant_status ON campaigns(tenant_id, status, created_at);

CREATE TABLE campaign_runs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    campaign_id UUID NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    prepared_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_campaign_run_status CHECK (
        status IN ('PREPARING','READY','RUNNING','COMPLETED','CANCELLED','FAILED'))
);
CREATE INDEX idx_campaign_runs_campaign ON campaign_runs(tenant_id, campaign_id, created_at);

CREATE TABLE campaign_recipients (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    campaign_id UUID NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    run_id UUID NOT NULL REFERENCES campaign_runs(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES customers(id),
    invoice_id UUID REFERENCES invoices(id),
    channel VARCHAR(30) NOT NULL,
    destination VARCHAR(500),
    locale VARCHAR(16),
    status VARCHAR(20) NOT NULL,
    skip_reason VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_campaign_recipient_run_customer_invoice UNIQUE (run_id, customer_id, invoice_id),
    CONSTRAINT ck_campaign_recipient_status CHECK (status IN ('SNAPSHOT','ELIGIBLE','SKIPPED'))
);
CREATE INDEX idx_campaign_recipients_run_status
    ON campaign_recipients(tenant_id, run_id, status, created_at);

INSERT INTO permissions(id, code, module, description)
VALUES
    ('10000000-0000-0000-0000-000000000030', 'CAMPAIGN_READ', 'campaign', 'Read campaigns and runs'),
    ('10000000-0000-0000-0000-000000000031', 'CAMPAIGN_MANAGE', 'campaign', 'Create and run campaigns');

INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000002', id
FROM permissions WHERE code IN ('CAMPAIGN_READ', 'CAMPAIGN_MANAGE');
