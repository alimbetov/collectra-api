# Production Smoke Suite Roadmap

Documentation lifecycle: ACTIVE CONTRACT — ongoing smoke catalogue; individual roadmap states require executable evidence before promotion.
Current documentation precedence: [`docs/README.md`](../README.md).

Status: PLANNED
Baseline: PRE-VC9 gate merged to `main` by PR #141
Current first extension: `json-xlsx-template-channel-smoke`

## Purpose

Keep a small set of orthogonal production-confidence smokes. Each smoke must prove a critical business or infrastructure boundary end-to-end and must not become a duplicate of lower-level integration/unit coverage.

The JSON/XLSX template-channel smoke remains independently mergeable. The scenarios below are follow-up gates, not blockers that silently expand its Definition of Done.

## Existing anchors

Before adding a new smoke, reconcile it against existing T30, Golden Journey and integration tests. Reuse existing fixtures/helpers where safe and strengthen an existing smoke instead of cloning it when the boundary is already represented.

## Priority matrix

| ID | Priority | Smoke | Primary invariant |
|---|---|---|---|
| PS-01 | P0 | JSON/XLSX Template Channel | Different supported inputs converge to one canonical model and correct HTML/PDF/channel-ready output |
| PS-02 | P0 | Tenant Isolation Attack Matrix | Alpha cannot read, reference or mutate Beta resources through path/query/body/nested/async boundaries |
| PS-03 | P0 | Persona/RBAC | API, routes, navigation and commands enforce the same capability model |
| PS-04 | P0 | Real RabbitMQ Delivery | Outbox -> real broker topology -> consumer -> worker -> deterministic adapter |
| PS-05 | P0 | Idempotency/Duplicate Delivery | Retry, broker redelivery and concurrent execution produce one logical side effect |
| PS-06 | P0 | Failure/Recovery | Transient, permanent and ambiguous outcomes follow safe retry/recovery semantics |
| PS-07 | P1 | Crash Recovery | Commit/publish/consume crash windows do not lose or duplicate logical work |
| PS-08 | P1 | Collection Lifecycle | Overdue -> collection -> payment/allocation -> eligibility recheck -> resolved |
| PS-09 | P1 | Campaign Delivery | eligibility -> CampaignRun -> Message -> attachment gate -> delivery -> counters |
| PS-10 | P1 | Template Lifecycle | draft -> validation/preview -> publish -> immutable version -> generation |
| PS-11 | P1 | File Security | upload/download/attachment enforce tenant, integrity and authorization boundaries |
| PS-12 | P1 | Clean Bootstrap/Upgrade | empty DB bootstrap and supported schema upgrade reach a usable application |
| PS-13 | P2 | Frontend Business Journey | browser-facing route/action flow remains coherent with backend capabilities |
| PS-14 | P2 | Large Batch | realistic batch volume preserves correctness, bounded processing and partial-failure semantics |

## PS-02 Tenant Isolation Attack Matrix

Use at least tenants Alpha and Beta. Exercise foreign identifiers through:

```text
path id
query/filter id
request-body id
nested resource id
mappingProfileVersionId
templateVersionId
generatedDocumentId
attachment/file id
campaign/message id
async/outbox/recovery references
```

Every denial must assert both the external response and **zero cross-tenant mutation/side effect**.

## PS-05 Idempotency and duplicate delivery

Exercise:

- duplicate HTTP idempotency key;
- concurrent equivalent command;
- duplicate broker delivery;
- stale worker completion;
- duplicate generation completion;
- duplicate recovery attempt.

The invariant is logical exactly-once behavior, not transport exactly-once.

## PS-06 Ambiguous provider outcome

Required communication safety case:

```text
send command
 -> provider may accept
 -> connection times out before Collectra has definitive response
 -> ACCEPTANCE_UNKNOWN (or current equivalent state)
 -> no blind automatic resend
 -> explicit reconciliation/recovery policy
```

A timeout after possible acceptance must never be treated as proof of non-delivery.

## PS-07 Crash recovery

Cover the dangerous durable boundaries:

```text
DB commit -> crash -> before broker publish -> restart -> outbox recovery

broker delivery -> side effect/commit -> crash before ack -> redelivery -> idempotent no-op

generation completion -> crash before attachment/message continuation -> restart/recovery
```

Use deterministic failure injection/test hooks. Do not use arbitrary sleeps as proof.

## PS-08 Collection lifecycle

Minimum business scenario:

```text
Customer
 -> Contract
 -> overdue Invoice
 -> Collection
 -> campaign eligibility
 -> partial Payment
 -> Allocation
 -> outstanding reduced
 -> eligibility recheck
 -> final Payment
 -> Allocation
 -> outstanding zero
 -> Collection resolved
 -> no further collection communication
```

Assert money/currency/status/counters and absence of stale eligible work.

## PS-09 Campaign delivery

Prove:

```text
segment/eligibility
 -> CampaignRun
 -> Message
 -> required attachment PENDING blocks delivery
 -> attachment READY
 -> one delivery request
 -> RabbitMQ
 -> worker
 -> deterministic adapter
 -> CampaignRun counters consistent
```

## PS-12 Bootstrap/upgrade

Two independent modes:

1. empty PostgreSQL -> Liquibase -> bootstrap -> critical API usable;
2. supported previous schema snapshot -> Liquibase upgrade -> data preserved -> critical API usable.

No FAILED `databasechangelog` entries and no manual repair steps.

## Test design rules

1. A smoke owns one primary invariant.
2. Real infrastructure boundaries are used where the invariant depends on them (PostgreSQL/RabbitMQ).
3. Direct listener/service invocation cannot be presented as broker/API evidence.
4. Async assertions use bounded polling against observable durable state.
5. Synthetic deterministic data only.
6. No live provider calls.
7. Failures identify the stage and durable IDs without leaking credentials or sensitive payloads.
8. A new smoke must not weaken existing T30/Golden Journey contracts.
9. Every final PASS belongs to an exact commit SHA.
10. A later code commit invalidates final evidence for the affected smoke until rerun.

## Recommended execution order

```text
PS-01 JSON/XLSX Template Channel
 -> PS-02 Tenant Isolation Attack Matrix
 -> PS-07 Crash Recovery
 -> PS-06 Failure/Ambiguous Provider Outcome
 -> PS-08 Collection Lifecycle
 -> PS-09 Campaign Delivery
```

PS-03/04/05 should first be reconciled with existing hardening/T30/RabbitMQ coverage; create new tests only for reproduced coverage gaps.

## CI strategy

Do not put every expensive scenario in every developer feedback loop.

- focused smoke command/tag: fast enough for PR verification;
- critical P0 production smokes: required CI gate where runtime is acceptable;
- expensive crash/large-batch/upgrade scenarios: dedicated CI job while still required before release;
- preserve exact-SHA evidence for release qualification.

## Completion rule

A smoke is VERIFIED only when its scenario executed successfully against the exact stated SHA with the infrastructure boundary claimed by the test. Source inspection, test existence or an older green run is not sufficient.
