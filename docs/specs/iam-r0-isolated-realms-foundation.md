# IAM-R0 — Isolated Realms and Shared PostgreSQL Foundation

Status: ACTIVE DESIGN / NOT IMPLEMENTED END-TO-END
Baseline: main@0b3f553d0c09fe77346341efa9dffa280958ee5f
Working branch: feat/iam-shared-platform

## Decisions

1. Collectra and every other application have **independent Keycloak realms**. No shared user directory, cross-realm SSO, user auto-provisioning or cross-application account linking.
2. One PostgreSQL database is shared by infrastructure, with separate schemas: `keycloak`, `identity`, `notification`, `platform_audit`; the existing Collectra schema is **not moved** by this slice.
3. Separate runtime and migration PostgreSQL credentials are mandatory before deploying Keycloak or shared APIs. No direct cross-schema SQL integration.
4. Identity keys must be scoped by `(issuer, subject)`. An email or Google identity in one realm must not identify a user in another.
5. Each backend must accept only its own realm issuer and intended audience; roles in one realm must not authorize another.
6. Google social login is configured separately in each realm; provider setup requires external OAuth client credentials and redirect URI registration.
7. Notification requests and delivery history are scoped to an authenticated application and tenant. Collectra business eligibility remains in Collectra.
8. Existing Collectra authentication, tenant RBAC, session and notification flows remain unchanged until regression and migration gates pass.

## IAM-R0 deliverables

- [x] Baseline inventory: existing `compose.yaml` uses PostgreSQL database `app_db`, runtime application uses Liquibase, and Spring Security OAuth2 Resource Server dependency is present.
- [x] SQL foundation script for **new, otherwise unused** schemas and non-login owner roles.
- [x] Executable SQL assertions for schema existence, ownership and no PUBLIC schema usage.
- [ ] Inventory existing users, roles, JWT validation, password hashes, session lifecycle and tenant permissions.
- [ ] Inventory actual current PostgreSQL schema, migration ownership and compatibility of Liquibase.
- [ ] Independently review security boundaries before adding Keycloak runtime.
- [ ] Configure separate LOGIN runtime/migration roles and secrets (not committed).
- [ ] Deploy pinned Keycloak with realm configuration and real PostgreSQL permission tests.
- [ ] Write RED/GREEN tests for cross-realm issuer, audience, roles, Google linking and tenant isolation.
- [ ] Run full backend/frontend regression and browser E2E on exact commit SHA.

## Local SQL foundation (manual, not auto-applied)

Against the existing `app_db` database, an authorized PostgreSQL administrator may run:

```bash
psql -X -v ON_ERROR_STOP=1 -d app_db -f scripts/iam/001-schema-foundation.sql
psql -X -v ON_ERROR_STOP=1 -d app_db -f scripts/iam/verify-schema-foundation.sql
```

This creates only new schemas and NOLOGIN owner roles. It does **not** migrate Collectra, provision Keycloak, configure application secrets or establish runtime DB access. Do not add it to Docker init-sql without first testing both fresh-volume and existing-volume behavior. Do not grant application roles membership in schema-owner roles. The target database name `collectra_platform` in the original proposal is deferred: renaming the live `app_db` is explicitly out of scope.

## Gates

G0: baseline/current schema and auth inventory accepted.
G1: PostgreSQL role-isolation tests pass against real PostgreSQL.
G2: two Keycloak realms with independent accounts, Google login and JWT isolation proven.
G3: Collectra legacy auth migration and tenant/RBAC regression pass.
G4: shared notification E2E, retry/recovery and cross-application isolation pass.

No gate may be marked VERIFIED from documentation or SQL inspection alone.
