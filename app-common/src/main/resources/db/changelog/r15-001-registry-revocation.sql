CREATE TABLE applications (
  id UUID PRIMARY KEY,
  code VARCHAR(80) NOT NULL UNIQUE,
  state VARCHAR(24) NOT NULL CHECK (state IN ('REGISTERED','CONFIGURING','ACTIVE','SUSPENDED','DECOMMISSIONED')),
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE application_clients (
  id UUID PRIMARY KEY,
  application_id UUID NOT NULL REFERENCES applications(id),
  client_id VARCHAR(160) NOT NULL UNIQUE,
  audience VARCHAR(160) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  UNIQUE(application_id, client_id)
);
CREATE TABLE subject_access_state (
  issuer VARCHAR(512) NOT NULL,
  subject_id VARCHAR(255) NOT NULL,
  state VARCHAR(16) NOT NULL CHECK (state IN ('ACTIVE','BLOCKED')),
  version BIGINT NOT NULL CHECK (version > 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (issuer,subject_id)
);
CREATE TABLE enforcement_operations (
  id UUID PRIMARY KEY,
  issuer VARCHAR(512) NOT NULL,
  subject_id VARCHAR(255) NOT NULL,
  target_version BIGINT NOT NULL,
  state VARCHAR(24) NOT NULL CHECK (state IN ('STATE_COMMITTED','PROPAGATING','ENFORCED','PARTIAL','FAILED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(issuer,subject_id,target_version)
);
CREATE TABLE access_state_outbox (
  id UUID PRIMARY KEY,
  issuer VARCHAR(512) NOT NULL,
  subject_id VARCHAR(255) NOT NULL,
  target_version BIGINT NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ,
  UNIQUE(issuer,subject_id,target_version)
);
CREATE INDEX idx_access_state_outbox_pending ON access_state_outbox(created_at) WHERE published_at IS NULL;
