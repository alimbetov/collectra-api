# R1.6C.7 — ACK binding epoch and concurrency hardening
Status: PATCHES COMMITTED / NOT COMPILED / TESTS NOT EXECUTED / RELEASE HOLD

Changes:
- r16f-001 migration adds binding_epoch to consumer registry, operation obligations and ACKs. PostgreSQL trigger increments epoch when OAuth client binding or enabled state changes. Legacy ACK epoch 0 cannot satisfy new operations.
- Block operation snapshots the exact binding epoch atomically.
- ACK endpoint records epoch and resets version when registration epoch changes.
- Reconciler requires both ACK version and epoch to match immutable per-operation obligations and current active consumer binding.
- SubjectAccessService.isAllowed is now fail-closed for unknown identities, with database errors propagating.
- Testcontainers test added using real Liquibase changelog and two concurrent same-key transactions, plus rebind scenario.
- CI workflow includes the new tests.

Unresolved:
- Maven/Docker unavailable; compile and PostgreSQL tests unexecuted. Liquibase SQL trigger parsing needs CI verification.
- The controller ACK protocol is still a trust assertion by a scoped client; durable consumer application requires E2E proof.
- Binding rotation intentionally prevents old operations from reaching ENFORCED until explicit administrative reconciliation. No recovery/migration protocol yet.
- No verified different-key concurrency test or crash/restart fault injection.
- State machine and Keycloak outage handling need further review.
- Independent App-B not implemented. Four-service E2E, <=30s global revocation, and independent S2 APPROVE not proven.
- Feature flags should remain disabled and release HOLD.
