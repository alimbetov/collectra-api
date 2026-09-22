--liquibase formatted sql

--changeset collectra:039-customer-channel-addresses
CREATE TABLE customer_channel_addresses (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL REFERENCES customers(id),
    channel VARCHAR(30) NOT NULL,
    address VARCHAR(500) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_customer_channel_address
        UNIQUE (tenant_id, customer_id, channel, address),
    CONSTRAINT ck_customer_channel_address_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_customer_channel_addresses_lookup
    ON customer_channel_addresses(tenant_id, customer_id, channel, status);
