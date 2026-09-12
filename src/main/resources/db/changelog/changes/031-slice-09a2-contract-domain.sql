--liquibase formatted sql

--changeset collectra:031-slice-09a2-contract-domain
CREATE TABLE contracts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    external_id VARCHAR(120) NOT NULL,
    contract_number VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    renewal_date DATE,
    custom_fields JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_contract_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT uk_contract_tenant_external UNIQUE (tenant_id, external_id),
    CONSTRAINT chk_contract_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT chk_contract_valid_range CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE INDEX idx_contract_tenant_created
    ON contracts (tenant_id, created_at DESC, id DESC);

CREATE INDEX idx_contract_tenant_status_created
    ON contracts (tenant_id, status, created_at DESC, id DESC);

CREATE INDEX idx_contract_tenant_customer_created
    ON contracts (tenant_id, customer_id, created_at DESC, id DESC);

CREATE INDEX idx_contract_tenant_number
    ON contracts (tenant_id, lower(contract_number));

CREATE INDEX idx_contract_tenant_external_search
    ON contracts (tenant_id, lower(external_id));
