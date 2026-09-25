-- liquibase formatted sql
-- changeset collectra:046-production-ingestion
CREATE TABLE ingestion_batches (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    integration_source_id uuid NOT NULL,
    service_client_id uuid NOT NULL,
    source_code varchar(100) NOT NULL,
    idempotency_key varchar(200) NOT NULL,
    request_hash varchar(64) NOT NULL,
    request_id varchar(200),
    raw_source_file_id uuid NOT NULL,
    source_schema_version_id uuid NOT NULL,
    mapping_profile_version_id uuid NOT NULL,
    mapping_config_sha256 varchar(64),
    status varchar(30) NOT NULL,
    content_type varchar(150) NOT NULL,
    record_count integer NOT NULL DEFAULT 0,
    created_count integer NOT NULL DEFAULT 0,
    reused_count integer NOT NULL DEFAULT 0,
    conflict_count integer NOT NULL DEFAULT 0,
    failed_count integer NOT NULL DEFAULT 0,
    processing_attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz,
    received_at timestamptz NOT NULL,
    processing_started_at timestamptz,
    completed_at timestamptz,
    error_code varchar(100),
    safe_error_message varchar(500),
    context_json jsonb NOT NULL DEFAULT '{}',
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_ingestion_transport_idempotency
        UNIQUE (tenant_id, integration_source_id, service_client_id, idempotency_key),
    CONSTRAINT fk_ingestion_source FOREIGN KEY (integration_source_id) REFERENCES integration_sources(id),
    CONSTRAINT fk_ingestion_raw_file FOREIGN KEY (raw_source_file_id) REFERENCES stored_files(id),
    CONSTRAINT ck_ingestion_status CHECK (status IN ('QUEUED','PROCESSING','RETRY_WAIT','COMPLETED','PARTIALLY_COMPLETED','FAILED'))
);
CREATE INDEX idx_ingestion_batches_tenant_received
    ON ingestion_batches (tenant_id, received_at DESC);
CREATE INDEX idx_ingestion_batches_source_received
    ON ingestion_batches (tenant_id, source_code, received_at DESC);
CREATE INDEX idx_ingestion_batches_recovery
    ON ingestion_batches (status, next_attempt_at, processing_started_at);

CREATE TABLE ingestion_record_diagnostics (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    ingestion_batch_id uuid NOT NULL,
    record_order integer NOT NULL,
    document_key varchar(200),
    outcome varchar(30) NOT NULL,
    stage varchar(30) NOT NULL,
    target_type varchar(30),
    target_id uuid,
    external_id varchar(200),
    error_code varchar(100),
    safe_error_message varchar(500),
    field_path varchar(300),
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_ingestion_diag_record UNIQUE (tenant_id, ingestion_batch_id, record_order),
    CONSTRAINT fk_ingestion_diag_batch FOREIGN KEY (ingestion_batch_id)
        REFERENCES ingestion_batches(id) ON DELETE CASCADE,
    CONSTRAINT ck_ingestion_diag_outcome CHECK (outcome IN ('CREATED','REUSED','CONFLICT','FAILED'))
);
CREATE INDEX idx_ingestion_diag_batch_order
    ON ingestion_record_diagnostics (tenant_id, ingestion_batch_id, record_order);
CREATE INDEX idx_ingestion_diag_filter
    ON ingestion_record_diagnostics (tenant_id, ingestion_batch_id, outcome, target_type);

-- rollback DROP TABLE IF EXISTS ingestion_record_diagnostics; DROP TABLE IF EXISTS ingestion_batches;
