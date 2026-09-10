# Reliability Vertical Slice — Implementation Checklist

This checklist operationalizes:

- `reliability-vertical-slice-spec.md` — scope and architectural requirements;
- `reliability-vertical-slice-implementation-design.md` — implementation-level decisions.

The checklist is intentionally ordered to keep every change reviewable and avoid introducing infrastructure before it is needed.

## Global rules before implementation

- [ ] Keep one Spring Boot application; do not split the slice into microservices.
- [ ] Reuse the existing shared outbox; do not create another outbox subsystem.
- [ ] Reuse current Template/Locale/Import/Document modules instead of duplicating their logic.
- [ ] Use PostgreSQL constraints as the final concurrency/idempotency invariant.
- [ ] Do not use JVM `synchronized`, Redis locks or distributed-lock libraries for DB-owned races.
- [ ] Do not hold DB row locks while waiting on RabbitMQ/provider/object-storage network I/O.
- [ ] Inject `Clock` into new retry/recovery/overdue code; reliability tests must not depend on wall-clock sleeps.
- [ ] Bound persisted error codes/messages and never persist stack traces in business tables.
- [ ] Every async side-effect consumer must be idempotent because RabbitMQ delivery is at-least-once.
- [ ] Every tenant-owned lookup in application/business code must include tenant scope.

---

## Phase 1 — Outbox reliability

### Schema — `022-outbox-reliability.sql`

- [ ] Introduce typed `OutboxEventStatus`: `PENDING`, `PROCESSING`, `RETRY_WAIT`, `PUBLISHED`, `DEAD`.
- [ ] Add/normalize `attempt_count`, `next_attempt_at`.
- [ ] Add `locked_at`, `locked_by`, `published_at`.
- [ ] Add bounded `last_error_code`, `last_error_message`.
- [ ] Add CHECK `attempt_count >= 0` where practical.
- [ ] Add partial ready-polling index matching `(status, next_attempt_at, created_at)` for `PENDING/RETRY_WAIT`.
- [ ] Add stale-processing index on `locked_at` only if used by recovery query.

### State model

- [ ] Add domain transition methods: `claim`, `markPublished`, `scheduleRetry`, `markDead`, `recover`.
- [ ] Reject invalid state transitions.
- [ ] Increment `attempt_count` when a real publish attempt starts, not during stale recovery.
- [ ] Clear lock metadata after terminal/retry transition.

### Atomic claim

- [ ] Implement `OutboxClaimService` using PostgreSQL `FOR UPDATE SKIP LOCKED` or equivalent atomic claim.
- [ ] Claim in a short transaction and commit before RabbitMQ I/O.
- [ ] Make batch size configurable; default 50.
- [ ] Add two-publisher concurrency test with deterministic barrier/latch.
- [ ] Assert claimed ID sets do not intersect.

### Routing

- [ ] Add one explicit `OutboxEventRouter`; do not introduce a handler/plugin framework yet.
- [ ] Route `DOCUMENT_GENERATION_REQUESTED` to the existing document exchange/routing key.
- [ ] Route `COMMUNICATION_DISPATCH_REQUESTED` to the communication exchange/routing key.
- [ ] Unknown event type must fail closed.
- [ ] Unknown event type must become `DEAD` immediately with stable error code such as `UNKNOWN_EVENT_TYPE`.
- [ ] Invalid payload before publish must be classified as permanent and must not loop indefinitely.

### AMQP metadata

- [ ] Set AMQP `messageId = outboxEvent.id`.
- [ ] Add stable `x-event-id` and `x-event-type` headers.
- [ ] Add `x-tenant-id` where tenant exists.
- [ ] Add aggregate ID/type headers for diagnostics.
- [ ] Do not redesign all existing payload JSON into a new envelope in this phase.

### Broker confirms

