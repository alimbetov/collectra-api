--liquibase formatted sql
--changeset collectra:052-vc9-tenant-financial-projections

CREATE TABLE tenant_daily_financial_metrics (
    business_date DATE NOT NULL,
    tenant_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL,
    invoiced_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    invoice_count BIGINT NOT NULL DEFAULT 0,
    payment_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    payment_count BIGINT NOT NULL DEFAULT 0,
    allocated_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    reversed_allocation_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    collection_opened_count BIGINT NOT NULL DEFAULT 0,
    collection_closed_count BIGINT NOT NULL DEFAULT 0,
    collection_resolved_count BIGINT NOT NULL DEFAULT 0,
    calculated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, business_date, currency)
);

CREATE INDEX idx_tenant_daily_financial_metrics_date
    ON tenant_daily_financial_metrics(business_date, tenant_id);

CREATE TABLE tenant_financial_projection_state (
    tenant_id UUID NOT NULL,
    business_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    source_watermark TIMESTAMPTZ,
    metric_rows INTEGER NOT NULL DEFAULT 0,
    calculated_at TIMESTAMPTZ NOT NULL,
    error_message VARCHAR(1000),
    PRIMARY KEY (tenant_id, business_date),
    CONSTRAINT chk_tenant_financial_projection_status
        CHECK (status IN ('BUILDING','READY','FAILED'))
);

CREATE INDEX idx_tenant_financial_projection_state_status
    ON tenant_financial_projection_state(status, business_date, tenant_id);
