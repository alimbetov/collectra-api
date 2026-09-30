# Project Consistency Defect Remediation Ledger

Status: ACTIVE  
Branch: `fix/project-consistency-defects`  
Created: 2026-09-30  
Parent audit baseline: `bfc93f7ac9b585847bc6b5a11b17bac8011367c1`

## Goal

Collect and remediate confirmed cross-module/state-machine defects discovered by the repository-wide architecture and process-interaction audits.

A defect is CLOSED only after:
1. root cause is fixed;
2. regression/crash-window evidence is added;
3. relevant focused tests are green;
4. final release verification is green on one exact SHA.

## Defect queue

| ID | Severity | Seam | Defect | Remediation | State |
|---|---|---|---|---|---|
| PCD-001 | P1 | delivery request -> worker claim | worker claim rechecked required attachments but not required document links | symmetric required attachment + document-link readiness gate in `MessageStateService.begin()`; regression test | IMPLEMENTED / CI PENDING |
| PCD-002 | P0 | generation worker -> downstream message readiness | JVM crash can leave `GenerationJob.PROCESSING` indefinitely; redelivery cannot reclaim it | stale PROCESSING recovery, 30m processing timeout, bounded attempts, 8h absolute job age, durable retry/failure event, crash-window tests | IMPLEMENTED / CI PENDING |
| PCD-003 | P1 | generation completion -> attachment + document link | sequential independent downstream transactions rely on redelivery to finish partial fan-out | shared completion orchestrator + prove partial-commit redelivery and exactly-one delivery intent | TEST IN PROGRESS |
| PCD-004 | P0 | receivable reversal -> collection | confirmed: allocation reversal can restore invoice debt while an existing CollectionCase remains CLOSED/PAID | business lifecycle decision required: reopen old case vs create a new collection cycle; until resolved CLOSED/PAID must not be interpreted as current no-debt truth | BLOCKED ON DOMAIN DECISION |
| PCD-005 | P1 | projection rebuild -> READY/FAILED state | communication projection lacked build ownership fencing; stale worker could finalize after another instance reclaimed BUILDING | add build_id migration and fence READY/FAILED by current build owner; continue late-arrival audit separately | IMPLEMENTED / CI PENDING |
| PCD-006 | TBD | outbox DEAD -> aggregate workflow | verify DEAD events cannot leave invisible permanently non-terminal business aggregates | operational/state reconciliation audit | AUDIT REQUIRED |
| PCD-007 | P1 | ingestion recovery -> broker wake-up | recovery committed batch state then published directly to RabbitMQ, leaving a DB/broker crash window | central durable IngestionRequestPublisher; recovery appends outbox request in same DB transaction | IMPLEMENTED / CI PENDING |
| PCD-008 | P1 | schedulers -> multi-instance runtime | continue scheduler-by-scheduler ownership/claim audit after projection and ingestion findings | campaign/file/projection scheduler verification | AUDIT REQUIRED |


## Normative remediation contract

The following rules are normative for every processor, worker, listener, scheduler and projection builder in this branch. Terms such as "retry", "stale", "exactly once" or "recovery" MUST NOT be used without the corresponding owner and durable-state semantics below.

### State ownership

Every durable non-terminal state MUST identify exactly one authoritative aggregate/table and one component allowed to perform each transition. A scheduler discovers candidates; it does not become the state owner merely by selecting them.

### Claim and lease

Any state that means work is exclusively owned (`PROCESSING`, `BUILDING`, equivalent) MUST use one of:

1. a transaction-scoped database lock where the complete work is inside that transaction; or
2. a durable lease/claim token (`workerId`, `buildId`, attempt id) plus an expiry/stale timestamp.

If ownership can be taken over after timeout, every completion/failure update MUST be fenced by the current claim token. A timestamp-only takeover without fenced finalization is invalid.

### Stale semantics

`stale` means `claim_started_at <= now - configured_timeout` using the injected `Clock`. The timeout is a lease expiry, not an automatic business failure. Recovery MUST lock/revalidate the candidate before changing it because discovery results can be stale.

### Retry semantics

A retry MUST define:
- which component owns the retry decision;
- which counter is incremented and at which transition;
- maximum attempts;
- next-attempt timestamp/backoff where applicable;
- whether absolute age also limits retries;
- the durable wake-up mechanism.

