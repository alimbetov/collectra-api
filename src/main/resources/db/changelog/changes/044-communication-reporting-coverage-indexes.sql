--liquibase formatted sql

--changeset collectra:044-communication-reporting-coverage-indexes
CREATE INDEX idx_campaigns_tenant_created_status_channel
    ON campaigns(tenant_id, created_at DESC, status, channel);

CREATE INDEX idx_campaign_runs_tenant_created_status
    ON campaign_runs(tenant_id, created_at DESC, status, campaign_id);

CREATE INDEX idx_campaign_recipients_tenant_created_status
    ON campaign_recipients(tenant_id, created_at DESC, status, campaign_id, run_id);

CREATE INDEX idx_message_delivery_attempts_tenant_started_status
    ON message_delivery_attempts(tenant_id, started_at DESC, status, message_id);

CREATE INDEX idx_message_attachments_tenant_created_status
    ON message_attachments(tenant_id, created_at DESC, status, message_id);
