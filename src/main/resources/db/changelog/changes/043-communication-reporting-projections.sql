--liquibase formatted sql

--changeset collectra:043-communication-reporting-projections
CREATE TABLE communication_daily_campaign_metrics (
    business_date DATE NOT NULL,
    tenant_id UUID NOT NULL,
    campaign_id UUID NOT NULL,
    created_by_user_id UUID,
    channel VARCHAR(32) NOT NULL,
    run_count BIGINT NOT NULL DEFAULT 0,
    recipient_count BIGINT NOT NULL DEFAULT 0,
    sent_count BIGINT NOT NULL DEFAULT 0,
    failed_count BIGINT NOT NULL DEFAULT 0,
    skipped_count BIGINT NOT NULL DEFAULT 0,
    retry_count BIGINT NOT NULL DEFAULT 0,
    message_count BIGINT NOT NULL DEFAULT 0,
    queued_count BIGINT NOT NULL DEFAULT 0,
    processing_count BIGINT NOT NULL DEFAULT 0,
    retry_wait_count BIGINT NOT NULL DEFAULT 0,
    message_sent_count BIGINT NOT NULL DEFAULT 0,
    message_failed_count BIGINT NOT NULL DEFAULT 0,
    unknown_count BIGINT NOT NULL DEFAULT 0,
    document_created_count BIGINT NOT NULL DEFAULT 0,
    document_pending_count BIGINT NOT NULL DEFAULT 0,
    document_ready_count BIGINT NOT NULL DEFAULT 0,
    document_failed_count BIGINT NOT NULL DEFAULT 0,
    document_expired_count BIGINT NOT NULL DEFAULT 0,
    document_access_count BIGINT NOT NULL DEFAULT 0,
    document_accessed_link_count BIGINT NOT NULL DEFAULT 0,
    last_run_at TIMESTAMPTZ,
    calculated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (business_date, tenant_id, campaign_id),
    CONSTRAINT fk_comm_daily_campaign_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_comm_daily_campaign_campaign
        FOREIGN KEY (campaign_id) REFERENCES campaigns(id)
);

CREATE INDEX idx_comm_daily_campaign_tenant_date
    ON communication_daily_campaign_metrics(tenant_id, business_date DESC);

CREATE INDEX idx_comm_daily_campaign_channel_date
    ON communication_daily_campaign_metrics(tenant_id, channel, business_date DESC);

CREATE INDEX idx_comm_daily_campaign_user_date
    ON communication_daily_campaign_metrics(tenant_id, created_by_user_id, business_date DESC);

CREATE INDEX idx_comm_daily_campaign_campaign_date
    ON communication_daily_campaign_metrics(tenant_id, campaign_id, business_date DESC);

CREATE TABLE communication_daily_failure_metrics (
    business_date DATE NOT NULL,
    tenant_id UUID NOT NULL,
    campaign_id UUID NOT NULL,
    error_code VARCHAR(160) NOT NULL,
    failure_count BIGINT NOT NULL DEFAULT 0,
    retryable_count BIGINT NOT NULL DEFAULT 0,
    permanent_count BIGINT NOT NULL DEFAULT 0,
    unknown_count BIGINT NOT NULL DEFAULT 0,
    calculated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (business_date, tenant_id, campaign_id, error_code),
    CONSTRAINT fk_comm_daily_failure_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_comm_daily_failure_campaign
        FOREIGN KEY (campaign_id) REFERENCES campaigns(id)
);

CREATE INDEX idx_comm_daily_failure_tenant_date
    ON communication_daily_failure_metrics(tenant_id, business_date DESC);

CREATE INDEX idx_comm_daily_failure_code_date
    ON communication_daily_failure_metrics(tenant_id, error_code, business_date DESC);

CREATE TABLE communication_reporting_projection_state (
    tenant_id UUID NOT NULL,
    business_date DATE NOT NULL,
    status VARCHAR(24) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    source_watermark TIMESTAMPTZ,
    campaign_rows BIGINT NOT NULL DEFAULT 0,
    failure_rows BIGINT NOT NULL DEFAULT 0,
    calculated_at TIMESTAMPTZ NOT NULL,
    error_message VARCHAR(1000),
    PRIMARY KEY (tenant_id, business_date),
    CONSTRAINT fk_comm_projection_state_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_comm_projection_status
        CHECK (status IN ('BUILDING', 'READY', 'FAILED')),
    CONSTRAINT ck_comm_projection_revision CHECK (revision >= 0),
    CONSTRAINT ck_comm_projection_rows
        CHECK (campaign_rows >= 0 AND failure_rows >= 0)
);


CREATE INDEX idx_comm_projection_state_status_date
    ON communication_reporting_projection_state(status, business_date DESC, tenant_id);
