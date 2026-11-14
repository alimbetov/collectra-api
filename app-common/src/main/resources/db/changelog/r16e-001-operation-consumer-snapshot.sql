-- Immutable enforcement obligations captured when a block operation is created.
CREATE TABLE enforcement_operation_consumers (
  operation_id UUID NOT NULL REFERENCES enforcement_operations(id) ON DELETE RESTRICT,
  consumer_id VARCHAR(100) NOT NULL,
  oauth_client_id VARCHAR(160) NOT NULL,
  PRIMARY KEY(operation_id,consumer_id),
  CONSTRAINT ck_enforcement_operation_consumer_client CHECK (length(trim(oauth_client_id))>0)
);
CREATE INDEX idx_enforcement_operation_consumers_consumer ON enforcement_operation_consumers(consumer_id);
-- Existing in-flight operations require a controlled backfill and explicit validation.
-- No automatic backfill: absent snapshots keep legacy operations out of ENFORCED.