- [ ] Enable correlated Rabbit publisher confirms.
- [ ] Enable publisher returns.
- [ ] Send using `CorrelationData(outboxEvent.id)`.
- [ ] Wait for confirm using bounded configurable timeout; default 5 seconds.
- [ ] Positive ACK -> short transaction -> `PUBLISHED + published_at`.
- [ ] NACK -> short transaction -> `RETRY_WAIT`.
- [ ] Confirm timeout -> `RETRY_WAIT`; treat as uncertain delivery, not guaranteed non-delivery.
- [ ] Unroutable returned message -> retry initially, then `DEAD` after max attempts.
- [ ] Verify no JDBC transaction remains open while waiting for broker confirmation.

### Retry/recovery

- [ ] Add small `OutboxRetryPolicy`, not a generic retry framework.
- [ ] Default max attempts: 6.
- [ ] Persist retry schedule; no tight in-memory loops.
- [ ] Suggested delays: immediate / 1m / 5m / 15m / 1h / DEAD.
- [ ] Add stale `PROCESSING` recovery with default processing timeout 2m.
- [ ] Stale recovery -> `RETRY_WAIT`, clear lock, error code `PROCESSING_TIMEOUT_RECOVERED`.
- [ ] Do not increment attempts during stale recovery.

### Phase 1 gate

- [ ] Unknown event never becomes `PUBLISHED`.
- [ ] Two publishers cannot claim the same row.
- [ ] Broker ACK is required before `PUBLISHED`.
- [ ] NACK/timeout remains retryable.
- [ ] Max attempts ends in `DEAD`.
- [ ] Process death after claim does not permanently stall the event.
- [ ] `OutboxPublisher` is a thin coordinator rather than one large transactional method.

---

## Phase 2 — Communication core

### Schema — `023-communication-core.sql`

- [ ] Add `communications`.
- [ ] One Communication row represents one recipient + one channel + one logical send intent.
- [ ] Store tenant, business reference, optional `next_action_id`, channel, recipient, locale.
- [ ] Store `template_id` and immutable `template_version_id` used for rendering.
- [ ] Store final rendered `subject` and unified `content` snapshot.
- [ ] Add tenant-scoped deterministic `idempotency_key`.
- [ ] Add status/timestamps/error fields and optimistic-lock `version`.
- [ ] Add DB unique `(tenant_id, idempotency_key)`.
- [ ] Add only indexes corresponding to actual lookup/polling queries.

### CommunicationAttempt

- [ ] Add `communication_attempts`.
- [ ] Persist one row per actual gateway invocation.
- [ ] Add `attempt_number`, provider, provider message ID, timestamps, result/error.
- [ ] Add unique `(communication_id, attempt_number)`.
- [ ] Allocate attempt number while serializing on parent Communication; no unprotected `MAX+1`.

### State model

- [ ] Implement `PENDING`, `QUEUED`, `SENDING`, `RETRY_WAIT`, `SENT`, `FAILED`, `CANCELLED`.
- [ ] Retry must reuse snapshotted subject/content and template version.
- [ ] Duplicate message after `SENT`, `FAILED`, or `CANCELLED` must not invoke gateway again.

### Gateway contract

- [ ] Define small provider-neutral `ChannelGateway`.
- [ ] Define provider-neutral `OutboundMessage`.
- [ ] Define provider-neutral `DeliveryResult` with `SUCCESS`, `TEMPORARY_FAILURE`, `PERMANENT_FAILURE`.
- [ ] Build a simple `Map<ChannelType, ChannelGateway>` registry from Spring beans.
- [ ] Fail startup on duplicate gateway implementation for the same channel.
- [ ] Do not leak provider SDK DTOs into domain/application packages.

### Mock gateway

- [ ] Implement deterministic test/local mock gateway.
- [ ] Support success outcome.
- [ ] Support transient failure outcome.
- [ ] Support permanent failure outcome.
- [ ] Support timeout/exception outcome.
- [ ] Support deterministic `TEMPORARY_FAILURE -> SUCCESS` sequence for retry E2E.
- [ ] No random failure behavior.

### Creation and dispatch

