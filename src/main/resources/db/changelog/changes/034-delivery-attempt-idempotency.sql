--liquibase formatted sql

--changeset collectra:034-delivery-attempt-idempotency
ALTER TABLE messages
    ADD COLUMN delivery_key VARCHAR(100);

UPDATE messages
SET delivery_key = 'msg-' || id::text
WHERE delivery_key IS NULL;

ALTER TABLE messages
    ALTER COLUMN delivery_key SET NOT NULL,
    ADD CONSTRAINT uk_messages_delivery_key UNIQUE (delivery_key);

ALTER TABLE messages DROP CONSTRAINT ck_messages_status;
ALTER TABLE messages ADD CONSTRAINT ck_messages_status CHECK (
    status IN ('QUEUED','PROCESSING','RETRY_WAIT','SENT','FAILED','UNKNOWN'));

ALTER TABLE messages DROP CONSTRAINT ck_messages_status_timestamps;
ALTER TABLE messages ADD CONSTRAINT ck_messages_status_timestamps CHECK (
    (status = 'QUEUED'
        AND processing_started_at IS NULL AND next_retry_at IS NULL AND sent_at IS NULL)
    OR (status = 'PROCESSING'
        AND processing_started_at IS NOT NULL AND next_retry_at IS NULL AND sent_at IS NULL)
    OR (status = 'RETRY_WAIT'
        AND processing_started_at IS NULL AND next_retry_at IS NOT NULL AND sent_at IS NULL)
    OR (status = 'SENT'
        AND processing_started_at IS NULL AND next_retry_at IS NULL AND sent_at IS NOT NULL)
    OR (status IN ('FAILED','UNKNOWN')
        AND processing_started_at IS NULL AND next_retry_at IS NULL AND sent_at IS NULL)
);

ALTER TABLE messages DROP CONSTRAINT ck_messages_attempted_status;
ALTER TABLE messages ADD CONSTRAINT ck_messages_attempted_status CHECK (
    status IN ('QUEUED','PROCESSING') OR attempt_count > 0);

CREATE TABLE message_delivery_attempts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    attempt_no INT NOT NULL,
    delivery_key VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider_reference VARCHAR(255),
    error_code VARCHAR(80),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uk_message_delivery_attempt_number UNIQUE (message_id, attempt_no),
    CONSTRAINT ck_message_delivery_attempt_no CHECK (attempt_no > 0),
    CONSTRAINT ck_message_delivery_attempt_status CHECK (
        status IN ('STARTED','ACCEPTED','RETRYABLE_FAILURE','PERMANENT_FAILURE','UNKNOWN')),
    CONSTRAINT ck_message_delivery_attempt_completion CHECK (
        (status = 'STARTED' AND completed_at IS NULL)
        OR (status <> 'STARTED' AND completed_at IS NOT NULL))
);

CREATE INDEX idx_message_delivery_attempts_message
    ON message_delivery_attempts(message_id, attempt_no DESC);
CREATE INDEX idx_message_delivery_attempts_started
    ON message_delivery_attempts(started_at, id) WHERE status = 'STARTED';
