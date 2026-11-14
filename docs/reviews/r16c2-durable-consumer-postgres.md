# R1.6C.2 — Durable Revocation Consumer + PostgreSQL tests
Status: CODE ADDED / TESTS NOT EXECUTED / REAL HTTP E2E NOT PROVEN / S2 HOLD

## Code
- Collectra RevocationSnapshotStore applies authenticated upstream snapshots into PostgreSQL using atomic INSERT .. ON CONFLICT; version may advance, or same version+state may refresh freshness; stale and conflicting updates fail. BLOCKED cannot be undone by older ACTIVE.
- Scheduled RevocationSnapshotConsumer uses dedicated Keycloak client_credentials token to call App Common protected snapshot endpoint for VERIFIED linked subjects; applies local snapshot transactionally, then sends authenticated ACK. If ACK fails, next poll retries; no false ACK before commit. On lost events, periodic pull can reconcile. Disabled by default.
- Collectra GlobalRevocationGate checks local PostgreSQL ACTIVE freshness <=30s; missing/stale/BLOCKED state fails closed.
- PostgreSQL Testcontainers tests cover ACTIVE->BLOCKED, stale state, duplicates, older versions, subject isolation. They use an isolated test table matching snapshot DDL, not the full Liquibase migration; migration validation remains outstanding.

## P0 blockers
- Testcontainers suite has NOT been executed. No Maven build, live Keycloak, Collectra HTTP E2E, App-B Resource Server, network partition, independent S2, or CI evidence.
- Central App Common returns ACTIVE version 0 for unknown subjects. Consumer explicitly rejects version 0, so initial ACTIVE must be created through verified enrollment. No unsafe auto-seeding.
- Polling is limited to first 10,000 VERIFIED linked subjects; pagination/partitioning and bounded propagation are NOT proven. Per-subject HTTP requests do not scale.
- Current consumer obtains machine token per subject and silently catches errors; operational metrics, alerts, retries with backoff and service token caching are needed.
- ACK is not proof of HTTP denial at consumer; E2E must assert same previously issued JWT is denied by actual Collectra and App-B.
- App Common's ENFORCED transition depends on registered active consumers; deployment must enforce mandatory consumer registry and durable reconciliation.
- Legacy Collectra HS256 tokens remain outside Keycloak global block; migration and session cutover must explicitly address them.
- Current keycloak mode is disabled. Do not enable without verified identity links, central ACTIVE initialization, mandatory consumer registry, operational credentials and independent review.

## Required executable acceptance
1. Start PostgreSQL 16, Keycloak, App Common, Collectra and independent App-B.
2. Register machine clients with scoped aud and azp, provision verified identity link, initialize central ACTIVE v1.
3. Obtain one user JWT for Collectra; call tenant-protected API 200; block global subject to v2.
4. Assert exact same JWT denied after propagation (401/403), local snapshot v2 BLOCKED, ACK v2 and operation ENFORCED.
5. Repeat with App-B JWT; cross-audience tokens rejected.
6. Drop/duplicate/reorder messages; stop Keycloak and central service; prove stale snapshots deny and reconciliation repairs missed updates.
7. Run full Liquibase migrations, all PostgreSQL tests and independent S2 APPROVE.

## Manual test command (not run)
`./mvnw -Dtest=RevocationSnapshotPostgresIT test` (requires Docker/Testcontainers and Maven wrapper)
No changes to main; only feat/iam-shared-platform.
