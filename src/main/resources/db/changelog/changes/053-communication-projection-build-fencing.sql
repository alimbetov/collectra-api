--liquibase formatted sql

--changeset collectra:053-communication-projection-build-fencing
ALTER TABLE communication_reporting_projection_state
    ADD COLUMN build_id UUID;

-- Existing BUILDING rows predate ownership fencing and must be recoverable rather than
-- accidentally finalized by an unfenced worker after deployment.
UPDATE communication_reporting_projection_state
   SET status = 'FAILED',
       error_message = 'Legacy BUILDING projection invalidated during build fencing upgrade'
 WHERE status = 'BUILDING';

CREATE INDEX idx_comm_projection_state_build_id
    ON communication_reporting_projection_state(build_id)
    WHERE build_id IS NOT NULL;
