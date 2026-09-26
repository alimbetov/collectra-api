# Codex Completion Contract — Pre-VC9 to Production-Ready Candidate

Status: FAIL-CLOSED
Branch: `spec/functional-hardening-user-journeys`

## Purpose

This contract prevents a Codex execution from stopping after local fixes or a subset of green tests. Completion means every required inventory row, decision, defect, T30 scenario, persona journey and release gate is reconciled against executable evidence.

This contract cannot guarantee infrastructure availability or make unresolved product decisions. It guarantees that such items remain explicit blockers and cannot be silently treated as complete.

## 1. Mandatory execution sequence

Do not skip phases.

### Phase A — security spine
Implement and execute T30-01..05:
- exhaustive Alpha/Beta tenant-owned resource isolation;
- foreign references in path/query/body;
- vertical authorization after accepted RBAC decision;
- human/service/platform trust-zone separation;
- stale user authorization and service credential revocation.

Exit: no unresolved P0; T30-01..05 PASS on exact SHA. D01-D15 are accepted; only a newly discovered D16+ semantic conflict may BLOCK this phase.

### Phase B — financial and ingestion spine
Implement and execute T30-07..14:
- collection/finance separation;
- allocation invariants;
- allocation idempotency/reversal;
- money/date transport;
- ingestion idempotency;
- schema/mapping canonicalization;
- durable safe import diagnostics;
- service-client lifecycle/scopes.

Exit: no incorrect monetary state, duplicate ingestion effect or stale client access.

### Phase C — Collections product closure
Resolve D02 and FH-001. If Collections remains in MVP, implement real `/collections` workspace:
queue -> filters -> detail -> lifecycle -> promise -> dispute -> action -> timeline -> close,
including paging/bounds, optimistic concurrency, 409 reconciliation, permissions, tenant isolation, deep links and browser tests.

A PlaceholderPage cannot pass this phase.

### Phase D — customer/campaign/content/delivery
Execute T30-15..28 including Customer 360 decision, concurrency, paid-recipient eligibility race, tenant-safe audience, immutable template/materialization, unsafe-content policy, file/asset isolation, attachment gate, duplicate broker events, retry/counters, ambiguous outcome contract, recovery, outbox and monitoring privacy.

### Phase E — frontend product coherence
Execute T30-29 against every primary route classified REAL/PARTIAL. Verify:
route loads; direct deep link; permission-aware navigation/actions; URL paging/filter/sort; empty/loading/error states; 401/403/404/409 behavior; no accidental placeholders; no client-side authoritative finance recalculation.

### Phase F — composite Golden Journey
Execute T30-30 from a clean environment through real authentication/API boundaries and PostgreSQL/RabbitMQ to deterministic channel adapter and monitoring.

### Phase G — production-readiness verification
Run all repository-required gates plus:
- backend formatting/static/architecture checks;
- unit + integration suites;
- PostgreSQL Testcontainers plus a real RabbitMQ Testcontainers broker-wiring smoke;
- frontend typecheck/test/build;
- OpenAPI compatibility;
- clean Liquibase/bootstrap;
- security isolation suite;
- Top-30 suite;
- Golden Journey.

Real external provider acceptance is not required in current scope.

## 2. Coverage reconciliation — anti-omission gate

Before declaring completion, regenerate/review repository inventory and reconcile all of these sets:

A. frontend routes/navigation entries;
B. backend controller endpoints;
C. permissions/roles/service scopes;
D. tenant-owned aggregates and child resources;
E. personas J01-J10;
F. T30-01..30;
G. existing smoke/security/integration/frontend tests;
H. open defects FH-*;
I. decisions D01-D15;
J. migrations/configuration/OpenAPI contracts.

Every item must map to one of:
`PASS(evidence)`, `NOT_APPLICABLE(reason)`, `DEFERRED_ACCEPTED(decision)`, or `BLOCKED(reason)`.

No orphan route, controller, permission, tenant-owned aggregate, P0/P1 defect or T30 scenario is allowed.

## 3. Traceability ledger

Maintain `docs/qa/pre-vc9-completion-ledger.md` with columns:

`Requirement | Source | Persona | Route/API | Permission | Tenant invariant | Test(s) | Result | Evidence SHA/run | Defect/Decision`.

Rows are append/update evidence, not prose assertions. PASS requires executed evidence.

## 4. Defect closure loop

For every FAIL:
reproducer -> severity -> raw relevant failure -> root cause -> fix -> focused regression -> affected journey -> affected security matrix -> full gate.

P0/P1: must close before completion.
P2: must close unless product owner explicitly waives it with rationale.
P3: may remain only if documented and does not falsify product completeness/navigation.

Do not suppress a failure, loosen an assertion, broaden permissions or add retries/timeouts merely to obtain green.

## 5. Production-safety review

Before completion inspect at minimum:
- secrets/config separation and no committed smoke credentials;
- production profiles reject test-only fault/scenario hooks;
- migrations work on clean DB and upgrade path represented by existing migration discipline;
- external calls have bounded timeout/retry behavior;
- logs/ProblemDetail/DTOs do not expose credentials/raw PII/provider bodies/storage internals;
- tenant ID comes from authenticated context, not trusted request data;
- async/outbox/recovery retain tenant ownership;
- health/metrics do not leak sensitive data;
- mock adapter cannot be accidentally selected where production policy forbids it.

Any violation is a defect, not documentation debt.

## 6. Final stop condition

Codex MUST NOT say DONE, READY, GREEN, PRODUCTION-READY or recommend VC-9 if any of the following is true:
- T30 item NOT_RUN/FAIL;
- unwaived P0/P1 exists;
- required P2 unresolved;
- new D16+ decision unresolved for an exercised scope;
- Collections remains an unintended placeholder;
- inventory contains an unmapped primary route/controller/permission/tenant resource;
- Golden Journey has not passed;
- full gate has not passed on exact HEAD;
- evidence ledger lacks exact SHA/run evidence.

Instead report `BLOCKED` and enumerate every remaining blocker.

## 7. Required final report

Produce:
1. exact branch + HEAD SHA;
2. accepted decisions and resulting contracts;
3. defects found/fixed/remaining by severity;
4. T30 table with 30/30 statuses;
5. persona J01-J10 status;
6. tenant-isolation resource matrix status;
7. route/controller/permission reconciliation counts and orphan count;
8. Golden Journey evidence;
9. backend/frontend/Testcontainers/OpenAPI/bootstrap evidence;
10. production-safety review;
11. remaining risks/explicit waivers;
12. verdict: `PRE-VC9_GATE_PASS` or `BLOCKED`.

Only `PRE-VC9_GATE_PASS` permits the next VC-9 work.
