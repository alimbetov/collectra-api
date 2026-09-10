# Collectra Reliability Vertical Slice — Technical Specification

## 1. Purpose

This specification defines the next production-hardening stage for Collectra after the current import, template, localization and document-generation foundation.

The goal is to close one complete business path:

```text
Customer / Receivable
        -> Collection decision
        -> NextAction
        -> Communication
        -> Template resolution + rendering
        -> Outbox
        -> RabbitMQ
        -> Communication dispatcher
        -> Mock ChannelGateway
        -> CommunicationAttempt
        -> SENT
```

Real Email/SMS/WhatsApp/Telegram provider integrations are explicitly out of scope for this stage. The application boundary must be implemented through `ChannelGateway`; test and local environments use deterministic mock adapters.

## 2. Architectural principles

The implementation MUST preserve the following invariants:

1. RabbitMQ delivery is treated as **at-least-once**.
2. Every consumer that causes a side effect MUST be idempotent.
3. An outbox event MUST NOT become `PUBLISHED` unless broker acceptance is confirmed.
4. Unknown outbox event types MUST fail closed and MUST NOT be silently acknowledged as published.
5. Tenant-owned entities MUST never be loaded by business ID without tenant scope.
6. Business state transitions MUST be explicit and testable.
7. DB constraints MUST protect invariants that cannot safely rely on application code alone.
8. Retries MUST distinguish transient errors from permanent errors.
9. Every retryable asynchronous operation MUST have a terminal state and observable failure reason.
10. Real external channel providers MUST remain replaceable adapters behind an application port.

---

# 3. Work package A — Outbox reliability

## 3.1 Required behavior

The current outbox flow must be hardened against:

- unknown event types;
- concurrent publishers;
- process restart during publishing;
- broker/network uncertainty;
- duplicate delivery;
- transient broker failure;
- permanently poison events.

## 3.2 Outbox states

Introduce or normalize the lifecycle to:

```text
PENDING
  -> PROCESSING
      -> PUBLISHED
      -> RETRY_WAIT
      -> DEAD
```

Recommended columns if not already present:

```text
status
attempt_count
next_attempt_at
locked_at
locked_by
published_at
last_error_code
last_error_message
```

`last_error_message` MUST be bounded in size.

## 3.3 Atomic claiming

Multiple application instances MUST NOT independently claim the same event.

Preferred PostgreSQL strategy:

```sql
SELECT ...
FROM outbox_events
WHERE status IN ('PENDING', 'RETRY_WAIT')
  AND (next_attempt_at IS NULL OR next_attempt_at <= now())
ORDER BY created_at
FOR UPDATE SKIP LOCKED
LIMIT :batchSize;
```

Within the same transaction, claimed rows transition to `PROCESSING` and receive `locked_at` / `locked_by`.

Alternative atomic `UPDATE ... RETURNING` is acceptable if it provides the same guarantee.

## 3.4 Unknown event types

Routing MUST be explicit.

Conceptual contract:

```java
public interface OutboxEventHandler {
    String eventType();
    void publish(OutboxEvent event);
}
```

The registry MUST throw when no handler exists.

Forbidden behavior:

```text
unknown event -> no-op -> PUBLISHED
```

Required behavior:

```text
unknown event -> failure -> retry/dead policy
```

## 3.5 Broker confirms

RabbitMQ publisher confirms MUST be enabled.

An event can transition to `PUBLISHED` only after positive broker acknowledgement.

Negative acknowledge / timeout / returned unroutable message MUST be treated as publishing failure.

Recommended configuration:

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated
    publisher-returns: true
