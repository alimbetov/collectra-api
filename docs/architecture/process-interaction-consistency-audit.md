# Collectra Process Interaction Consistency Audit

Status: ACTIVE  
Started: 2026-09-30  
Baseline: `fix/pre-channel-release-gate`

## 1. Purpose

This audit verifies consistency between processors, state machines and asynchronous handoffs across the complete Collectra runtime. A module can be locally correct while the system is globally incorrect if its output state is not consumed, is consumed twice, or has different terminal semantics in the next processor.

The audit therefore follows state ownership and producer/consumer seams rather than package boundaries.

## 2. Audit questions for every seam

For every transition A -> B verify:

1. authoritative state owner;
2. producer preconditions;
3. durable state written before handoff;
4. transaction boundary;
5. handoff mechanism (direct call / outbox / RabbitMQ / scheduler);
6. consumer claim/idempotency guard;
7. duplicate delivery behavior;
8. crash before and after external side effect;
9. retry ownership and retry limit;
10. terminal states and downstream interpretation;
11. tenant/correlation identifiers;
12. recovery for orphan non-terminal states;
13. counters/projections updated exactly once;
14. executable evidence.

## 3. End-to-end processor graph

```text
IntegrationSource + published Schema/Mapping
  -> IngestionApplicationService.reserve
  -> IngestionReservationWriter [IngestionBatch + OutboxEvent]
  -> OutboxPublisher
  -> RabbitMQ
  -> IngestionListener
  -> IngestionWorker
  -> MappingExecutionService
  -> IngestionRecordProcessor
  -> BusinessRecordPersistenceService
  -> Customer / Invoice / Payment

ReceivableService
  -> Allocation / Reversal
  -> authoritative Invoice financial state
  -> CollectionService
  -> Campaign audience / CampaignEligibilityService

CampaignService / CampaignDispatchScheduler
  -> CampaignRun + CampaignRecipient snapshot
  -> CampaignMessageMaterializer
  -> Template binding + current eligibility
  -> Message
  -> optional GenerationJob + attachment/document-link correlation
  -> MessageDeliveryRequestService

GenerationJobService [GenerationJob + OutboxEvent]
  -> OutboxPublisher
  -> RabbitMQ
  -> DocumentGenerationListener
  -> DocumentGenerationWorker
  -> GeneratedOutputService
  -> GenerationJobStateService
  -> completion/failure OutboxEvent
  -> RabbitMQ
  -> MessageAttachmentGenerationListener
  -> MessageAttachmentService / MessageDocumentLinkService
  -> MessageDeliveryRequestService

MessageDeliveryRequestService [Message.deliveryRequestedAt + OutboxEvent]
  -> OutboxPublisher
  -> RabbitMQ
  -> MessageDeliveryListener
  -> MessageDeliveryWorker
  -> MessageStateService
  -> DeliveryGateway
  -> SENT / RETRY_WAIT / FAILED / UNKNOWN
  -> MessageRecoveryService
  -> CampaignRun counters

Financial / communication source state
  -> projection rebuild schedulers
  -> READY projection state
  -> tenant analytics queries
```

## 4. Current seam ledger

| Seam | Durable handoff | Duplicate guard | Recovery | State |
|---|---|---|---|---|
| ingestion reserve -> broker | IngestionBatch + transactional outbox | batch claim/status | stale/retry scheduler | REVIEWED |
| broker -> ingestion worker | IngestionBatch status/version | QUEUED/RETRY_WAIT claim | retry + stale PROCESSING | REVIEWED |
| record processor -> canonical business state | canonical identity + per-record diagnostic | diagnostic + persistence identity | batch replay | AUDIT_REQUIRED |
| receivable -> collection | DB authoritative invoice | domain/version checks | synchronous | AUDIT_REQUIRED |
| collection -> campaign eligibility | DB current-state lookup | current eligibility | materialization recheck | AUDIT_REQUIRED |
| campaign run -> materializer | CampaignRun/Recipient | locked run + unique message recipient | repeated batch materialization | AUDIT_REQUIRED |
| materializer -> generation | GenerationJob + outbox | job state | broker retry | REVIEWED |
| generation -> attachment/link | completed/failed outbox | PENDING-only transitions | broker redelivery | PARTIAL |
| attachment/link -> delivery request | Message deliveryRequestedAt + outbox | markDeliveryRequested | outbox recovery | PARTIAL |
| delivery request -> worker | outbox + Rabbit queue | message state | broker/recovery | REVIEWED |
| worker -> provider | DeliveryAttempt STARTED before call | deliveryKey/attempt | UNKNOWN instead of blind resend | REVIEWED |
| worker -> CampaignRun counters | same DB transaction in state service | message terminal state guard | recovery path | REVIEWED |
| financial source -> daily projection | projection state/build fencing | buildId/state | scheduler reconciliation | AUDIT_REQUIRED |
| projection -> analytics | READY/raw routing | state-based routing | raw fallback | PARTIAL |

