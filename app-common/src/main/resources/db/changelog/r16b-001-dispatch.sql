ALTER TABLE access_state_outbox ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE access_state_outbox ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE access_state_outbox ADD COLUMN last_error VARCHAR(500);
ALTER TABLE access_state_outbox ADD COLUMN keycloak_applied_at TIMESTAMPTZ;
CREATE TABLE revocation_consumers (
 consumer_id VARCHAR(100) PRIMARY KEY,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE revocation_acks (
 consumer_id VARCHAR(100) NOT NULL REFERENCES revocation_consumers(consumer_id),
 issuer VARCHAR(512) NOT NULL,
 subject_id VARCHAR(255) NOT NULL,
 version BIGINT NOT NULL,
 applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(consumer_id,issuer,subject_id)
);
CREATE INDEX idx_outbox_due ON access_state_outbox(next_attempt_at,created_at) WHERE published_at IS NULL;
