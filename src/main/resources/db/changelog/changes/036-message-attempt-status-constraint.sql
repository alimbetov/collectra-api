--liquibase formatted sql

--changeset collectra:036-message-attempt-status-constraint
ALTER TABLE messages DROP CONSTRAINT ck_messages_attempted_status;
ALTER TABLE messages ADD CONSTRAINT ck_messages_attempted_status CHECK (
    status IN ('QUEUED','PROCESSING')
    OR attempt_count > 0
    OR processing_attempt_count > 0
);
