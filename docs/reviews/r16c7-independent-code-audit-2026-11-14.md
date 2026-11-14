# R1.6C.7 — Independent code/process audit (2026-11-14)
Status: NOT RELEASE READY / NO VERIFIED GREEN / S2 HOLD
Scope: current feat/iam-shared-platform implementation, read-only code audit.

## Confirmed code-level findings
P0-01: RevocationSnapshotConsumer.synchronize() obtains service token, applies snapshot, then sends ACK; ACK proves a DB write was attempted, not actual HTTP denial of already issued user JWT by an independent resource server. No App-B service/E2E evidence.
P0-02: SubjectAccessService.block() increments version on an already BLOCKED subject with a new idempotency key, generating duplicate operations/outbox; no no-op or explicit reassert policy.
P0-03: Replay response hardcodes STATE_COMMITTED regardless of actual enforcement_operations.state. Misreports completed/in-progress status.
P0-04: Reconciler requires current registry oauth_client_id/binding_epoch to equal frozen operation snapshot. After rotation old operations can remain PROPAGATING permanently; no rebind reconciliation/repair protocol.
P0-05: Legacy already-issued HS256 JWTs not covered by global Keycloak block; two-mode security cutover remains unproven.
P0-06: Unknown subject snapshot returns 404, but no integrated verified enrollment transaction across Keycloak, App Common and local identity links. Existing users may fail closed without onboarding.
P1-07: Consumer registry readiness count and snapshot INSERT are separate statements; concurrent registration changes can cause incomplete snapshots/rollback. Need registry locking or single-statement atomic validation.
P1-08: ACK binding epoch is read separately from consumer lookup; concurrent rebind can associate a late ACK with new epoch. Consumer identity+epoch must be selected atomically, and ideally ACK must bind to a consumer-generated durable enforcement receipt.
P1-09: RevocationDispatcher uses a 2-minute lease and marks published_at when Keycloak succeeds, not when consumers apply. Restart/partial failure tests absent.
P1-10: RevocationSnapshotConsumer fetches up to 10000 subjects without pagination, requests OAuth token per subject and suppresses exceptions without metrics.
P1-11: KeycloakAdminClient compares issuer to internal admin base URL; reverse proxy canonical issuer may differ.
P1-12: Liquibase migration r16f contains a PostgreSQL $$ function body inside sqlFile without splitStatements/endDelimiter configuration. Must run migration on real PostgreSQL; SQL splitting risk.
P1-13: RevocationConsistencyPostgresIT setup sends multiple CREATE TABLE statements via a single JdbcClient.update(); JDBC driver execution semantics need verification. Test fixtures also differ from production migrations.
P1-14: RevocationLiquibaseConcurrencyIT invokes Liquibase CommandScope without explicit classpath resource accessor and depends on runtime Liquibase version/API. Build and actual migration success unverified.
P1-15: GitHub Actions YAML workflow exists but no observed PASS; Docker/Testcontainers and Maven execution unavailable in this audit environment.
P1-16: ACTIVE -> BLOCKED state in ledger is distinct from operation STATE_COMMITTED -> PROPAGATING -> ENFORCED; an operation may remain PROPAGATING indefinitely without deadline or escalated PARTIAL/FAILED transition.
P1-17: No verified load/fault tests proving <=30 seconds invalidation across both services.

## Release gates
1. Build both modules and run full unit tests.
2. Execute Liquibase migration on fresh and upgraded PostgreSQL.
3. Concurrent idempotency same/different keys, registry rotation, ACK replay, restart, Keycloak outage tests.
4. Enforce explicit onboarding and canonical issuer configuration.
5. Independent App-B and 4-service real JWT HTTP E2E; confirm actual rejection, not ACK only.
6. Independent S2 approval. Keep feature flags disabled and HOLD.

No fixes claimed in this audit; report is a code inspection, not executed QA.
