CREATE TABLE collectra_global_access_snapshots (
 issuer VARCHAR(512) NOT NULL,
 subject_id VARCHAR(255) NOT NULL,
 version BIGINT NOT NULL CHECK(version >= 0),
 state VARCHAR(16) NOT NULL CHECK(state IN ('ACTIVE','BLOCKED')),
 refreshed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(issuer,subject_id)
);
-- Snapshot ingestion must be authenticated and transactional; never allow untrusted users to update this table.