Changing state to retryable without a durable future wake-up is invalid.

### Durable asynchronous handoff

A database transition that requires later broker work MUST append its outbox event in the same database transaction. Direct DB-commit-then-Rabbit publication is not permitted for correctness-critical handoffs, including recovery paths.

At-least-once broker delivery is assumed. Consumers MUST therefore converge under duplicate delivery.

### Exactly-once terminology

"Exactly once" in this project means **exactly one logical durable business effect**, enforced by a database uniqueness/idempotency/state guard. It does not mean RabbitMQ delivers a message physically once.

### External side effects

Before a non-transactional external side effect, the system MUST persist enough attempt identity/state to reconcile an ambiguous outcome. A timeout after an external call MUST NOT cause blind resend when duplicate external execution is unsafe.

### Terminal failure

Retry exhaustion MUST lead to a defined terminal state and, when downstream aggregates are waiting, a durable downstream failure signal. A DEAD outbox event by itself does not satisfy business terminality unless the aggregate contract explicitly declares it terminal.

### Fan-out

When one event updates multiple aggregates in independent transactions, each branch MUST be idempotent and replayable. Partial commit followed by redelivery MUST converge, and any exactly-one downstream intent MUST have a durable guard.

### Multi-instance schedulers

Schedulers MUST be safe when N application instances run the same schedule concurrently. Candidate selection alone is not a claim. Work requires locked/fenced claim or an idempotent state transition whose affected-row count proves ownership.

### Verification

A remediation item is `VERIFIED` only when its focused failure-window/concurrency test and the required repository CI gates pass on the same exact SHA. Source inspection or an older green run is not verification.


## PCD-002 target lifecycle

```text
PENDING
  -> PROCESSING
       -> COMPLETED
       -> handled failure -> PENDING/retry
       -> crash/orphan
            -> stale after 30m
            -> recovery
                 -> retry if attempt/age budget remains
                 -> FAILED when retry budget exhausted or absolute age >= 8h
                      -> durable DOCUMENT_GENERATION_FAILED
                      -> attachment/link FAILED
                      -> message/campaign terminal accounting
```

The 30-minute threshold is the stale-processing lease, not the business job lifetime. Eight hours is the absolute age ceiling. Defaults must be configurable and testable with `Clock`.

## Working rules

- no speculative fixes: confirm the seam/invariant first;
- preserve PostgreSQL as business source of truth;
- use transactional outbox for durable asynchronous handoffs;
- consumers remain idempotent under at-least-once RabbitMQ delivery;
- recovery is bounded and observable;
- no final green claim without exact-SHA CI evidence.


## Root-cause analysis: PCD-002

### Failure window

The generation listener receives a Rabbit message and the worker calls `GenerationJobStateService.begin()`. That method commits `PENDING -> PROCESSING` in its own transaction before template rendering, object storage and final completion.

A hard process/JVM/node failure after that commit bypasses the listener's Java exception handler. RabbitMQ may redeliver the message, but `begin()` intentionally treats PROCESSING as already claimed and returns no work. Before this remediation there was no lease expiry or stale PROCESSING recovery.

### Why existing safeguards did not catch it

The existing tests covered two different safety properties:

- creation atomicity: GenerationJob and initial outbox request do not split;
- mutual exclusion: two concurrent workers cannot both claim the same PENDING job.

Both were valid, but neither covered liveness after the winning worker disappears. In fact, the mutual-exclusion rule made redelivery insufficient by design.

The missing system invariant was:

> Every durable non-terminal claimed state must have a lease/heartbeat or a bounded recovery path.

### Remediation

Generation processing now has configurable recovery bounds:

- stale processing lease: 30 minutes;
- absolute job age: 8 hours;
- maximum generation attempts: 3;
- recovery scan batch: 100;
- scan delay: 1 minute.

A stale job with remaining budget is transactionally changed back to PENDING and a fresh `DOCUMENT_GENERATION_REQUESTED` is appended to the PostgreSQL outbox in the same transaction.

A stale job with exhausted attempt/age budget becomes FAILED and a `DOCUMENT_GENERATION_FAILED` outbox event is appended in the same transaction. Existing downstream consumers then fail the required attachment/document link and terminally account the Message/CampaignRun.

