-- liquibase formatted sql
-- changeset collectra:046-production-ingestion
CREATE TABLE ingestion_batches (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, integration_source_id uuid NOT NULL, service_client_id uuid NOT NULL,
 idempotency_key varchar(200) NOT NULL, request_hash varchar(64) NOT NULL, raw_source_file_id uuid NOT NULL,
 source_schema_version_id uuid, mapping_profile_version_id uuid, mapping_config_sha256 varchar(64),
 status varchar(30) NOT NULL, content_type varchar(150) NOT NULL, record_count integer NOT NULL DEFAULT 0,
 accepted_count integer NOT NULL DEFAULT 0, reused_count integer NOT NULL DEFAULT 0, failed_count integer NOT NULL DEFAULT 0,
 received_at timestamptz NOT NULL, processing_started_at timestamptz, completed_at timestamptz,
 error_code varchar(100), safe_error_message varchar(500), context_json jsonb NOT NULL DEFAULT '{}',
 version bigint NOT NULL DEFAULT 0,
 CONSTRAINT uq_ingestion_idempotency UNIQUE(tenant_id,integration_source_id,idempotency_key),
 CONSTRAINT fk_ingestion_source FOREIGN KEY(integration_source_id) REFERENCES integration_sources(id),
 CONSTRAINT fk_ingestion_raw_file FOREIGN KEY(raw_source_file_id) REFERENCES stored_files(id)
);
CREATE INDEX idx_ingestion_batches_source_received ON ingestion_batches(tenant_id,integration_source_id,received_at DESC);
CREATE TABLE ingestion_record_diagnostics (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, ingestion_batch_id uuid NOT NULL, record_order integer NOT NULL,
 document_key varchar(200), status varchar(30) NOT NULL, stage varchar(30) NOT NULL, target_type varchar(30),
 target_id uuid, external_id varchar(200), error_code varchar(100), safe_error_message varchar(500), field_path varchar(300),
 created_at timestamptz NOT NULL,
 CONSTRAINT fk_ingestion_diag_batch FOREIGN KEY(ingestion_batch_id) REFERENCES ingestion_batches(id) ON DELETE CASCADE
);
CREATE INDEX idx_ingestion_diag_batch_order ON ingestion_record_diagnostics(tenant_id,ingestion_batch_id,record_order);
