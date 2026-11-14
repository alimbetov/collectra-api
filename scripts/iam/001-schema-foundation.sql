-- IAM-R0: additive schema foundation only; run as an authorized PostgreSQL administrator.
-- Does not touch existing Collectra tables or change application search_path.
\set ON_ERROR_STOP on
BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'iam_keycloak_owner') THEN
    CREATE ROLE iam_keycloak_owner NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'iam_identity_owner') THEN
    CREATE ROLE iam_identity_owner NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'iam_notification_owner') THEN
    CREATE ROLE iam_notification_owner NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'iam_audit_owner') THEN
    CREATE ROLE iam_audit_owner NOLOGIN;
  END IF;
END
$$;

CREATE SCHEMA IF NOT EXISTS keycloak AUTHORIZATION iam_keycloak_owner;
CREATE SCHEMA IF NOT EXISTS identity AUTHORIZATION iam_identity_owner;
CREATE SCHEMA IF NOT EXISTS notification AUTHORIZATION iam_notification_owner;
CREATE SCHEMA IF NOT EXISTS platform_audit AUTHORIZATION iam_audit_owner;

REVOKE ALL ON SCHEMA keycloak, identity, notification, platform_audit FROM PUBLIC;
COMMIT;