```

## 3.6 Retry policy

Retry policy MUST be bounded and persisted.

Suggested initial policy:

```text
attempt 1 -> immediate
attempt 2 -> +1 min
attempt 3 -> +5 min
attempt 4 -> +15 min
attempt 5 -> +1 h
attempt 6 -> DEAD
```

Exact delays MAY be configuration-driven.

No tight in-memory retry loops are allowed.

## 3.7 Stale PROCESSING recovery

Application restart can leave rows in `PROCESSING`.

A recovery job MUST periodically move stale rows back to retryable state when:

```text
status = PROCESSING
and locked_at < now() - processingTimeout
```

The recovery operation MUST be safe under multiple instances.

## 3.8 Dead events

A `DEAD` event MUST remain queryable and observable.

Required information:

- event ID;
- tenant ID where applicable;
- event type;
- aggregate ID/type;
- attempt count;
- last failure code/message;
- created/updated timestamps.

Manual replay API is NOT required in this stage, but the schema must not prevent it later.

## 3.9 Mandatory tests

- unknown event never becomes `PUBLISHED`;
- two publishers cannot claim the same row concurrently;
- broker nack keeps event retryable;
- broker timeout keeps event retryable;
- broker ack transitions exactly once to `PUBLISHED`;
- stale `PROCESSING` event is recovered;
- event becomes `DEAD` after configured maximum attempts;
- restart between claim and publish does not permanently lose event;
- duplicate publish attempt does not produce duplicate business side effect downstream.

---

# 4. Work package B — Communication + CommunicationAttempt + Mock Gateway

## 4.1 Domain model

Introduce a tenant-owned `Communication` aggregate representing one intended outbound communication.

Suggested fields:

```text
id UUID
 tenant_id UUID
 business_type VARCHAR
 business_id UUID/VARCHAR
 channel VARCHAR
 recipient VARCHAR
 locale VARCHAR
 template_id UUID
 template_version_id UUID
 subject TEXT nullable
 content TEXT / content_html TEXT
 idempotency_key VARCHAR
 status VARCHAR
 scheduled_at TIMESTAMP nullable
 queued_at TIMESTAMP nullable
 sent_at TIMESTAMP nullable
 failed_at TIMESTAMP nullable
 created_at
 updated_at
 version
```

Recommended states:

```text
PENDING
QUEUED
SENDING
RETRY_WAIT
SENT
FAILED
CANCELLED
```

Add a unique DB constraint:

```text
UNIQUE (tenant_id, idempotency_key)
```

`@Version` optimistic locking SHOULD be used on mutable aggregate state.

## 4.2 CommunicationAttempt

Every provider attempt MUST be persisted independently.

Suggested fields:

```text
id UUID
communication_id UUID
attempt_number INT
provider VARCHAR
provider_message_id VARCHAR nullable
started_at
completed_at nullable
result VARCHAR
error_code VARCHAR nullable
error_message VARCHAR nullable
```

Recommended attempt results:

```text
SUCCESS
TEMPORARY_FAILURE
PERMANENT_FAILURE
TIMEOUT
```

Constraint:

```text
UNIQUE (communication_id, attempt_number)
```

## 4.3 Application port

Create a provider-neutral port:

```java
public interface ChannelGateway {
    ChannelType channel();
    DeliveryResult send(OutboundMessage message);
}
```

`OutboundMessage` MUST contain only provider-independent information.

`DeliveryResult` MUST distinguish at minimum:

```text
SUCCESS
TEMPORARY_FAILURE
PERMANENT_FAILURE
```

Timeout/transport exceptions may be mapped to transient failure by dispatcher policy.

## 4.4 Mock gateway

Implement deterministic mock gateway(s) under a test/local profile.

The mock MUST support deterministic outcomes based on explicit test input, not random behavior.

Example conventions:

```text
success:*            -> SUCCESS
temporary-failure:*  -> TEMPORARY_FAILURE
permanent-failure:*  -> PERMANENT_FAILURE
timeout:*            -> throw timeout
```

The exact syntax is implementation-specific, but tests MUST be deterministic.

## 4.5 Dispatcher semantics

Dispatcher flow:

```text
consume message
 -> load Communication by tenant + id
 -> idempotency/state guard
 -> transition to SENDING
 -> create CommunicationAttempt
 -> invoke ChannelGateway
 -> persist result
 -> SUCCESS => Communication.SENT
 -> TEMPORARY_FAILURE => RETRY_WAIT
 -> PERMANENT_FAILURE => FAILED
