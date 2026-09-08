--liquibase formatted sql

--changeset collectra:003-service-client-credentials
ALTER TABLE service_clients
    ADD COLUMN expires_at TIMESTAMPTZ;

CREATE TABLE service_client_credentials (
    id UUID PRIMARY KEY,
    service_client_id UUID NOT NULL REFERENCES service_clients(id) ON DELETE CASCADE,
    secret_hash VARCHAR(100) NOT NULL,
    secret_hint VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_service_credential_status
        CHECK (status IN ('ACTIVE', 'ROTATING', 'REVOKED'))
);

CREATE INDEX idx_service_credentials_client_status
    ON service_client_credentials(service_client_id, status);

INSERT INTO service_client_credentials(
    id,
    service_client_id,
    secret_hash,
    secret_hint,
    status,
    created_at,
    updated_at,
    version
)
SELECT
    gen_random_uuid(),
    id,
    secret_hash,
    'legacy',
    'ACTIVE',
    created_at,
    updated_at,
    0
FROM service_clients;

ALTER TABLE service_clients
    ALTER COLUMN secret_hash DROP NOT NULL;

COMMENT ON COLUMN service_clients.secret_hash IS
    'Deprecated after migration 003; remove after all environments are verified';

COMMENT ON TABLE service_client_credentials IS
    'Allows overlapping credentials so tenant integrations can rotate secrets without downtime';
