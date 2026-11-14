# R1.6C.5 — Mandatory Consumers + ACTIVE Initialization
Status: PARTIAL CODE / NOT COMPILED / POSTGRESQL TESTS NOT EXECUTED / S2 HOLD

## Implemented
- App Common migration r16d-001 creates required_for_enforcement and seeds mandatory disabled consumer identities collectra-api and app-b-api. Consumer binding and activation require separate verified provisioning.
- EnforcementReconciler requires exactly two enabled required consumers, specifically collectra-api and app-b-api, each with non-null oauth_client_id, and both ACK >= operation target_version; also requires Keycloak applied.
- Unknown subject snapshot now returns 404, not ACTIVE version 0.
- Privileged initialization endpoint requires platform.subject.initialize scope. INSERT ON CONFLICT DO NOTHING is idempotent; it does not reset BLOCKED to ACTIVE.
- SubjectAccessService block no longer auto-creates ACTIVE state; unknown subjects return 409.
- PostgreSQL Testcontainers test cases authored for two ACKs, disabled App-B, initial ACTIVE and no resurrection from BLOCKED.

## Critical unproven/remaining
- Maven and Docker tests NOT run; CI PASS not evidenced. Test class uses isolated DDL, not full Liquibase migration. Requires build and migration integration tests.
- Enrollment authorization only checks scope, not proof of Keycloak subject existence/ownership. Scope assignment and provisioning workflow need separate security approval.
- ACK identity is OAuth azp and caller binding, not cryptographic proof of consumer application; must validate service client claims and tenant scope.
- Consumer registry topology is global, not frozen per operation; changing required consumers after operation creation can affect ENFORCED criteria. Must pin required set for each operation for production.
- Block idempotency and state transition require concurrency tests and consistent replay semantics.
- Keycloak dispatcher and consumer delivery remain separate, and Keycloak outages not proven.
- App-B Resource Server not implemented; no real four-service HTTP E2E, no fault injection or independent S2.
- Legacy Collectra HS256 tokens not covered by Keycloak global block.
- Keep all runtime enable flags false and release HOLD until tests pass and independent approval.