```

If a duplicate RabbitMQ message arrives after `SENT`, dispatcher MUST acknowledge without sending again.

If a message is redelivered while another worker is processing the same communication, optimistic/pessimistic concurrency control MUST prevent duplicate provider call.

## 4.6 Mandatory tests

- success creates one attempt and sets `SENT`;
- transient failure creates one attempt and `RETRY_WAIT`;
- permanent failure creates one attempt and `FAILED`;
- duplicate delivery after `SENT` does not call gateway again;
- concurrent duplicate delivery causes at most one gateway invocation;
- retry creates a new attempt with incremented attempt number;
- tenant A cannot dispatch tenant B communication.

---

# 5. Work package C — Minimal Customer + Receivable + CollectionCase / NextAction

This stage intentionally implements the smallest domain required to prove the vertical slice.

## 5.1 Customer

Minimum fields:

```text
id UUID
tenant_id UUID
external_id VARCHAR nullable
name VARCHAR
status VARCHAR
preferred_locale VARCHAR nullable
email VARCHAR nullable
phone VARCHAR nullable
created_at
updated_at
version
```

Required constraint:

```text
UNIQUE (tenant_id, external_id) where external_id is not null
```

Do not model the final full customer-contact schema in this stage unless required by existing imports.

## 5.2 Receivable

Minimum fields:

```text
id UUID
tenant_id UUID
customer_id UUID
external_id VARCHAR nullable
invoice_number VARCHAR
amount NUMERIC
currency VARCHAR(3)
due_date DATE
outstanding_amount NUMERIC
status VARCHAR
created_at
updated_at
version
```

Suggested statuses:

```text
OPEN
PARTIALLY_PAID
PAID
OVERDUE
CANCELLED
```

Required invariants:

```text
amount >= 0
outstanding_amount >= 0
outstanding_amount <= amount
```

## 5.3 CollectionCase

A minimal case represents collection work for one overdue receivable.

Minimum fields:

```text
id UUID
tenant_id UUID
receivable_id UUID
status VARCHAR
opened_at
closed_at nullable
created_at
updated_at
version
```

Suggested statuses:

```text
OPEN
ON_HOLD
RESOLVED
CLOSED
```

Recommended constraint preventing duplicate active cases for the same receivable.

## 5.4 NextAction

Minimum fields:

```text
id UUID
tenant_id UUID
collection_case_id UUID
type VARCHAR
status VARCHAR
scheduled_at
executed_at nullable
idempotency_key VARCHAR
created_at
updated_at
version
```

Initial action type required:

```text
PAYMENT_OVERDUE_NOTIFICATION
```

Suggested states:

```text
PENDING
PROCESSING
COMPLETED
FAILED
CANCELLED
```

Required constraint:

```text
UNIQUE (tenant_id, idempotency_key)
```

## 5.5 Minimal collection rule

For the first slice, avoid a generic rules engine.

A receivable is eligible when:

```text
due_date < businessDate
and outstanding_amount > 0
and status not in (PAID, CANCELLED)
```

The application service creates or reuses the active `CollectionCase` and creates one idempotent `PAYMENT_OVERDUE_NOTIFICATION` NextAction.

---

# 6. Work package D — E2E vertical slice: overdue invoice -> SENT

Create one executable integration test covering real application components up to the mock gateway.

## 6.1 Happy path

Scenario:

```text
1. Create tenant.
2. Configure tenant locales; e.g. kk default, ru fallback.
3. Ensure PAYMENT_OVERDUE template/preset is available and published.
4. Import customer + invoice/receivable data.
5. Process import.
6. Persist/update Customer.
7. Persist/update Receivable with due date in the past and outstanding balance > 0.
8. Collection application detects overdue receivable.
9. CollectionCase is created/reused.
10. PAYMENT_OVERDUE_NOTIFICATION NextAction is created.
11. Communication is created idempotently.
12. Template locale/version is resolved.
13. Template is rendered.
14. Outbox event is persisted in the same transaction as Communication queueing.
15. Outbox publisher publishes and receives broker confirm.
16. Dispatcher consumes event.
17. Mock ChannelGateway returns SUCCESS.
18. CommunicationAttempt = SUCCESS.
19. Communication = SENT.
20. NextAction = COMPLETED.
```

## 6.2 Failure path

A second E2E scenario MUST force temporary provider failure:

```text
PENDING
 -> QUEUED
 -> SENDING
 -> RETRY_WAIT
 -> SENDING
 -> SENT
```

Assertions MUST verify attempt history and no duplicate communication.

## 6.3 Test infrastructure

Preferred integration stack:

- PostgreSQL via Testcontainers;
- RabbitMQ via Testcontainers;
- real Spring transaction boundaries;
- mock `ChannelGateway` Spring bean;
- no external network/provider dependencies.

H2 MUST NOT be used for concurrency/locking/outbox correctness tests.

---

# 7. Work package E — Template concurrency hardening

## 7.1 Version allocation

Current `latest + 1` style allocation is unsafe under concurrent requests.

Required guarantee:

```text
(template_id, template_version) is unique
```

Version creation MUST serialize on a stable parent row or equivalent lock.

Preferred simple implementation:

```text
lock DocumentTemplate row
 -> read max version
 -> create next version
 -> commit
