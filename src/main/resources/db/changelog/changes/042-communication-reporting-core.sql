--liquibase formatted sql

--changeset collectra:042-communication-reporting-core
ALTER TABLE message_document_links
    ADD COLUMN access_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN first_access_at TIMESTAMPTZ,
    ADD COLUMN last_access_at TIMESTAMPTZ;

ALTER TABLE message_document_links
    ADD CONSTRAINT ck_message_document_links_access_count CHECK (access_count >= 0),
    ADD CONSTRAINT ck_message_document_links_access_timestamps CHECK (
        (access_count = 0 AND first_access_at IS NULL AND last_access_at IS NULL)
        OR (access_count > 0 AND first_access_at IS NOT NULL AND last_access_at IS NOT NULL)
    );

CREATE INDEX idx_campaign_runs_tenant_created
    ON campaign_runs(tenant_id, created_at DESC, id);

CREATE INDEX idx_message_delivery_attempts_tenant_completed
    ON message_delivery_attempts(tenant_id, completed_at DESC, id)
    WHERE completed_at IS NOT NULL;

CREATE INDEX idx_message_document_links_tenant_created
    ON message_document_links(tenant_id, created_at DESC, id);
