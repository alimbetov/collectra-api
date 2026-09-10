--liquibase formatted sql
--changeset collectra:022-outbox-reliability

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS locked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS locked_by VARCHAR(120),
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_error_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS last_error_message VARCHAR(1000);

ALTER TABLE outbox_events
    DROP CONSTRAINT IF EXISTS chk_outbox_attempt_count;
ALTER TABLE outbox_events
    ADD CONSTRAINT chk_outbox_attempt_count CHECK (attempt_count >= 0);

DROP INDEX IF EXISTS idx_outbox_pending;

CREATE INDEX IF NOT EXISTS idx_outbox_ready
    ON outbox_events(status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'RETRY_WAIT');

CREATE INDEX IF NOT EXISTS idx_outbox_processing_locked
    ON outbox_events(locked_at)
    WHERE status = 'PROCESSING';