```

DB unique constraint remains mandatory as a final invariant.

## 7.2 Optimistic locking

Add `@Version` to mutable template/version entities where concurrent editor updates can overwrite each other.

A stale update MUST surface as a conflict (HTTP 409 preferred), not silently overwrite newer content.

## 7.3 Published invariant

For a logical template locale/channel, only one current version may be `PUBLISHED`.

Protect with DB partial unique index where schema permits, for example:

```sql
CREATE UNIQUE INDEX ...
ON template_versions(template_id, locale, channel)
WHERE status = 'PUBLISHED';
```

Publish operation MUST execute archive-old + publish-new atomically.

## 7.4 Mandatory tests

- two concurrent version creations result in two distinct sequential versions or one defined conflict/retry path, never corruption;
- stale draft update returns conflict;
- concurrent publish cannot result in two published versions for the same template/locale/channel;
- publish rollback leaves previous published version valid.

---

# 8. Work package F — Import and document-generation idempotency verification

## 8.1 Import

Define the idempotency contract explicitly.

At minimum, duplicate submission with the same tenant + idempotency key MUST return/reuse the same logical import batch.

Concurrent duplicate requests MUST not create two independent batches.

Verify database constraint exists and is part of correctness, not only service pre-check.

Required scenarios:

- sequential duplicate request;
- concurrent duplicate request;
- restart after batch creation;
- restart during processing;
- failed batch preserves structured error;
- retry does not silently duplicate domain rows.

## 8.2 Document generation

Every generation request MUST have a deterministic idempotency key.

Acceptable inputs include:

```text
tenantId + source/business ID + templateVersionId + document variant
```

or a stable upstream request/event ID if business semantics guarantee uniqueness.

Required DB uniqueness MUST prevent concurrent duplicate generation jobs.

Duplicate Rabbit deliveries MUST reuse already-created/completed generation job and MUST NOT create duplicate object-storage files.

Required tests:

- duplicate request before processing;
- duplicate request while processing;
- duplicate message after success;
- worker restart after object upload but before final status update;
- retry reconciles existing object rather than blindly producing another file where possible.

---

# 9. Work package G — Tenant isolation architectural rule

## 9.1 Rule

For every tenant-owned aggregate, application/business code MUST load resources using tenant-scoped queries.

Forbidden:

```java
repository.findById(id)
```

Required form:

```java
repository.findByIdAndTenantId(id, tenantId)
```

or an equivalent explicit tenant specification.

Initial scope:

```text
Customer
Receivable
CollectionCase
NextAction
Communication
CommunicationAttempt through parent
DocumentTemplate
TemplateVersion through tenant-owned parent/query
ImportBatch
GenerationJob
File metadata/assets where tenant-owned
```

## 9.2 ArchUnit/static architecture tests

Add architecture tests that prevent obvious unsafe repository methods from being invoked from application/API layers for tenant-owned aggregates.

The test need not prove row-level security formally, but it MUST detect regression patterns such as direct unscoped `findById` usage.

## 9.3 API behavior

Cross-tenant resource access SHOULD resolve to `404 Not Found` rather than reveal that another tenant owns the identifier.

Mandatory integration tests:

```text
tenant A token + tenant B resource ID -> 404
```

for critical aggregates.

---

# 10. Work package H — Concurrency, restart and duplicate-message tests

Create a dedicated reliability test suite.

## 10.1 Concurrency tests

Must cover:

- outbox concurrent claim;
- communication concurrent dispatch;
- template concurrent version creation;
- template concurrent publish;
- import concurrent idempotency;
- document generation concurrent idempotency;
- NextAction concurrent creation.

Use barriers/latches to force actual overlap where needed. Tests that merely start two futures without synchronization are insufficient.

## 10.2 Restart/recovery tests

Must cover persisted intermediate state:

- stale Outbox `PROCESSING`;
- Communication left `SENDING`;
- import left processing;
- generation job left processing;
- retry scheduler safely resumes work.

A restart simulation may recreate Spring context or directly verify recovery service behavior against persisted state.

## 10.3 Duplicate-message tests

For every asynchronous consumer that changes state or calls an external port:

```text
same event ID delivered twice
```

must result in one logical side effect.

Tests MUST assert both final DB state and gateway invocation count.

---

# 11. External channel adapters — explicitly deferred

Only after work packages A-H pass CI should real adapters be added:

```text
EmailChannelGateway
SmsChannelGateway
WhatsAppChannelGateway
TelegramChannelGateway
```

Each adapter must implement the same `ChannelGateway` contract.

Provider-specific response codes must be translated into Collectra-neutral outcomes:

```text
SUCCESS
TEMPORARY_FAILURE
PERMANENT_FAILURE
```

Provider-specific DTOs MUST NOT leak into domain/application layers.

---

# 12. Suggested package structure

```text
io.collectra.api
  customer/
    api/
    application/
    domain/
    infrastructure/

  receivable/
    api/
    application/
    domain/
    infrastructure/

  collection/
    application/
    domain/
    infrastructure/

  communication/
    api/
    application/
    domain/
    infrastructure/
    gateway/

  outbox/
    application/
    domain/
    infrastructure/