- [ ] Create Communication only after template resolution/render succeeds.
- [ ] In one local transaction: persist Communication + append `COMMUNICATION_DISPATCH_REQUESTED` outbox + mark `QUEUED`.
- [ ] Use first-slice idempotency key `nextActionId:channel:normalizedRecipient`.
- [ ] On concurrent duplicate insert, reload the DB winner after unique violation.
- [ ] Dispatcher short tx: lock communication, state guard, set `SENDING`, create attempt, commit.
- [ ] Invoke `ChannelGateway` outside DB transaction.
- [ ] Result short tx: persist attempt result and Communication state.
- [ ] Reuse Rabbit retry queues for retry timing; do not add a second retry scheduler/table.
- [ ] Treat DB attempt history as source of truth; Rabbit retry header is transport metadata only.
- [ ] Define stale `SENDING` recovery using latest attempt `started_at` where possible instead of adding redundant lease columns.

### Phase 2 gate

- [ ] Sequential duplicate creation reuses same Communication.
- [ ] Concurrent duplicate creation produces one Communication.
- [ ] One attempt exists per actual mock gateway invocation.
- [ ] Duplicate delivery after `SENT` causes zero additional calls.
- [ ] Concurrent duplicate delivery causes at most one active provider call.
- [ ] Temporary failure -> `RETRY_WAIT`.
- [ ] Permanent/max attempts -> `FAILED`.
- [ ] Rendered content is unchanged across retries.

---

## Phase 3 — Minimal business domain

### Schema — `024-collection-core.sql`

#### Customer

- [ ] Add tenant-owned `Customer` with UUID, optional external ID, name, status, preferred locale, one email and one phone.
- [ ] Use only `ACTIVE/INACTIVE` statuses for this stage.
- [ ] Add partial unique `(tenant_id, external_id)` where external ID is not null.
- [ ] Do not create a generalized contact child model yet.

#### Receivable

- [ ] Add tenant-owned `Receivable` linked to Customer.
- [ ] Use `BigDecimal` / `NUMERIC(19,2)` for amounts.
- [ ] Add amount/outstanding DB CHECK constraints.
- [ ] Add tenant-scoped optional external ID uniqueness.
- [ ] Use statuses `OPEN`, `PARTIALLY_PAID`, `PAID`, `CANCELLED`.
- [ ] **Do not persist `OVERDUE` as source-of-truth state**; derive overdue from date/outstanding/status.

#### CollectionCase

- [ ] Add tenant-owned `CollectionCase` linked to Receivable.
- [ ] Use only `OPEN`, `RESOLVED` in first slice.
- [ ] Add partial unique constraint preventing more than one `OPEN` case per tenant+receivable.

#### NextAction

- [ ] Add tenant-owned `NextAction` linked to CollectionCase.
- [ ] Initial type only: `PAYMENT_OVERDUE_NOTIFICATION`.
- [ ] Use states `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`.
- [ ] Add deterministic idempotency key and tenant-scoped unique constraint.
- [ ] Initial key may be `PAYMENT_OVERDUE:{receivableId}`.

### Business service

- [ ] Implement one concrete `OverdueCollectionService`; no rule DSL/strategy engine.
- [ ] Eligibility: `dueDate < businessDate && outstandingAmount > 0 && status not PAID/CANCELLED`.
- [ ] Input business date explicitly or via injected Clock.
- [ ] Find/create OPEN case idempotently.
- [ ] Find/create overdue NextAction idempotently.
- [ ] Resolve concurrent unique-race by reloading DB winner.

### First-slice routing policy

- [ ] Channel is EMAIL only.
- [ ] Recipient is `customer.email`.
- [ ] Locale = customer preferred locale, else tenant default.
- [ ] Template/preset business code = `PAYMENT_OVERDUE`.
- [ ] Missing email -> NextAction `FAILED` + stable `CUSTOMER_EMAIL_MISSING`.
- [ ] Do not silently fallback to SMS.

### Phase 3 gate

