CREATE TABLE subject_block_idempotency (
  issuer VARCHAR(512) NOT NULL,
  actor_subject VARCHAR(255) NOT NULL,
  idempotency_key VARCHAR(160) NOT NULL,
  request_subject VARCHAR(255) NOT NULL,
  operation_id UUID NOT NULL REFERENCES enforcement_operations(id),
  target_version BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (issuer,actor_subject,idempotency_key)
);
CREATE INDEX idx_enforcement_operations_subject ON enforcement_operations(issuer,subject_id,created_at DESC);
