--liquibase formatted sql

--changeset collectra:026-communication-delivery-core
CREATE TABLE messages (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    campaign_id UUID NOT NULL REFERENCES campaigns(id),
    campaign_run_id UUID NOT NULL REFERENCES campaign_runs(id),
    campaign_recipient_id UUID NOT NULL REFERENCES campaign_recipients(id),
    customer_id UUID NOT NULL REFERENCES customers(id),
    invoice_id UUID REFERENCES invoices(id),
    template_version_id UUID NOT NULL REFERENCES template_versions(id),

    channel VARCHAR(30) NOT NULL,
    destination VARCHAR(500) NOT NULL,
    resolved_locale VARCHAR(35) NOT NULL,
    subject VARCHAR(500),
    body TEXT NOT NULL,

    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    processing_started_at TIMESTAMPTZ,
    next_retry_at TIMESTAMPTZ,
    provider_message_id VARCHAR(255),
    last_error_code VARCHAR(80),
    last_error_message VARCHAR(1000),
    sent_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uk_messages_campaign_recipient UNIQUE (campaign_recipient_id),
    CONSTRAINT ck_messages_channel CHECK (
        channel IN ('EMAIL','SMS','WHATSAPP','TELEGRAM','IN_APP')),
    CONSTRAINT ck_messages_status CHECK (
        status IN ('QUEUED','PROCESSING','RETRY_WAIT','SENT','FAILED')),
    CONSTRAINT ck_messages_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_messages_destination_required CHECK (BTRIM(destination) <> ''),
    CONSTRAINT ck_messages_locale_required CHECK (BTRIM(resolved_locale) <> ''),
    CONSTRAINT ck_messages_body_required CHECK (BTRIM(body) <> ''),
    CONSTRAINT ck_messages_email_subject CHECK (
        channel <> 'EMAIL' OR (subject IS NOT NULL AND BTRIM(subject) <> '')),
    CONSTRAINT ck_messages_status_timestamps CHECK (
        (status = 'QUEUED'
            AND processing_started_at IS NULL AND next_retry_at IS NULL AND sent_at IS NULL)
        OR (status = 'PROCESSING'
            AND processing_started_at IS NOT NULL AND next_retry_at IS NULL AND sent_at IS NULL)
        OR (status = 'RETRY_WAIT'
            AND processing_started_at IS NULL AND next_retry_at IS NOT NULL AND sent_at IS NULL)
        OR (status = 'SENT'
            AND processing_started_at IS NULL AND next_retry_at IS NULL AND sent_at IS NOT NULL)
        OR (status = 'FAILED'
            AND processing_started_at IS NULL AND next_retry_at IS NULL AND sent_at IS NULL)
    ),
    CONSTRAINT ck_messages_attempted_status CHECK (
        status = 'QUEUED' OR attempt_count > 0)
);

CREATE INDEX idx_messages_run_status
    ON messages(tenant_id, campaign_run_id, status, created_at);
CREATE INDEX idx_messages_retry_due
    ON messages(next_retry_at, id) WHERE status = 'RETRY_WAIT';
CREATE INDEX idx_messages_processing_stale
    ON messages(processing_started_at, id) WHERE status = 'PROCESSING';
CREATE INDEX idx_messages_tenant_created
    ON messages(tenant_id, created_at DESC, id);

ALTER TABLE campaign_runs
    ADD COLUMN recipient_count INT NOT NULL DEFAULT 0,
    ADD COLUMN sent_count INT NOT NULL DEFAULT 0,
    ADD COLUMN failed_count INT NOT NULL DEFAULT 0,
    ADD COLUMN skipped_count INT NOT NULL DEFAULT 0,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0;

ALTER TABLE campaign_runs
    ADD CONSTRAINT ck_campaign_runs_delivery_counters CHECK (
        recipient_count >= 0
        AND sent_count >= 0
        AND failed_count >= 0
        AND skipped_count >= 0
        AND retry_count >= 0
        AND sent_count + failed_count + skipped_count <= recipient_count
    );