```

Avoid introducing microservices at this stage. Keep module boundaries inside the current Spring Boot application and retain transaction simplicity for the first reliable vertical slice.

---

# 13. Database migration expectations

New schema changes MUST be introduced through the existing migration mechanism and include:

- `communications`;
- `communication_attempts`;
- minimal `customers`;
- minimal `receivables`;
- `collection_cases`;
- `next_actions`;
- outbox reliability columns/indexes where missing;
- idempotency unique constraints;
- template publication/version constraints;
- indexes for polling queries and tenant-scoped lookups.

Every polling index MUST be designed from the actual query shape.

Example outbox polling index:

```text
(status, next_attempt_at, created_at)
```

Avoid adding indexes without a corresponding query/use case.

---

# 14. API/error semantics

Normalize expected concurrency and state errors:

```text
400  invalid input
401  unauthenticated
403  insufficient authority
404  resource absent in current tenant scope
409  optimistic-lock/state/idempotency conflict when conflict cannot be transparently reused
422  valid request that violates domain transition where applicable
500  unexpected failure
503  transient infrastructure unavailable where surfaced synchronously
```

Internal exception messages, SQL constraint names and stack traces MUST NOT be exposed as public API error text.

---

# 15. Observability requirements

At minimum expose structured logs/metrics for:

```text
outbox.pending
outbox.retry_wait
outbox.dead
outbox.publish.success
outbox.publish.failure
communication.sent
communication.retry
communication.failed
communication.gateway.duration
import.failed
job.recovered
```

Structured log correlation SHOULD include where available:

```text
tenantId
eventId
communicationId
businessId
attempt
```

Do not log message body, secrets, tokens or full sensitive recipient data.

---

# 16. CI acceptance gate

The branch implementing this specification MUST NOT be considered complete until CI proves:

1. unit tests are green;
2. PostgreSQL integration tests are green;
3. RabbitMQ integration tests are green;
4. E2E overdue -> SENT happy path is green;
5. transient failure -> retry -> SENT path is green;
6. duplicate-message tests are green;
7. concurrency tests are green;
8. tenant isolation tests are green;
9. unknown outbox events cannot be published silently;
10. no real channel provider is required to run CI.

---

# 17. Recommended implementation order

Implement in this order to minimize rework:

```text
Phase 1
Outbox state model + atomic claim + confirms + retry/dead + recovery

Phase 2
Communication + CommunicationAttempt + ChannelGateway + deterministic mocks

Phase 3
Customer + Receivable + CollectionCase + NextAction

Phase 4
overdue invoice -> Communication -> Outbox -> Rabbit -> mock gateway -> SENT

Phase 5
Template concurrency hardening

Phase 6
Import/document idempotency verification and hardening

Phase 7
Tenant isolation architecture tests

Phase 8
Concurrency/restart/duplicate-message reliability suite

Phase 9
Only then: real Email/SMS/WhatsApp/Telegram adapters
```

Each phase SHOULD be implemented as a compact reviewable PR or commit set and MUST leave CI green.

---

# 18. Definition of Done

This stage is complete when the following statement is true:

> Given an overdue receivable for a tenant, Collectra can deterministically create one collection action, resolve and render the appropriate tenant template, persist one communication, safely publish it through the transactional outbox, tolerate duplicate/restarted asynchronous processing, dispatch exactly one logical send through a mock channel gateway, persist every delivery attempt, and reach `SENT` without depending on any real external channel provider.

Anything beyond this statement, including provider onboarding, campaign tooling, advanced collection rules, reporting UI and provider-specific delivery callbacks, belongs to subsequent stages.