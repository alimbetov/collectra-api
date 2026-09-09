--liquibase formatted sql

--changeset collectra:015-import-batch-failure-details
ALTER TABLE import_batches
    ADD COLUMN error_code VARCHAR(60),
    ADD COLUMN error_message VARCHAR(1000),
    ADD COLUMN failed_at TIMESTAMPTZ;

ALTER TABLE import_batches ADD CONSTRAINT ck_import_batch_failure_details
    CHECK ((status = 'FAILED' AND error_code IS NOT NULL AND error_message IS NOT NULL AND failed_at IS NOT NULL)
        OR (status <> 'FAILED' AND error_code IS NULL AND error_message IS NULL AND failed_at IS NULL));
