# Smoke Test Technical Identities

Status: DESIGN — NO SECRETS IN GIT

## Purpose

Use deterministic test personas for browser smoke and backend/API smoke. Create two symmetric tenants so every happy path has a horizontal-isolation twin.

### Tenant A: smoke-alpha
- alpha-admin: TENANT_ADMIN; full tenant administration.
- alpha-operator: TENANT_USER + customer/receivable permissions available in the product model.
- alpha-collections: TENANT_USER + intended collection permissions once capability model is defined.
- alpha-content: TENANT_USER + TEMPLATE_READ/TEMPLATE_MANAGE/TEMPLATE_PUBLISH + required FILE permissions.
- alpha-campaign: TENANT_USER + CAMPAIGN_READ/CAMPAIGN_MANAGE + required template/file read.
- alpha-support: TENANT_USER + CAMPAIGN_READ only; diagnostic read, no campaign mutation.
- alpha-auditor: TENANT_USER + read-only selected permissions.
- alpha-restricted: TENANT_USER with one deliberately minimal permission for vertical-escalation tests.
- alpha-service-ingest: ROLE_SERVICE scopes integration:imports:create/read.

### Tenant B: smoke-beta
Mirror alpha-admin/operator/collections/content/campaign/support/auditor/restricted/service-ingest. Tenant B exists primarily to supply foreign IDs and prove non-disclosure.

### Platform
- platform-smoke-admin: PLATFORM_SUPER_ADMIN.
- A tenant principal must be denied platform endpoints.
- Platform principal must not silently acquire tenant-business context.

## Provisioning rules

- Never commit passwords, client secrets, refresh tokens or JWTs.
- Provision only in local test/dev or isolated CI environments.
- Prefer API/bootstrap creation using the same public/admin flows under test.
- Generate random strong passwords/secrets per run; export them only to the test process.
- Use reserved @example.test addresses and unmistakable SMOKE-* display names.
- Tag/namespace fixtures so cleanup is deterministic.
- Do not reuse production-like emails, phones or customer data.
- Browser smoke logs in as human personas; API smoke additionally exercises service clients.
- Destroy/revoke sessions and service credentials after the suite.

## Isolation fixture pattern

For every domain create paired records:
A.customer / B.customer; A.invoice / B.invoice; A.file / B.file; A.template / B.template; A.campaign/run/message / B equivalents; A.collection case / B case.

Then execute:
1. Alpha actor accesses Alpha resource — expected authorized result.
2. Alpha actor supplies Beta ID — expected non-disclosure/denial and zero state change.
3. Restricted Alpha actor calls Alpha manage API directly — expected 403 when capability policy requires it.
4. Alpha service token calls human endpoint — 403.
5. Alpha human token calls service-only ingestion endpoint — 403.
6. Revoke/block/permission-change actor and retry old token/session — established invalidation contract must hold.

This fixture model supports both frontend smoke (visible navigation/actions) and backend smoke (direct adversarial calls). Frontend success never substitutes for backend authorization assertions.