## 5. Confirmed / active findings

### PI-001 — delivery begin gate is not symmetric with delivery-request gate

`MessageDeliveryRequestService.requestIfEligible()` checks both required attachments and required document links before creating the delivery intent.

`MessageStateService.begin()`, however, re-checks only required attachments before moving a QUEUED message to PROCESSING.

This creates an asymmetric invariant between the producer of the delivery event and the actual delivery consumer. It is safe only if a required document link can never regress or be missing after `deliveryRequestedAt` is set. That invariant is not currently encoded in `MessageStateService`.

Classification: logical seam risk; regression scenario and fix required unless immutability is proven by domain/schema.

Required contract: the worker-side claim must enforce every readiness condition that is authoritative at send time, or the domain must make post-request regression structurally impossible and document that guarantee.

### PI-002 — document completion fan-out uses sequential independent transactions

`MessageAttachmentGenerationListener` consumes one completion/failure event and invokes attachment and document-link processors sequentially. Each service owns its own transaction.

The individual transitions are PENDING-only/idempotent, which makes broker redelivery capable of continuing after a partial commit. This is a sound recovery shape, but it requires explicit integration coverage for:
- first downstream commit succeeds;
- second downstream fails;
- broker redelivery occurs;
- first downstream is a no-op;
- second completes;
- exactly one delivery intent exists.

Classification: coverage/reliability gap until executable evidence exists.

### PI-003 — ingestion retry has two possible wake-up sources

Storage retry can schedule a delayed Rabbit retry while the durable batch is also visible to the recovery scheduler as RETRY_WAIT when `nextAttemptAt` becomes due.

The worker claim guard makes duplicate wake-ups converge: only QUEUED/RETRY_WAIT at the due time can start, while a duplicate delivered during PROCESSING or after terminal completion becomes a no-op.

Classification: acceptable at-least-once design, but requires a concurrency regression proving one effective processing attempt per state claim.

### PI-004 — generation job can be orphaned in PROCESSING

`GenerationJobStateService.begin()` durably changes a job from PENDING to PROCESSING before rendering/storage. `DocumentGenerationListener` handles Java exceptions by calling retry/fail, but there is no generation-job recovery scheduler/repository query for stale PROCESSING jobs.

A JVM/process crash after `begin()` but before the listener's exception handling or `complete()` can therefore leave:

```text
GenerationJob = PROCESSING
MessageAttachment/DocumentLink = PENDING
Message = QUEUED
CampaignRun = RUNNING
```

indefinitely. A broker redelivery does not repair this because `GenerationJobStateService.begin()` returns null for PROCESSING and the worker exits without completing or retrying the job.

Classification: confirmed orphan-state defect / release blocker for generated attachment/link campaigns.

Required remediation: bounded stale PROCESSING recovery with attempt-aware retry/fail semantics and durable re-publication, plus crash-window integration coverage.

### PI-005 — ingestion initial handoff is transactionally durable

`IngestionReservationWriter` persists the IngestionBatch and appends `INTEGRATION_INGESTION_REQUESTED` to the transactional outbox in one transaction. Initial broker publication is therefore not a DB/broker dual-write.

Classification: consistent.

## 6. High-priority audit targets

1. worker-side document-link readiness gate;
2. attachment/link partial fan-out redelivery;
3. allocation reversal after PAID collection closure;
4. campaign cancellation/recipient terminal accounting;
5. materializer crash after Message insert but before generation/delivery intent;
6. generation output stored but job completion transaction fails;
7. retry/DLQ state agreement for generation jobs;
8. outbox DEAD event vs business aggregate non-terminal state;
9. UNKNOWN delivery and CampaignRun completion semantics;
10. stale READY financial projection after late-arriving source data;
11. communication projection late/duplicate outcome events;
12. scheduler concurrency across multiple application instances.

## 7. Completion rule

This audit is complete only when every non-terminal durable state has:
- a known owner;
- a bounded next action;
- duplicate-safe processing;
- crash recovery;
- a terminal/error route visible to operations;
- tenant-safe correlation;
- executable evidence for the critical failure windows.
