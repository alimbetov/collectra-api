--liquibase formatted sql

--changeset collectra:035-message-processing-attempt-count
ALTER TABLE messages
    ADD COLUMN processing_attempt_count INT NOT NULL DEFAULT 0;

UPDATE messages
SET processing_attempt_count = attempt_count
WHERE attempt_count > 0;

ALTER TABLE messages
    ADD CONSTRAINT ck_messages_processing_attempt_count CHECK (processing_attempt_count >= 0);
