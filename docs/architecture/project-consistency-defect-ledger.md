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
| PCD-003 | P1 | generation completion -> attachment + document link | sequential independent downstream transactions rely on redelivery to finish partial fan-out | prove partial-commit redelivery and exactly-one delivery intent | AUDIT/TEST REQUIRED |
| PCD-004 | TBD | receivable reversal -> collection | verify reversal after PAID close cannot leave contradictory financial/workflow state | determine invariant from current domain then fix/test if confirmed | AUDIT REQUIRED |
| PCD-005 | TBD | financial source -> READY projection | verify late-arriving/reversed data cannot leave stale READY projection authoritative | inspect invalidation/watermark/reconciliation contract | AUDIT REQUIRED |
| PCD-006 | TBD | outbox DEAD -> aggregate workflow | verify DEAD events cannot leave invisible permanently non-terminal business aggregates | operational/state reconciliation audit | AUDIT REQUIRED |
| PCD-007 | TBD | schedulers -> multi-instance runtime | verify concurrent schedulers use claims/locks and do not double-process | scheduler-by-scheduler audit | AUDIT REQUIRED |

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
