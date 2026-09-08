--liquibase formatted sql

--changeset collectra:004-security-core
DROP TABLE IF EXISTS service_client_ip_rules;

DELETE FROM roles
WHERE id = '00000000-0000-0000-0000-000000000004'
  AND code = 'TENANT_TECHNICAL_CLIENT';
