# A02 — Golden Journey and Failure Journey Assurance

Historical audit baseline: `main@165eb9639fa013f77a24e0a8e2b225df3a94c186`
Current reconciliation baseline: `main@9b3369d52af5bfaf0436019b4b8eb97e51f0b956`
Current main CI: run #2801 / `36713091538` is the active exact-SHA qualification run at the time of this documentation revision.

Status: **IN PROGRESS**

## Closure rule

A02 is CLOSED only when the complete business chain is backed by executable repository evidence on one exact SHA. Existing component tests are supporting evidence, not a substitute for cross-boundary verification.

## Golden Journey

| Step | Outcome | Existing evidence | A02 status |
|---|---|---|---|
| G01 | Tenant is established and isolated | Foundation/security integration tests | VERIFY |
| G02 | Service client authenticates with bounded scopes | Production ingestion / RabbitMQ smoke | VERIFY |
| G03 | Integration source is configured | RabbitMQ smoke | VERIFY |
| G04 | Source schema and mapping are versioned and ready | Production ingestion acceptance | VERIFY |
| G05 | Ingestion crosses durable outbox/RabbitMQ boundary | Production ingestion acceptance | VERIFY |
| G06 | Customer canonical state is created/reused | ingestion tests | VERIFY |
| G07 | Invoice canonical state is created | ingestion tests | VERIFY |
| G08 | Payment is registered and allocated | RabbitMQ smoke / receivable tests | VERIFY |
| G09 | Late payment/reversal preserves financial invariants | financial analytics tests | VERIFY |
| G10 | Collection case/action lifecycle is usable | collection integration tests | VERIFY |
| G11 | Published template is usable | JSON/XLSX/template/channel smoke | VERIFY |
| G12 | Campaign audience/run materializes messages | campaign materialization tests | VERIFY |
| G13 | Required generated attachment becomes READY | document-link/channel smoke | VERIFY |
| G14 | Message crosses real RabbitMQ topology | RabbitMQ message delivery smoke | VERIFY |
| G15 | Delivery reaches terminal state exactly once | delivery smoke + worker matrix | VERIFY |
| G16 | Financial analytics reflects business events | TenantFinancialAnalyticsIntegrationTest | VERIFY |
| G17 | Communication analytics reflects delivery events | CommunicationAnalyticsIntegrationTest | VERIFY |
| G18 | Foreign tenant cannot observe or reference journey state | FunctionalHardeningSecuritySmokeIntegrationTest | VERIFY |

## Failure Journey Matrix

| ID | Failure | Required invariant | Evidence target |
|---|---|---|---|
| F01 | Duplicate transport ingestion | one batch/idempotent replay | ProductionIngestionAcceptanceTest |
| F02 | Duplicate business object | REUSED or CONFLICT, never silent overwrite | ProductionIngestionAcceptanceTest |
| F03 | Invalid schema/mapping/input | durable safe diagnostic, no corrupt canonical state | ingestion/import diagnostics tests |
| F04 | RabbitMQ unavailable | durable outbox state survives and can be re-driven | outbox/integration recovery test |
| F05 | Worker crashes after claim | stale PROCESSING recovered without duplicate side effects | ingestion/message/generation recovery tests |
| F06 | Duplicate broker delivery | idempotent no-op / no double counters | listener/worker scenario tests |
| F07 | Provider timeout/ambiguous result | UNKNOWN/retry policy, never false SENT | KumoMTA/worker tests |
| F08 | Required attachment not ready | delivery is fail-closed | message readiness tests |
| F09 | Generation stuck PROCESSING | bounded recovery/re-drive or terminal failure | GenerationJobRecoveryIntegrationTest |
| F10 | Message stuck PROCESSING | bounded recovery with correct run counters | MessageRecoveryConcurrencyIntegrationTest |
| F11 | Late payment | projection/raw equality and outstanding correctness | financial analytics integration |
| F12 | Allocation reversal | restored outstanding + analytics equality | receivable/financial analytics tests |
| F13 | Cross-tenant read/reference/filter | 404/empty/forbidden without existence oracle | FunctionalHardeningSecuritySmokeIntegrationTest |
| F14 | Disabled/revoked identity | access rejected across sync/async entry points | tenant/security integration tests |

## Execution sequence

1. Re-run and inspect all existing evidence on this branch.
2. Add a single A02 cross-boundary executable Golden Journey where current tests leave subsystem seams unverified.
3. Add missing failure-injection tests for F01–F14.
4. Record every discovered product defect in the assurance ledger; fix P0/P1 in this branch.
5. Run Spotless, focused tests, full PostgreSQL Testcontainers verify, RabbitMQ smoke, frontend gate.
6. Record exact SHA and CI run. Only then mark A02 CLOSED.


## Pre-Channel Release Gate relationship

A02 owns the Golden/Failure Journey assurance catalogue. `docs/qa/pre-channel-release-gate.md` consumes this evidence for release qualification: PC-18 consumes the positive G01-G18 chain, PC-19 consumes F01-F14 plus its explicit negative-delivery assertions, and PC-20 is the final same-exact-SHA aggregation gate. Do not duplicate an A02 scenario solely to satisfy a second ledger.