The normal creation path and recovery path share `DocumentGenerationRequestPublisher`; recovery does not implement a separate direct RabbitMQ publication path.

### Remaining proof

Focused compile/format and PostgreSQL integration tests must pass on the exact branch SHA before PCD-002 can move to VERIFIED. The partial fan-out crash window remains PCD-003.


## Analysis: PCD-003

Attachment and document-link completion intentionally use independent short transactions. Making the Rabbit listener one large transaction would increase lock scope and would still not make object storage/broker effects atomic.

The convergence contract is instead:
- each correlation transitions only from PENDING;
- a replayed already-terminal correlation is a no-op;
- delivery eligibility is evaluated after each successful transition;
- Message.deliveryRequestedAt is the durable exactly-once logical delivery-intent guard;
- the delivery event is appended to the outbox in the same transaction that first marks delivery requested.

A shared `MessageGenerationCompletionService` now makes this fan-out an explicit orchestration seam and enables fault-injection/redelivery testing. PCD-003 closes only after the partial-commit replay test proves the above contract.


## Root-cause analysis: PCD-005 communication projection ownership

Financial projections already used a per-build UUID and required `status=BUILDING AND build_id=:buildId` before READY/FAILED finalization. Communication projections only used a stale timestamp to allow BUILDING takeover.

That made claim acquisition mutually exclusive only at the instant of claim. After the building timeout, worker B could reclaim the same tenant/day while worker A was still executing. Worker A retained an unfenced unconditional READY update and an upsert-style FAILED path, so it could overwrite worker B's state.

Remediation adds `communication_reporting_projection_state.build_id`, assigns a fresh UUID on every claim, and fences READY/FAILED finalization by that UUID. Legacy BUILDING rows are invalidated during migration so no pre-fencing worker state is treated as owned.

This finding establishes a project-wide rule: timeout-based takeover is safe only when completion/failure is fenced by the current lease/build owner.


## Root-cause analysis: PCD-007 ingestion recovery handoff

Initial ingestion reservation already used the transactional outbox, but the recovery scheduler evolved separately and used direct `RabbitTemplate.convertAndSend` after committing recovery state.

For stale PROCESSING batches this created:

```text
PROCESSING -> recover() -> DB commit -> [process crash] -> Rabbit publish
```

A crash in the marked window can lose the wake-up. The recovered state is durable, but liveness then depends on whether a later scheduler query happens to select that exact state again.

Recovery now uses the same `IngestionRequestPublisher` as initial reservation. State recovery and the new `INTEGRATION_INGESTION_REQUESTED` outbox row are committed atomically. Retryable wake-ups also go through the outbox. This removes the recovery-specific DB/Rabbit dual-write.


## Branch ownership decision

All newly confirmed cross-processor consistency defects are fixed only in `fix/project-consistency-defects`.

`fix/pre-channel-release-gate` is retained as the audit/gate baseline. At the time of this decision, `fix/project-consistency-defects` is 27 commits ahead and 0 commits behind that branch, with merge-base `bfc93f7ac9b585847bc6b5a11b17bac8011367c1`.

This avoids parallel remediation histories and manual duplication. After the remediation ledger reaches verified status on one exact SHA, the resulting branch is integrated forward as a single coherent change set.


## Confirmed domain ambiguity: PCD-004 allocation reversal after PAID closure

Current code permits this sequence:

```text
Invoice outstanding > 0
 -> allocate payment
 -> Invoice PAID / outstanding = 0
 -> CollectionCase.close(PAID)
 -> reverseAllocation()
 -> Invoice outstanding > 0
 -> CollectionCase remains CLOSED / PAID
```

The financial source of truth is the Invoice, so the historical close reason cannot be used as current debt truth after reversal.

The technical specification MUST choose one lifecycle rule before automatic remediation:

1. **reopen same case** — reversal transitions the PAID case back to an active state and records a REOPENED_AFTER_REVERSAL event; or
2. **new collection cycle** — the historical PAID case remains immutable and a new CollectionCase is opened/eligible to open for the restored debt.

Until that decision is made, no processor may suppress eligibility solely because a historical case is CLOSED/PAID. Automatic reopening is intentionally not implemented from inference alone.