- [ ] Running overdue evaluation twice creates one active CollectionCase.
- [ ] Running it concurrently creates one active CollectionCase.
- [ ] Exactly one first reminder NextAction exists for the receivable.
- [ ] Not-overdue receivable produces no collection action.

---

## Phase 4 — E2E overdue -> SENT

### Orchestration

- [ ] Add one concrete `PaymentOverdueNotificationService`; do not add a generic action-executor framework.
- [ ] Load NextAction/Case/Receivable/Customer using tenant scope.
- [ ] Mark action `PROCESSING` when execution begins.
- [ ] Resolve EMAIL recipient and requested locale.
- [ ] Reuse existing `TemplateLocaleResolver` and published `PAYMENT_OVERDUE` TemplateVersion.
- [ ] Build canonical render Map/DTO; do not pass JPA entities into `TemplateRenderer`.
- [ ] Add contract test that required PAYMENT_OVERDUE placeholders exist in the canonical render model.
- [ ] Render final subject/content once.
- [ ] Create/reuse Communication idempotently.
- [ ] Persist Communication + Outbox in the same local transaction.
- [ ] Link Communication to NextAction.

### Completion propagation

- [ ] `Communication.SENT -> NextAction.COMPLETED`.
- [ ] `Communication.FAILED -> NextAction.FAILED`.
- [ ] `Communication.RETRY_WAIT -> NextAction remains PROCESSING`.
- [ ] Persist terminal Communication + NextAction result in one DB transaction.

### E2E infrastructure

- [ ] PostgreSQL Testcontainer.
- [ ] RabbitMQ Testcontainer.
- [ ] Real Spring transactions.
- [ ] Deterministic mock `ChannelGateway`; no external network credentials.
- [ ] Deterministic injected Clock/business date.

### Happy path assertions

- [ ] Exactly 1 Customer.
- [ ] Exactly 1 Receivable.
- [ ] Exactly 1 OPEN CollectionCase.
- [ ] Exactly 1 PAYMENT_OVERDUE_NOTIFICATION NextAction.
- [ ] Exactly 1 Communication.
- [ ] Exactly 1 communication outbox event.
- [ ] Exactly 1 SUCCESS CommunicationAttempt.
- [ ] Communication is `SENT`.
- [ ] NextAction is `COMPLETED`.
- [ ] Rendered content contains fixture invoice number.
- [ ] Used TemplateVersion is PUBLISHED and tenant-owned.

### Retry path assertions

- [ ] Attempt #1 = `TEMPORARY_FAILURE`.
- [ ] Communication = `RETRY_WAIT`.
- [ ] NextAction remains `PROCESSING`.
- [ ] Attempt #2 = `SUCCESS`.
- [ ] Communication = `SENT`.
- [ ] NextAction = `COMPLETED`.
- [ ] Still only one logical Communication.

---

## Phase 5 — Template concurrency

### Schema — `025-template-concurrency.sql`

- [ ] Keep/verify DB uniqueness `(template_id, template_version)`.
- [ ] Add partial unique index for one `PUBLISHED` version per `(template_id, locale, channel)`.
- [ ] Add/use `@Version` optimistic locking on mutable TemplateVersion.

### Version creation

- [ ] Add tenant-scoped `DocumentTemplate findForUpdate` repository query using `PESSIMISTIC_WRITE`.
- [ ] Lock stable parent row before `MAX(version)+1` allocation.
- [ ] Do not introduce a separate sequence table.
- [ ] Concurrent createVersion test must produce distinct sequential versions without data corruption.

### Editing

- [ ] Expose entity revision/rowVersion to frontend DTO.
- [ ] Require revision on mutable draft update.
- [ ] Stale edit -> HTTP 409 + stable `TEMPLATE_VERSION_CONFLICT`.

### Publication

- [ ] Lock parent template for publish operation.
- [ ] Archive previous published locale/channel version and publish new version atomically.
- [ ] Concurrent publish can never leave two PUBLISHED rows.
- [ ] Rollback during publish leaves previous PUBLISHED version intact.

---

## Phase 6 — Import + document idempotency hardening

