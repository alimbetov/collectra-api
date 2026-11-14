-- IAM-R0.1: run as DBA after 001-schema-foundation.sql.
-- Creates NOLOGIN privilege bundles only. LOGIN roles/passwords are provisioned
-- separately through secrets management. Do not grant schema-owner role membership.
\set ON_ERROR_STOP on
BEGIN;
DO $$
DECLARE
  role_name text;
BEGIN
  FOREACH role_name IN ARRAY ARRAY[
    'iam_keycloak_runtime', 'iam_identity_runtime',
    'iam_notification_runtime', 'iam_audit_reader'
  ]
  LOOP
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
      EXECUTE format('CREATE ROLE %I NOLOGIN', role_name);
    END IF;
  END LOOP;
END
$$;
DO $
BEGIN
  EXECUTE format('GRANT CONNECT ON DATABASE %I TO iam_keycloak_runtime, iam_identity_runtime, iam_notification_runtime, iam_audit_reader', current_database());
END
$;
GRANT USAGE, CREATE ON SCHEMA keycloak TO iam_keycloak_runtime;
GRANT USAGE ON SCHEMA identity TO iam_identity_runtime;
GRANT USAGE ON SCHEMA notification TO iam_notification_runtime;
GRANT USAGE ON SCHEMA platform_audit TO iam_audit_reader;
-- Runtime DML for existing and future tables requires explicit object grants.
-- Keycloak DDL requires CREATE and ownership/upgrade permissions; validate against
-- the pinned Keycloak version before production use.
REVOKE ALL ON SCHEMA keycloak FROM iam_identity_runtime, iam_notification_runtime, iam_audit_reader;
REVOKE ALL ON SCHEMA identity FROM iam_keycloak_runtime, iam_notification_runtime, iam_audit_reader;
REVOKE ALL ON SCHEMA notification FROM iam_keycloak_runtime, iam_identity_runtime, iam_audit_reader;
REVOKE ALL ON SCHEMA platform_audit FROM iam_keycloak_runtime, iam_identity_runtime, iam_notification_runtime;
COMMIT;
