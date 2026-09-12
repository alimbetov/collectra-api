--liquibase formatted sql

--changeset collectra:029-delivery-api-observability
CREATE INDEX idx_messages_run_created
    ON messages(tenant_id, campaign_run_id, created_at DESC, id DESC);