### Import — preserve existing design

- [ ] Review existing `015-import-batch-reliability.sql` before adding migration.
- [ ] Preserve `ImportBatchReservationService` `REQUIRES_NEW` reservation pattern.
- [ ] Verify tenant-scoped DB uniqueness for idempotency key.
- [ ] Contract: same key + same request hash -> same batch.
- [ ] Contract: same key + different request hash -> conflict.
- [ ] Add sequential duplicate test.
- [ ] Add concurrent duplicate test with actual overlap.
- [ ] On concurrent unique violation, reload winning row and verify request hash.
- [ ] Do not use `synchronized` to solve reservation race.
- [ ] Verify `FAILED` retains structured bounded error.
- [ ] Ensure import retry/upsert does not duplicate Customer/Receivable rows by using tenant+external ID constraints.

### Document generation

- [ ] Add deterministic `generation_jobs.idempotency_key` only if not already available.
- [ ] Add unique `(tenant_id, idempotency_key)`.
- [ ] Build deterministic key from stable request/business inputs; never include timestamp/random UUID.
- [ ] Canonicalize/hash normalized payload deterministically if payload contributes to key.
- [ ] Sort output formats before hashing/key generation.
- [ ] Duplicate API/business request reuses existing GenerationJob.
- [ ] Duplicate Rabbit message after `COMPLETED` is a no-op.
- [ ] Use deterministic storage object key such as `reports/{tenantId}/{jobId}/{format}.{ext}`.
- [ ] Retry after upload-before-DB-completion must reuse/overwrite the same object key rather than create a second file.
- [ ] Reuse existing `started_at` for stale PROCESSING recovery if sufficient; do not add lease columns only for symmetry.
- [ ] Preserve current document Rabbit retry/dead routing unless an actual defect requires change.

### Migration

- [ ] Create `026-idempotency-hardening.sql` only if schema changes are actually required.

---

## Phase 7 — Tenant isolation

- [ ] Maintain inventory of every tenant-owned aggregate and required scoped repository lookup.
- [ ] Customer -> tenant-scoped lookup.
- [ ] Receivable -> tenant-scoped lookup.
- [ ] CollectionCase -> tenant-scoped lookup.
- [ ] NextAction -> tenant-scoped lookup.
- [ ] Communication -> tenant-scoped lookup.
- [ ] CommunicationAttempt -> access through tenant-scoped Communication.
- [ ] DocumentTemplate/TemplateVersion -> tenant-scoped lookup.
- [ ] ImportBatch -> retain `findByIdAndTenantId` behavior.
- [ ] GenerationJob -> retain `findByIdAndTenantId` behavior.
- [ ] Add ArchUnit/static test for obvious forbidden unscoped application/API usages.
- [ ] Do not treat ArchUnit as the only proof of isolation.
- [ ] Add Tenant A principal + Tenant B resource ID -> `404` integration tests for critical APIs.
- [ ] Async workers must carry tenant ID in message metadata/payload and query tenant-owned rows by tenant+ID.
- [ ] Do not rely on HTTP `TenantContext` inside background consumers.

---

## Phase 8 — Reliability regression suite

### Unit tests

- [ ] Outbox state transitions.
- [ ] Outbox retry policy.
- [ ] Unknown event route classification.
- [ ] Overdue eligibility rule.
- [ ] Idempotency key generation.
- [ ] DeliveryResult classification.

### PostgreSQL integration tests

- [ ] `SKIP LOCKED` concurrent claim.
- [ ] Partial unique open CollectionCase constraint.
- [ ] Communication concurrent idempotency insert.
- [ ] NextAction concurrent idempotency insert.
- [ ] Template concurrent version allocation.
- [ ] Template optimistic locking.
- [ ] Template one-PUBLISHED invariant.
- [ ] Import concurrent idempotency reservation.
- [ ] GenerationJob concurrent idempotency creation.

### RabbitMQ E2E tests

