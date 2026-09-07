--liquibase formatted sql
--changeset collectra:001-foundation
CREATE TABLE tenants (id UUID PRIMARY KEY, slug VARCHAR(80) NOT NULL UNIQUE, name VARCHAR(200) NOT NULL, status VARCHAR(40) NOT NULL, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0);
CREATE TABLE user_accounts (id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES tenants(id), email VARCHAR(254) NOT NULL, password_hash VARCHAR(100) NOT NULL, role VARCHAR(40) NOT NULL, status VARCHAR(20) NOT NULL, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0, CONSTRAINT uk_user_tenant_email UNIQUE(tenant_id,email));
CREATE TABLE refresh_sessions (id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES user_accounts(id), tenant_id UUID NOT NULL REFERENCES tenants(id), token_hash VARCHAR(64) NOT NULL, family_id UUID NOT NULL, expires_at TIMESTAMPTZ NOT NULL, revoked_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0);
CREATE UNIQUE INDEX idx_refresh_token_hash ON refresh_sessions(token_hash);
CREATE INDEX idx_refresh_user ON refresh_sessions(user_id);
CREATE TABLE outbox_events (id UUID PRIMARY KEY, tenant_id UUID REFERENCES tenants(id), aggregate_type VARCHAR(100) NOT NULL, aggregate_id UUID NOT NULL, event_type VARCHAR(150) NOT NULL, payload JSONB NOT NULL, status VARCHAR(20) NOT NULL, attempt_count INT NOT NULL DEFAULT 0, next_attempt_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL);
CREATE INDEX idx_outbox_pending ON outbox_events(status,next_attempt_at);
