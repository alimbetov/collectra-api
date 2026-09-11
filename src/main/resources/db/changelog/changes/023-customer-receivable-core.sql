--liquibase formatted sql
--changeset collectra:023-customer-receivable-core

CREATE TABLE customers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    external_id VARCHAR(120) NOT NULL,
    customer_type VARCHAR(20) NOT NULL,
    display_name VARCHAR(300) NOT NULL,
    first_name VARCHAR(120), last_name VARCHAR(120), middle_name VARCHAR(120), company_name VARCHAR(300),
    status VARCHAR(20) NOT NULL,
    manager_user_id UUID, preferred_locale VARCHAR(35), timezone VARCHAR(60), custom_fields JSONB,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_customer_tenant_external UNIQUE (tenant_id, external_id)
);
CREATE INDEX idx_customers_tenant_status ON customers(tenant_id,status);

CREATE TABLE customer_emails (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, customer_id UUID NOT NULL REFERENCES customers(id), email VARCHAR(320) NOT NULL,
    type VARCHAR(20) NOT NULL, is_primary BOOLEAN NOT NULL DEFAULT FALSE, verified BOOLEAN NOT NULL DEFAULT FALSE, status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_customer_email UNIQUE(tenant_id,customer_id,email)
);
CREATE TABLE customer_phones (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, customer_id UUID NOT NULL REFERENCES customers(id), phone VARCHAR(40) NOT NULL, normalized_phone VARCHAR(24) NOT NULL,
    type VARCHAR(20) NOT NULL, is_primary BOOLEAN NOT NULL DEFAULT FALSE, verified BOOLEAN NOT NULL DEFAULT FALSE, status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_customer_phone UNIQUE(tenant_id,customer_id,normalized_phone)
);
CREATE TABLE customer_segments (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, code VARCHAR(80) NOT NULL, name VARCHAR(200) NOT NULL, description VARCHAR(1000), active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_customer_segment_code UNIQUE(tenant_id,code)
);
CREATE TABLE customer_segment_members (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, customer_id UUID NOT NULL REFERENCES customers(id), segment_id UUID NOT NULL REFERENCES customer_segments(id),
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_customer_segment_member UNIQUE(tenant_id,customer_id,segment_id)
);

CREATE TABLE invoices (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, customer_id UUID NOT NULL REFERENCES customers(id), contract_id UUID,
    external_id VARCHAR(120) NOT NULL, invoice_number VARCHAR(120) NOT NULL, invoice_date DATE, due_date DATE NOT NULL,
    original_amount NUMERIC(19,4) NOT NULL, paid_amount NUMERIC(19,4) NOT NULL DEFAULT 0, outstanding_amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL, payment_status VARCHAR(30) NOT NULL, document_file_id UUID, custom_fields JSONB,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_invoice_tenant_external UNIQUE(tenant_id,external_id),
    CONSTRAINT chk_invoice_amounts CHECK(original_amount > 0 AND paid_amount >= 0 AND outstanding_amount >= 0)
);
CREATE INDEX idx_invoices_tenant_customer ON invoices(tenant_id,customer_id);
CREATE INDEX idx_invoices_tenant_due ON invoices(tenant_id,due_date,payment_status);

CREATE TABLE payments (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, customer_id UUID NOT NULL REFERENCES customers(id), external_id VARCHAR(120) NOT NULL,
    payment_date DATE NOT NULL, amount NUMERIC(19,4) NOT NULL, currency VARCHAR(3) NOT NULL, payment_reference VARCHAR(200), source VARCHAR(80), custom_fields JSONB,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_payment_tenant_external UNIQUE(tenant_id,external_id), CONSTRAINT chk_payment_amount CHECK(amount > 0)
);
CREATE TABLE payment_allocations (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, payment_id UUID NOT NULL REFERENCES payments(id), invoice_id UUID NOT NULL REFERENCES invoices(id), amount NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_payment_allocation_amount CHECK(amount > 0)
);
CREATE INDEX idx_payment_alloc_payment ON payment_allocations(tenant_id,payment_id);
CREATE INDEX idx_payment_alloc_invoice ON payment_allocations(tenant_id,invoice_id);
