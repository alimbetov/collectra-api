--liquibase formatted sql

--changeset collectra:041-campaign-contract-closure
ALTER TABLE campaign_runs
    ADD COLUMN prepare_command_id UUID;

CREATE UNIQUE INDEX uk_campaign_runs_prepare_command
    ON campaign_runs(tenant_id, campaign_id, prepare_command_id)
    WHERE prepare_command_id IS NOT NULL;

ALTER TABLE campaigns
    ADD COLUMN scheduled_dispatched_at TIMESTAMPTZ;

CREATE INDEX idx_campaigns_due_dispatch
    ON campaigns(status, scheduled_at, id)
    WHERE status = 'ACTIVE'
      AND scheduled_at IS NOT NULL
      AND scheduled_dispatched_at IS NULL;
