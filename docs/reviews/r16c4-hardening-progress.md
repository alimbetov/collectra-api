# R1.6C.4 — Security & Reliability Hardening (incremental)
Status: PATCHES COMMITTED / NOT COMPILED / TESTS NOT EXECUTED / RELEASE HOLD

## Changes in this iteration
1. Correct App Common YAML binding: keycloak and revocation moved under app-common, leaving management endpoints under management.
2. Collectra AuthorizationVersionFilter tenant-user branch now resolves local UUID from authenticated principal name rather than Keycloak global sub. Service branch unchanged.
3. App Common bearer-only SecurityFilterChain now stateless and CSRF-disabled (not a BFF).
4. Collectra KeycloakDomainAuthorizationFilter servlet auto-registration disabled; it is ordered only in Spring Security chain.
5. SubjectAccessService block accepts authenticated actor and mandatory Idempotency-Key, serializes concurrent same-key requests via PostgreSQL transaction-scoped advisory lock, replays original operation/version, returns conflict on changed subject. Existing block endpoint now requires key.
6. Added YAML regression test and local-principal regression test.

## Still BLOCKING release
- No Maven/Docker in execution environment; no compile, Testcontainers, CI PASS, or HTTP E2E evidence.
- Mandatory consumer topology is not frozen; ENFORCED may be premature if App-B absent.
- Initial ACTIVE onboarding/ledger registration missing; local consumers reject version 0.
- No independent App-B server or real Keycloak-issued user-token E2E.
- Legacy HS256 tokens and Keycloak migration/cutover not covered by global revocation.
- Polling first 10000 only, machine token per subject, no operational error metrics.
- Unblock policy and BLOCKED->ACTIVE transitions not specified.
- Keycloak external issuer/internal admin URL mismatch; no real admin permission test.
- Real Liquibase migrations not executed in PostgreSQL Testcontainers.
- Block idempotency needs concurrent PostgreSQL tests and a business decision on repeated block with distinct keys.
- Review of filter principal handling, authorization order and security exception translation remains necessary.

## Next mandatory sequence
1. Compile/test via CI and correct build errors.
2. PostgreSQL integration tests for idempotency replay/concurrency and migrations.
3. Safe ACTIVE onboarding, mandatory two-consumer ACK topology.
4. Durable consumer operational hardening and independent App-B.
5. Four-service E2E with fault injection and independent S2 APPROVE.

Branch feat/iam-shared-platform only. main unchanged.
