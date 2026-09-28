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
    PRIMARY KEY (tenant_id, business_date, currency),
    CONSTRAINT fk_tenant_daily_financial_metrics_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT chk_tenant_daily_financial_metrics_non_negative CHECK (
        invoiced_amount >= 0 AND invoice_count >= 0
        AND payment_amount >= 0 AND payment_count >= 0
        AND allocated_amount >= 0 AND reversed_allocation_amount >= 0
        AND collection_opened_count >= 0 AND collection_closed_count >= 0
        AND collection_resolved_count >= 0
    )
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
    CONSTRAINT fk_tenant_financial_projection_state_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT chk_tenant_financial_projection_status
        CHECK (status IN ('BUILDING','READY','FAILED')),
    CONSTRAINT chk_tenant_financial_projection_revision CHECK (revision >= 0),
    CONSTRAINT chk_tenant_financial_projection_rows CHECK (metric_rows >= 0)
);

CREATE INDEX idx_tenant_financial_projection_state_status
    ON tenant_financial_projection_state(status, business_date, tenant_id);


CREATE INDEX idx_invoices_tenant_invoice_date_currency
    ON invoices(tenant_id, invoice_date, currency)
    WHERE invoice_date IS NOT NULL;

CREATE INDEX idx_payments_tenant_payment_date_currency
    ON payments(tenant_id, payment_date, currency);