- [ ] Happy overdue -> SENT.
- [ ] Temporary failure -> retry -> SENT.
- [ ] Same communication delivery twice -> one logical send after terminal success.
- [ ] Outbox confirm/NACK/timeout behavior where practical with test configuration/stubbed confirm boundary.

### Determinism rules

- [ ] Use barriers/latches to force real concurrency overlap.
- [ ] No `Thread.sleep(60000)` retry tests.
- [ ] Use injected Clock and direct recovery invocation.
- [ ] Assert both DB final state and mock gateway invocation count.

---

## Phase 9 — Deferred real provider adapters

Do not start until Phases 1–8 are green in CI.

- [ ] `EmailChannelGateway`.
- [ ] `SmsChannelGateway`.
- [ ] `WhatsAppChannelGateway`.
- [ ] `TelegramChannelGateway`.
- [ ] Map provider-specific errors to Collectra-neutral outcomes.
- [ ] Use `communicationId` as provider client/idempotency reference where supported.
- [ ] Add delivery receipt/callback processing only after initial send pipeline is reliable.

---

## Configuration checklist

- [ ] Consolidate new messaging properties under `collectra.messaging`.
- [ ] Use `@ConfigurationProperties` once the group grows beyond a few fields.
- [ ] Default outbox batch size = 50.
- [ ] Default publisher confirm timeout = 5s.
- [ ] Default outbox processing timeout = 2m.
- [ ] Default outbox max attempts = 6.
- [ ] Default communication max attempts = 4.
- [ ] Do not introduce DB-backed dynamic configuration yet.

---

## API/error checklist

- [ ] Extend existing `ApiExceptionHandler`; do not create another global exception handler.
- [ ] Cross-tenant ID access -> 404.
- [ ] Template optimistic conflict -> 409 / `TEMPLATE_VERSION_CONFLICT`.
- [ ] Reused idempotency key with different request -> 409 / stable idempotency error code.
- [ ] Missing customer email -> persisted domain failure with `CUSTOMER_EMAIL_MISSING`.
- [ ] Do not expose SQL constraint names, stack traces, provider secrets or unmasked recipients.
- [ ] Keep first public API minimal; do not build search/export endpoints before E2E is green.

---

## Observability checklist

- [ ] Add Micrometer counters/timers using existing Spring Boot tooling only.
- [ ] Count outbox published/retry/dead.
- [ ] Count communication sent/retry/failed.
- [ ] Measure gateway duration.
- [ ] Structured logs include IDs/status/attempt, not message content.
- [ ] Mask recipient values in logs.
- [ ] Do not implement expensive DB `COUNT(*)` gauges without need.

---

## Recommended commit/PR sequence

- [ ] 1 — outbox schema/state/claim/recovery.
- [ ] 2 — Rabbit confirms/router/outbox tests.
- [ ] 3 — communication schema/domain/mock gateway.
- [ ] 4 — dispatcher/retry/idempotency tests.
- [ ] 5 — Customer/Receivable/CollectionCase/NextAction.
- [ ] 6 — overdue orchestration + E2E happy/retry path.
- [ ] 7 — template concurrency hardening.
- [ ] 8 — import/document idempotency hardening.
- [ ] 9 — tenant-isolation and reliability regression suite.

Every item above must leave CI green before the next phase is considered complete.

---

## Merge gate

Before merging implementation to `main`:

- [ ] Unit tests green.
- [ ] PostgreSQL integration tests green.
- [ ] RabbitMQ integration tests green.
- [ ] Happy-path E2E green.
- [ ] Retry-path E2E green.
- [ ] Duplicate-message tests green.
- [ ] Concurrency tests green.
- [ ] Tenant isolation tests green.
- [ ] Unknown outbox event test proves no silent `PUBLISHED` transition.
- [ ] Database transaction is not held while waiting for Rabbit/provider calls.
- [ ] Duplicate generation/import tests prove DB-backed idempotency.
- [ ] No real external channel provider credentials are required by CI.
- [ ] No new microservice/distributed-lock/rules-engine infrastructure was introduced for this slice.
