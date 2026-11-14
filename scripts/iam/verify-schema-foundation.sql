-- IAM-R0: executable schema-foundation assertions.
\set ON_ERROR_STOP on
DO $$
DECLARE
  s record;
BEGIN
  FOR s IN SELECT * FROM (VALUES
    ('keycloak', 'iam_keycloak_owner'),
    ('identity', 'iam_identity_owner'),
    ('notification', 'iam_notification_owner'),
    ('platform_audit', 'iam_audit_owner')
  ) AS expected(schema_name, owner_name)
  LOOP
    IF NOT EXISTS (
      SELECT 1 FROM pg_namespace n JOIN pg_roles r ON r.oid = n.nspowner
      WHERE n.nspname = s.schema_name AND r.rolname = s.owner_name
    ) THEN
      RAISE EXCEPTION 'IAM-R0 schema % missing or owned by unexpected role', s.schema_name;
    END IF;
    IF has_schema_privilege('public', s.schema_name, 'USAGE')
       OR has_schema_privilege('public', s.schema_name, 'CREATE') THEN
      RAISE EXCEPTION 'IAM-R0 schema % grants privileges to PUBLIC', s.schema_name;
    END IF;
  END LOOP;
END
$$;
SELECT 'IAM-R0 schema foundation assertions passed' AS result;
