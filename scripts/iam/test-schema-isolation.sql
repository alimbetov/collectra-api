-- IAM-R0.1: executable PostgreSQL negative/positive privilege tests.
-- Run as a database administrator in a disposable PostgreSQL database.
-- Entire fixture rolls back. No production execution.
\set ON_ERROR_STOP on
BEGIN;
CREATE ROLE iam_r01_collectra_test NOLOGIN;
CREATE ROLE iam_r01_identity_test NOLOGIN;
CREATE ROLE iam_r01_notification_test NOLOGIN;
CREATE ROLE iam_r01_outsider_test NOLOGIN;
CREATE SCHEMA iam_r01_collectra AUTHORIZATION iam_r01_collectra_test;
CREATE SCHEMA iam_r01_identity AUTHORIZATION iam_r01_identity_test;
CREATE SCHEMA iam_r01_notification AUTHORIZATION iam_r01_notification_test;
CREATE TABLE iam_r01_collectra.private_record (id integer PRIMARY KEY);
CREATE TABLE iam_r01_identity.private_record (id integer PRIMARY KEY);
CREATE TABLE iam_r01_notification.private_record (id integer PRIMARY KEY);
REVOKE ALL ON SCHEMA iam_r01_collectra, iam_r01_identity, iam_r01_notification FROM PUBLIC;
GRANT USAGE ON SCHEMA iam_r01_collectra TO iam_r01_collectra_test;
GRANT USAGE ON SCHEMA iam_r01_identity TO iam_r01_identity_test;
GRANT USAGE ON SCHEMA iam_r01_notification TO iam_r01_notification_test;
GRANT SELECT ON iam_r01_collectra.private_record TO iam_r01_collectra_test;
GRANT SELECT ON iam_r01_identity.private_record TO iam_r01_identity_test;
GRANT SELECT ON iam_r01_notification.private_record TO iam_r01_notification_test;

DO $$
DECLARE
  role_name text;
  schema_name text;
  other_schema text;
  privilege text;
BEGIN
  FOR role_name, schema_name IN
    SELECT * FROM (VALUES
      ('iam_r01_collectra_test','iam_r01_collectra'),
      ('iam_r01_identity_test','iam_r01_identity'),
      ('iam_r01_notification_test','iam_r01_notification')
    ) v(role_name,schema_name)
  LOOP
    IF NOT has_schema_privilege(role_name, schema_name, 'USAGE')
      OR NOT has_table_privilege(role_name, schema_name || '.private_record', 'SELECT') THEN
      RAISE EXCEPTION 'Expected own-schema SELECT for %', role_name;
    END IF;
    FOR other_schema IN SELECT unnest(ARRAY['iam_r01_collectra','iam_r01_identity','iam_r01_notification'])
    LOOP
      IF other_schema <> schema_name THEN
        FOREACH privilege IN ARRAY ARRAY['USAGE','CREATE'] LOOP
          IF has_schema_privilege(role_name, other_schema, privilege) THEN
            RAISE EXCEPTION '% unexpectedly has % on %',role_name,privilege,other_schema;
          END IF;
        END LOOP;
        FOREACH privilege IN ARRAY ARRAY['SELECT','INSERT','UPDATE','DELETE'] LOOP
          IF has_table_privilege(role_name, other_schema || '.private_record', privilege) THEN
            RAISE EXCEPTION '% unexpectedly has % on %',role_name,privilege,other_schema;
          END IF;
        END LOOP;
      END IF;
    END LOOP;
  END LOOP;
  FOREACH privilege IN ARRAY ARRAY['USAGE','CREATE'] LOOP
    IF has_schema_privilege('iam_r01_outsider_test', 'iam_r01_collectra', privilege)
       OR has_schema_privilege('iam_r01_outsider_test', 'iam_r01_identity', privilege)
       OR has_schema_privilege('iam_r01_outsider_test', 'iam_r01_notification', privilege) THEN
      RAISE EXCEPTION 'Outsider unexpectedly has schema privilege %', privilege;
    END IF;
  END LOOP;
END
$;
ROLLBACK;
SELECT 'IAM-R0.1 isolated fixture ACL assertions passed (rolled back)' AS result;
