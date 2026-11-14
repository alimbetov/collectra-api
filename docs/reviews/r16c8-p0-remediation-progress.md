# R1.6C.8 — P0 remediation progress
Status: CODE COMMITTED / BUILD NOT VERIFIED / RELEASE HOLD

Applied:
- SubjectAccessService.block serializes requests per idempotency key and per issuer+subject using transaction-scoped PostgreSQL advisory locks.
- A distinct key for an already BLOCKED subject reuses the latest matching enforcement operation/version and records its own idempotency mapping. No duplicate version/outbox is created by this path.
- Replays now return the current enforcement_operations.state.
- Mandatory registry rows are locked FOR SHARE through transaction commit while the operation consumer snapshot is inserted.
- ACK insert/update now selects active OAuth client identity and binding epoch in one SQL statement, rejecting changed client binding.
- Liquibase r16f sqlFile configured splitStatements:false for PL/pgSQL trigger body.
- Added regression test for distinct-key block replay and state accuracy.

Still P0:
- No real independent App-B and no proof that ACK implies actual denial of previously issued JWT.
- Consumer rebind after operation snapshot intentionally blocks ENFORCED; explicit recovery policy is missing.
- No full onboarding proof or legacy token cutover.
- Dispatcher crash/retry and >=2 concurrent worker tests incomplete.
- No verified CI PASS or independent S2 APPROVE.

Tests were authored but not executed here. Do not enable dispatch/reconcile in production.
