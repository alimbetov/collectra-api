--liquibase formatted sql
--changeset collectra:048-import-operations
ALTER TABLE import_batches
    ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'LEGACY_IMPORT',
    ADD COLUMN source_schema_version_id UUID REFERENCES source_schemas(id),
    ADD COLUMN raw_source_file_id UUID REFERENCES stored_file(id),
    ADD COLUMN processing_started_at TIMESTAMPTZ,
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN processing_attempts INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN record_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN created_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN reused_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN conflict_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN failed_count INTEGER NOT NULL DEFAULT 0;
UPDATE import_batches SET processing_started_at=created_at,
 completed_at=COALESCE(failed_at,CASE WHEN status IN ('ACCEPTED','FAILED') THEN updated_at END),
 record_count=document_count,
 created_count=CASE WHEN status='ACCEPTED' THEN document_count ELSE 0 END,
 failed_count=CASE WHEN status='FAILED' THEN GREATEST(document_count,1) ELSE 0 END;
ALTER TABLE import_batches ADD CONSTRAINT ck_import_batch_operation_counters CHECK
 (processing_attempts>=0 AND record_count>=0 AND created_count>=0 AND reused_count>=0 AND conflict_count>=0 AND failed_count>=0);
CREATE INDEX idx_import_batches_tenant_status_created_id ON import_batches(tenant_id,status,created_at DESC,id);
