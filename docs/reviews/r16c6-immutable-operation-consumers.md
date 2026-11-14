# R1.6C.6 — Immutable per-operation consumer obligations
Status: PATCHES COMMITTED / NOT COMPILED / TESTS NOT EXECUTED / RELEASE HOLD

Implemented:
- Liquibase migration r16e-001 creates enforcement_operation_consumers(operation_id,consumer_id,oauth_client_id).
- SubjectAccessService.block requires exactly two enabled bound mandatory consumers and inserts their snapshot in the same @Transactional block transaction as the state, operation and outbox.
- EnforcementReconciler requires exactly the frozen Collectra and App-B obligations and matching ACK versions, plus Keycloak applied, before ENFORCED.
- RevocationConsistencyPostgresIT fixtures updated to include frozen obligations and test mutation of live registry.
- GitHub Actions workflow r16c6-app-common-postgres.yml added.

Important limitations:
- No local Docker/Maven; workflow success not verified.
- Existing in-flight operations have no automatic snapshot backfill and remain unable to reach ENFORCED until manually reconciled.
- The captured consumer set is immutable but consumer OAuth client binding and enabled status are still read live; deliberate security tradeoff: a disabled or rebound consumer blocks completion rather than silently satisfying it.
- Existing ACK rows are keyed by consumer_id, not client binding epoch; rebind/disable/re-enable may allow stale ACK reuse. Must introduce registration generation/epoch for production.
- Global block idempotency replay and concurrent different-key requests need real PostgreSQL tests; currently distinct keys can create successive BLOCKED versions.
- No explicit terminal failure/retry transition proof, no real Keycloak outage/recovery test.
- App-B server, four-service E2E, S2 approval remain missing.
- Integration test currently uses test-specific DDL rather than Liquibase schema. Upgrade before release.
- Existing operation with older version can be ENFORCED based on later ACK; acceptable only if semantics explicitly guarantee monotonic BLOCKED and no reactivation.
- Keep release HOLD and feature flags disabled until evidence.
