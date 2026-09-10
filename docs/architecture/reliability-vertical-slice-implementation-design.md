# Collectra Reliability Vertical Slice — Detailed Implementation Design

This document refines `reliability-vertical-slice-spec.md` into an implementation-level plan aligned with the current `collectra-api` codebase.

The goal is **higher implementation precision without increasing architecture complexity**. The design deliberately keeps Collectra as one Spring Boot application, PostgreSQL as the source of truth, RabbitMQ as the asynchronous transport, and provider integrations behind a small `ChannelGateway` port.

---

# 1. Scope and design constraints

## 1.1 Target outcome

The implementation is complete when this path works reliably:

```text
Import/customer data
    -> Receivable
    -> overdue evaluation
    -> CollectionCase
    -> NextAction(PAYMENT_OVERDUE_NOTIFICATION)
    -> Communication
    -> published TemplateVersion resolution
    -> render
    -> OutboxEvent
    -> RabbitMQ
    -> CommunicationDispatcher
    -> Mock ChannelGateway
    -> CommunicationAttempt
    -> Communication.SENT
    -> NextAction.COMPLETED
```

## 1.2 Keep the implementation simple

The following are explicit non-goals for this stage:

- no new microservices;
- no Kafka;
- no generic workflow engine;
- no generic rules engine;
- no Saga framework;
- no event-sourcing;
- no distributed locks such as Redis/Redisson;
- no separate retry service;
- no generic provider SDK abstraction hierarchy;
- no manual dead-event UI;
- no real Email/SMS/WhatsApp/Telegram provider integration;
- no database row-level security migration in this stage;
- no custom exactly-once protocol.

Reliability is achieved with:

```text
PostgreSQL constraints
+ short transactions
+ explicit states
+ SKIP LOCKED
+ deterministic idempotency keys
+ RabbitMQ confirms
+ idempotent consumers
+ recovery jobs
```

---

# 2. Current-state delta

The current project already contains useful foundations. The implementation MUST extend them instead of creating parallel mechanisms.

## 2.1 Existing outbox

Current classes:

```text
io.collectra.api.shared.outbox.OutboxEvent
io.collectra.api.shared.outbox.OutboxService
io.collectra.api.shared.outbox.OutboxRepository
io.collectra.api.shared.outbox.OutboxPublisher
```

Current deficiencies to fix:

1. `status` is an untyped `String`.
2. Unknown event types are currently treated as successfully published.
3. Current repository uses pessimistic locking but not `SKIP LOCKED`.
4. Current publisher holds the transaction while performing RabbitMQ I/O.
5. There is no persisted `PROCESSING` ownership metadata.
6. There is no `PUBLISHED` timestamp or persisted failure details.
7. There is no terminal `DEAD` state.
8. `convertAndSend()` return is currently treated as success without broker confirmation.

Do not create a second outbox table or a second publisher subsystem.

## 2.2 Existing import reliability

Current `ImportBatchReservationService` already:

- reserves by `(tenantId, idempotencyKey)`;
- compares a request hash;
- uses `REQUIRES_NEW`;
- exposes tenant-scoped `findByIdAndTenantId` access;
- persists structured failure code/message.

Therefore Phase 6 for import is **verification + targeted hardening**, not redesign.

## 2.3 Existing document generation

Current flow already:

```text
GenerationJobService.createMapped()
    -> create GenerationJob
    -> append DOCUMENT_GENERATION_REQUESTED outbox event
```

in one transaction.

Current gap: `GenerationJob` has no deterministic idempotency key, so duplicate requests may create duplicate jobs before Rabbit processing even begins.

The current `DocumentGenerationListener` already has Rabbit retry/dead routing. Do not replace it merely to make it look similar to Communication. Harden its idempotency/recovery behavior only.

## 2.4 Existing template foundation

Current modules already provide:

```text
TenantLocale
TemplateLocaleResolver
TemplatePreset catalog
TemplateRenderer
TemplateVersion lifecycle
```

Communication MUST reuse these classes rather than duplicating locale fallback or placeholder rendering.

---

# 3. Cross-cutting implementation rules

## 3.1 Time

Inject `java.time.Clock` into new services that make business-time or retry-time decisions.

Use:

```java
Instant.now(clock)
LocalDate.now(clock)
```

instead of direct `Instant.now()` / `LocalDate.now()` in new reliability logic.

Reason: retry, stale-lock and overdue tests must be deterministic without sleeps.

Do not retrofit every existing class immediately. Apply `Clock` to the new/modified reliability path only.

## 3.2 Error text bounds

Persisted technical messages must be bounded:

```text
error_code        <= 80 chars
error_message     <= 1000 chars
```

Before persistence:

```java
truncate(rootMessage, 1000)
```

Never persist full stack traces in business tables.

## 3.3 Tenant-scoped access

For tenant-owned aggregates, application services use:

```java
findByIdAndTenantId(id, tenantId)
```

or an equivalent query that contains tenant scope.

Cross-tenant resource access returns the same `404` behavior as an absent resource.

## 3.4 Database constraints are part of correctness

A pre-check such as:

```java
if (!repository.exists(...)) repository.save(...);
```

is never sufficient for idempotency under concurrency.

The DB unique constraint is the final invariant. Application code may pre-check for normal flow, but must correctly handle a concurrent `DataIntegrityViolationException` by reloading the winning row when the operation is idempotent.

## 3.5 No network call while holding a row lock

RabbitMQ/provider/object-storage calls MUST NOT happen while a long database transaction owns row locks unless there is no simpler alternative.

The normal pattern is:

```text
short transaction: claim state
commit

external operation

short transaction: persist result
commit
```

---

# 4. Phase 1 — Outbox reliability detailed design

## 4.1 Database migration

Use the next migration after `021-tenant-locales.sql`, suggested name:

```text
022-outbox-reliability.sql
```

Alter the existing `outbox_events` table; do not recreate it.

Required columns:

```sql
status             VARCHAR(20)  NOT NULL
attempt_count      INT          NOT NULL DEFAULT 0
next_attempt_at    TIMESTAMPTZ  NOT NULL
locked_at          TIMESTAMPTZ  NULL
locked_by          VARCHAR(120) NULL
published_at       TIMESTAMPTZ  NULL
last_error_code    VARCHAR(80)  NULL
last_error_message VARCHAR(1000) NULL
```

Optional but useful CHECK constraints:

```sql
CHECK (attempt_count >= 0)
```

Do not add an `updated_at` column only for consistency if it is not needed by queries.

Replace/augment the existing polling index with one matching the query:

```sql
CREATE INDEX idx_outbox_ready
ON outbox_events(status, next_attempt_at, created_at)
WHERE status IN ('PENDING', 'RETRY_WAIT');
```

Add recovery index only if the recovery query is used frequently:

```sql
CREATE INDEX idx_outbox_processing_locked
ON outbox_events(locked_at)
WHERE status = 'PROCESSING';
```

## 4.2 Typed state

Introduce:

```java
public enum OutboxEventStatus {
    PENDING,
    PROCESSING,
    RETRY_WAIT,
    PUBLISHED,
    DEAD
}
```

Map with `@Enumerated(EnumType.STRING)`.

Required domain methods on `OutboxEvent`:

```java
void claim(String workerId, Instant now)
void markPublished(Instant now)
void scheduleRetry(Instant nextAttemptAt, String code, String message)
void markDead(String code, String message)
void recover(Instant nextAttemptAt, String code, String message)
```

State mutations should reject invalid transitions.

Minimum allowed transitions:

| From | To | Cause |
|---|---|---|
| PENDING | PROCESSING | publisher claim |
| RETRY_WAIT | PROCESSING | publisher claim |
| PROCESSING | PUBLISHED | broker ACK |
| PROCESSING | RETRY_WAIT | transient publishing failure |
| PROCESSING | DEAD | permanent/max-attempt failure |
| PROCESSING | RETRY_WAIT | stale recovery |

Do not allow `PUBLISHED -> ...` automatically.

## 4.3 Claiming algorithm

Do not keep the current transaction open while publishing.

Implement a small application service:

```text
OutboxClaimService
```

Responsibilities only:

```text
claimBatch(workerId, now, batchSize)
recoverStale(now, timeout)
```

Preferred repository implementation: native PostgreSQL query using `FOR UPDATE SKIP LOCKED`.

Conceptual algorithm inside one short transaction:

```text
BEGIN
SELECT ready rows
FOR UPDATE SKIP LOCKED
LIMIT 50

for each row:
    status = PROCESSING
    locked_at = now
    locked_by = workerId
COMMIT

return claimed IDs/data
```

Important: claim transaction ends before RabbitMQ call.

A simple implementation may return `List<UUID>` and reload each event before publishing. No custom queue abstraction is needed.

## 4.4 Router, not a framework

At the current number of event types, avoid a generic handler registry.

Use one explicit class:

```java
@Component
public class OutboxEventRouter {
    public OutboxRoute route(String eventType) {
        return switch (eventType) {
            case "DOCUMENT_GENERATION_REQUESTED" ->
                    new OutboxRoute(DocumentMessagingConfig.EXCHANGE,
                                    DocumentMessagingConfig.ROUTING_KEY);
            case "COMMUNICATION_DISPATCH_REQUESTED" ->
                    new OutboxRoute(CommunicationMessagingConfig.EXCHANGE,
                                    CommunicationMessagingConfig.ROUTING_KEY);
            default -> throw new UnknownOutboxEventTypeException(eventType);
        };
    }
}
```

This is intentionally simpler than one Spring bean per event type.

Unknown event is a **permanent routing error** for the running application version. Mark it `DEAD` immediately with:

```text
last_error_code = UNKNOWN_EVENT_TYPE
```

Do not retry it six times.

A later deployment/manual replay can requeue it if support is introduced.

## 4.5 Rabbit message metadata

Do not redesign every existing event payload into a new envelope in this stage.

Keep the existing JSON payload format for each event, but attach stable metadata to AMQP message properties:

```text
messageId        = outboxEvent.id
x-event-id       = outboxEvent.id
x-event-type     = outboxEvent.eventType
x-tenant-id      = outboxEvent.tenantId (when non-null)
x-aggregate-id   = outboxEvent.aggregateId
x-aggregate-type = outboxEvent.aggregateType
```

This gives consumers a stable event identity without breaking existing payload contracts.

## 4.6 Broker confirm implementation

Required configuration:

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated
    publisher-returns: true
```

For each claimed event:

```text
1. resolve route;
2. send with CorrelationData(outboxEvent.id);
3. wait for confirm for bounded timeout;
4. ACK => mark PUBLISHED in short transaction;
5. NACK/timeout/return => schedule retry in short transaction.
```

Suggested initial confirm timeout:

```text
5 seconds
```

configurable as:

```text
collectra.messaging.publisher-confirm-timeout-ms
```

Do not keep a JDBC transaction open while waiting for the confirm future.

### Important guarantee

A confirm timeout is an **uncertain delivery**, not proof that RabbitMQ did not receive the message.

Therefore the event is retried and downstream consumers MUST remain idempotent.

This is the intended at-least-once behavior.

## 4.7 Retry policy

Create one small helper, not a retry framework:

```java
@Component
class OutboxRetryPolicy {
    Duration delayForAttempt(int attempt);
    int maxAttempts();
}
```

Suggested configuration/defaults:

```text
maxAttempts = 6
1 -> 0
2 -> 1m
3 -> 5m
4 -> 15m
5 -> 1h
6 -> DEAD
```

Increment `attempt_count` when an actual publish attempt starts, not when an event is merely selected.

Failure categories:

| Failure | Result |
|---|---|
| unknown event type | DEAD immediately |
| invalid event payload before send | DEAD immediately |
| Rabbit NACK | RETRY_WAIT |
| confirm timeout | RETRY_WAIT |
| connection failure | RETRY_WAIT |
| returned unroutable message | RETRY_WAIT initially; DEAD after max attempts |

## 4.8 Stale recovery

Run a scheduled recovery query, for example every minute.

Initial processing timeout:

```text
2 minutes
```

A stale row:

```text
status = PROCESSING
locked_at < now - processingTimeout
```

is changed to:

```text
RETRY_WAIT
next_attempt_at = now
locked_at = null
locked_by = null
last_error_code = PROCESSING_TIMEOUT_RECOVERED
```

Do not increment `attempt_count` during recovery; the previous publish attempt already accounted for it.

## 4.9 OutboxPublisher final responsibility

After refactoring, `OutboxPublisher` should remain a thin coordinator:

```text
@Scheduled
publishPending()
    -> claimService.claimBatch(...)
    -> for each eventId
         publishOne(eventId)
```

Do not place all persistence, routing, retry calculation and confirmation logic in the scheduled method.

Target number of new production classes for Phase 1: approximately 4–6, not dozens.

## 4.10 Phase 1 acceptance criteria

Phase 1 is accepted when:

- two publisher instances never hold the same event claim at the same time;
- database locks are not held during Rabbit publish/confirm waiting;
- unknown events become `DEAD`, never `PUBLISHED`;
- ACK produces `PUBLISHED + published_at`;
- NACK/timeout produces persisted `RETRY_WAIT`;
- max attempts produce `DEAD`;
- process death after claim is recoverable;
- duplicate broker delivery remains safe downstream.

---

# 5. Phase 2 — Communication detailed design

Suggested migration:

```text
023-communication-core.sql
```

## 5.1 Communication aggregate

One `Communication` represents one logical outbound message to one recipient through one channel.

Use one row per recipient/channel. Do not model recipient arrays inside one communication.

Required fields:

```text
id                  UUID PK
tenant_id           UUID NOT NULL FK tenants
business_type       VARCHAR(50) NOT NULL
business_id         VARCHAR(120) NOT NULL
next_action_id      UUID NULL
channel             VARCHAR(20) NOT NULL
recipient           VARCHAR(320) NOT NULL
locale              VARCHAR(35) NOT NULL
template_id         UUID NOT NULL
template_version_id UUID NOT NULL
subject             VARCHAR(500) NULL
content             TEXT NOT NULL
idempotency_key     VARCHAR(200) NOT NULL
status              VARCHAR(20) NOT NULL
scheduled_at        TIMESTAMPTZ NULL
queued_at           TIMESTAMPTZ NULL
sent_at             TIMESTAMPTZ NULL
failed_at           TIMESTAMPTZ NULL
last_error_code     VARCHAR(80) NULL
last_error_message  VARCHAR(1000) NULL
created_at          TIMESTAMPTZ NOT NULL
updated_at          TIMESTAMPTZ NOT NULL
version             BIGINT NOT NULL DEFAULT 0
```

Constraint:

```sql
UNIQUE (tenant_id, idempotency_key)
```

Indexes:

```text
(tenant_id, id)
(tenant_id, status, scheduled_at)   only if scheduler uses it
(next_action_id)                    if completion lookup uses it
```

Do not duplicate `content_html` and `text_content`. Existing template API already uses a unified content concept; Communication stores the final immutable rendered `content` snapshot.

## 5.2 Why store rendered content

When communication is queued, persist:

```text
recipient
locale
template_version_id
subject
rendered content
```

This makes retries deterministic even if a new template version is published later.

Retries MUST NOT rerender from the newest template.

## 5.3 Communication statuses

Use:

```text
PENDING
QUEUED
SENDING
RETRY_WAIT
SENT
FAILED
CANCELLED
```

Allowed transitions:

| From | To | Trigger |
|---|---|---|
| PENDING | QUEUED | outbox event stored |
| QUEUED | SENDING | dispatcher claims send |
| RETRY_WAIT | SENDING | retry delivery |
| SENDING | SENT | gateway success |
| SENDING | RETRY_WAIT | transient gateway failure |
| SENDING | FAILED | permanent/max-attempt failure |
| PENDING/QUEUED/RETRY_WAIT | CANCELLED | future/manual cancellation if implemented |

For this stage no cancellation API is required.

## 5.4 CommunicationAttempt

Required fields:

```text
id                  UUID PK
communication_id    UUID NOT NULL FK communications
attempt_number      INT NOT NULL
provider             VARCHAR(50) NOT NULL
provider_message_id VARCHAR(200) NULL
started_at           TIMESTAMPTZ NOT NULL
completed_at         TIMESTAMPTZ NULL
result               VARCHAR(30) NOT NULL
error_code           VARCHAR(80) NULL
error_message        VARCHAR(1000) NULL
```

Constraint:

```sql
UNIQUE (communication_id, attempt_number)
```

Attempt number is derived while holding the communication claim/lock. Do not use `MAX(attempt)+1` without serializing on the parent communication row.

## 5.5 Application contracts

Keep the port small:

```java
public interface ChannelGateway {
    ChannelType channel();
    DeliveryResult send(OutboundMessage message);
}
```

Suggested records:

```java
public record OutboundMessage(
        UUID communicationId,
        String recipient,
        String subject,
        String content,
        String locale) {}

public record DeliveryResult(
        DeliveryStatus status,
        String providerMessageId,
        String errorCode,
        String errorMessage) {}
```

Do not expose tenant/domain repositories to gateway adapters.

## 5.6 Gateway selection

Use a very small registry built from Spring beans:

```java
Map<ChannelType, ChannelGateway>
```

Fail application startup on duplicate gateway for the same channel.

No factory hierarchy is needed.

## 5.7 Deterministic mock gateway

Use one `MockChannelGateway` per required channel or one implementation configurable with channel.

For tests, behavior may be controlled by recipient prefix:

```text
success:alice@example.test
transient:alice@example.test
permanent:alice@example.test
timeout:alice@example.test
```

For a retry-then-success test, allow a deterministic sequence stored in test bean state:

```text
communicationId -> [TEMPORARY_FAILURE, SUCCESS]
```

No random failures.

## 5.8 Communication creation transaction

Create communication only after template resolution/render succeeds.

Single transaction:

```text
load tenant/customer/receivable/action
resolve published template
render immutable subject/content
INSERT communication
INSERT outbox COMMUNICATION_DISPATCH_REQUESTED
communication -> QUEUED
commit
```

If the transaction rolls back, neither Communication nor Outbox should remain queued independently.

Suggested event payload:

```json
{
  "communicationId": "uuid"
}
```

The stable outbox event ID goes in AMQP headers/messageId as defined in Phase 1.

## 5.9 Communication idempotency key

For the first overdue action use a deterministic key:

```text
nextActionId + ":" + channel + ":" + normalizedRecipient
```

This allows one logical send per action/channel/recipient.

Do not base the key on random UUID or timestamp.

For generic future callers, allow a supplied business idempotency key but always tenant-scope the unique constraint.

## 5.10 Dispatcher concurrency

Provider call must happen at most once concurrently for one Communication.

Simplest design:

```text
short tx:
  SELECT communication FOR UPDATE
  if SENT/FAILED/CANCELLED -> no-op/ack
  if QUEUED/RETRY_WAIT -> set SENDING
  create attempt
commit

call ChannelGateway outside tx

short tx:
  SELECT communication FOR UPDATE
  update attempt result
  update communication state
commit
```

Problem to handle: process may die after setting `SENDING` but before persisting gateway result.

Add `sending_started_at` OR reuse latest attempt `started_at` for recovery. Prefer reusing the attempt to avoid another column if query remains simple.

A recovery scheduler may move communications stuck in `SENDING` longer than configured timeout to `RETRY_WAIT` and close the attempt as `TIMEOUT`/`UNKNOWN`.

This may lead to a duplicate external send after an uncertain crash. That is unavoidable without provider idempotency. Therefore `communicationId` MUST be available to future adapters as provider idempotency/client-reference where supported.

For the mock gateway, duplicate logical calls can be detected and asserted.

## 5.11 Communication retry

Do not create a second retry database scheduler if RabbitMQ delayed/retry queues already provide retry timing.

Use current RabbitMQ retry pattern for dispatch messages:

```text
main queue
 -> transient failure
 -> retry exchange/TTL queue
 -> main queue
```

Persist Communication status as `RETRY_WAIT` between deliveries.

Persist retry count in `CommunicationAttempt`; Rabbit header count is transport metadata, not the source of truth.

## 5.12 Phase 2 acceptance criteria

- one logical Communication per idempotency key;
- duplicate creation returns/reuses existing row;
- content snapshot does not change on retry;
- one attempt row per provider invocation;
- duplicate message after `SENT` causes zero new gateway calls;
- concurrent duplicate message causes at most one active gateway call;
- transient failure becomes `RETRY_WAIT`;
- permanent/max attempts becomes `FAILED`;
- every failure has bounded code/message.

---

# 6. Phase 3 — Minimal business domain detailed design

Suggested migration:

```text
024-collection-core.sql
```

## 6.1 Customer

Keep the first model deliberately small:

```text
id               UUID PK
tenant_id        UUID NOT NULL
external_id      VARCHAR(120) NULL
name             VARCHAR(250) NOT NULL
status           VARCHAR(20) NOT NULL
preferred_locale VARCHAR(35) NULL
email            VARCHAR(320) NULL
phone            VARCHAR(40) NULL
created_at
updated_at
version
```

Statuses:

```text
ACTIVE
INACTIVE
```

Constraints:

```sql
CREATE UNIQUE INDEX uq_customer_tenant_external
ON customers(tenant_id, external_id)
WHERE external_id IS NOT NULL;
```

No contact child table yet. It can be introduced later when multiple emails/phones become a real requirement for the vertical slice.

## 6.2 Receivable

Fields:

```text
id                 UUID PK
tenant_id          UUID NOT NULL
customer_id        UUID NOT NULL
external_id        VARCHAR(120) NULL
invoice_number     VARCHAR(120) NOT NULL
amount             NUMERIC(19,2) NOT NULL
outstanding_amount NUMERIC(19,2) NOT NULL
currency           VARCHAR(3) NOT NULL
due_date           DATE NOT NULL
status             VARCHAR(20) NOT NULL
created_at
updated_at
version
```

Use `BigDecimal` in Java.

Constraints:

```sql
CHECK (amount >= 0)
CHECK (outstanding_amount >= 0)
CHECK (outstanding_amount <= amount)
```

Use one unique business identifier:

```sql
UNIQUE (tenant_id, external_id) WHERE external_id IS NOT NULL
```

If external ID is absent, do not guess uniqueness from invoice number in this stage.

Statuses:

```text
OPEN
PARTIALLY_PAID
PAID
CANCELLED
```

Do **not** persist `OVERDUE` as a source-of-truth status in this stage. Overdue is derived from:

```text
dueDate < businessDate
AND outstandingAmount > 0
AND status not PAID/CANCELLED
```

This avoids state drift where the date passes but status remains `OPEN`.

## 6.3 CollectionCase

Fields:

```text
id            UUID PK
tenant_id     UUID NOT NULL
receivable_id UUID NOT NULL
status        VARCHAR(20) NOT NULL
opened_at     TIMESTAMPTZ NOT NULL
closed_at     TIMESTAMPTZ NULL
created_at
updated_at
version
```

For the first vertical slice use statuses:

```text
OPEN
RESOLVED
```

Do not add `ON_HOLD`/`CLOSED` until a business flow requires them.

Prevent duplicate active case:

```sql
CREATE UNIQUE INDEX uq_collection_case_open
ON collection_cases(tenant_id, receivable_id)
WHERE status = 'OPEN';
```

## 6.4 NextAction

Fields:

```text
id                 UUID PK
tenant_id          UUID NOT NULL
collection_case_id UUID NOT NULL
type               VARCHAR(50) NOT NULL
status             VARCHAR(20) NOT NULL
scheduled_at       TIMESTAMPTZ NOT NULL
executed_at        TIMESTAMPTZ NULL
idempotency_key    VARCHAR(200) NOT NULL
communication_id   UUID NULL
created_at
updated_at
version
```

Initial type:

```text
PAYMENT_OVERDUE_NOTIFICATION
```

Statuses:

```text
PENDING
PROCESSING
COMPLETED
FAILED
```

Constraint:

```sql
UNIQUE (tenant_id, idempotency_key)
```

Suggested first action key:

```text
"PAYMENT_OVERDUE:" + receivableId
```

If repeated reminders become required later, include a deterministic reminder stage/date. Do not design that now.

## 6.5 Overdue evaluator service

One application service is enough:

```text
OverdueCollectionService
```

Input:

```java
process(UUID tenantId, UUID receivableId, LocalDate businessDate)
```

Behavior:

```text
load Receivable with tenant scope
if not overdue -> return NO_ACTION
find/create OPEN CollectionCase
find/create NextAction using deterministic key
return action
```

Use DB constraints to close concurrent create races by reloading the winning row.

Do not create a rule DSL or strategy registry in this stage.

## 6.6 Recipient/locale/channel decision for first slice

To avoid building customer preference engines now:

```text
channel = EMAIL
recipient = customer.email
requestedLocale = customer.preferredLocale if present
                  else tenant default locale
preset/template code = PAYMENT_OVERDUE
```

If email is absent:

```text
NextAction -> FAILED
error = CUSTOMER_EMAIL_MISSING
```

Do not silently switch to SMS in this stage; channel fallback is a later policy feature.

---

# 7. Phase 4 — Vertical slice orchestration

## 7.1 One orchestration service

Introduce one service such as:

```text
PaymentOverdueNotificationService
```

Do not build a generic action executor framework yet.

Responsibilities:

```text
1. receive NextAction ID;
2. load action/case/receivable/customer with tenant scope;
3. choose EMAIL + recipient + requested locale;
4. resolve published PAYMENT_OVERDUE TemplateVersion;
5. build canonical render model;
6. render subject/content;
7. create Communication idempotently;
8. append COMMUNICATION_DISPATCH_REQUESTED outbox event;
9. link Communication to NextAction;
10. leave action PROCESSING until communication becomes SENT/FAILED.
```

## 7.2 Canonical render model

Do not send JPA entities directly to `TemplateRenderer`.

Build a `Map<String,Object>`/DTO matching existing placeholder contracts, for example:

```json
{
  "customer": {
    "name": "Aruzhan S.",
    "email": "success:aruzhan@example.test"
  },
  "invoice": {
    "number": "INV-1001",
    "amount": "250000.00",
    "outstandingAmount": "250000.00",
    "currency": "KZT",
    "dueDate": "2026-09-01"
  }
}
```

The exact keys MUST match the existing Field Catalog/preset placeholders. Add a contract test proving the required preset placeholders are available.

## 7.3 NextAction completion

Communication owns delivery status; NextAction mirrors terminal business outcome.

Rules:

```text
Communication SENT   -> NextAction COMPLETED
Communication FAILED -> NextAction FAILED
Communication RETRY_WAIT -> NextAction remains PROCESSING
```

Perform the Communication terminal update and NextAction terminal update in the same local DB transaction after gateway result is known.

## 7.4 E2E fixture

Create one deterministic fixture:

```text
tenant locale default: kk
customer preferred locale: kk
email: success:customer@example.test
invoice: INV-E2E-001
amount: 250000.00 KZT
dueDate: businessDate - 3 days
outstanding: 250000.00
```

Do not depend on wall-clock date; use injected Clock/businessDate.

## 7.5 E2E assertions

Happy path MUST assert database facts, not only HTTP 2xx:

```text
1 Customer
1 Receivable
1 OPEN CollectionCase
1 PAYMENT_OVERDUE_NOTIFICATION NextAction
1 Communication
1 Outbox event for communication
1 SUCCESS CommunicationAttempt
Communication = SENT
NextAction = COMPLETED
rendered content contains invoice number
resolved TemplateVersion is PUBLISHED and belongs to tenant
```

Transient path:

```text
attempt #1 TEMPORARY_FAILURE
Communication RETRY_WAIT
NextAction PROCESSING
attempt #2 SUCCESS
Communication SENT
NextAction COMPLETED
```

---

# 8. Phase 5 — Template concurrency detailed design

Suggested migration:

```text
025-template-concurrency.sql
```

## 8.1 Version allocation

Use the existing `DocumentTemplate` row as the stable lock.

Repository method:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select t from DocumentTemplate t where t.id = :id and t.tenantId = :tenantId")
Optional<DocumentTemplate> findForUpdate(UUID id, UUID tenantId);
```

Inside `TemplateManagementService.createVersion()`:

```text
lock parent template
read current max version
insert max+1
commit
```

Keep DB unique `(template_id, template_version)` as the final invariant.

Do not create a sequence table per template.

## 8.2 Optimistic edit locking

Add/use `@Version` on `TemplateVersion`.

Expose version to the frontend DTO as `rowVersion` or `revision`.

Update request sends it back.

If stale:

```text
HTTP 409
code = TEMPLATE_VERSION_CONFLICT
```

Do not silently use last-write-wins for draft editing.

## 8.3 Publish invariant

DB:

```sql
CREATE UNIQUE INDEX uq_template_published_locale_channel
ON template_versions(template_id, locale, channel)
WHERE status = 'PUBLISHED';
```

Publish transaction:

```text
lock DocumentTemplate
load currently published same locale/channel
archive it
validate new version state
publish new version
commit
```

If any step fails, previous published version remains valid after rollback.

---

# 9. Phase 6 — Existing import/document hardening

Suggested migration if required:

```text
026-idempotency-hardening.sql
```

Only create it if schema changes are actually necessary.

## 9.1 Import verification

First inspect existing migration `015-import-batch-reliability.sql` and current unique constraints.

Required contract:

```text
same tenant + same idempotency key + same request hash
    -> same logical ImportBatch

same tenant + same idempotency key + different request hash
    -> conflict/error
```

Concurrent race handling:

```text
request A sees no row
request B sees no row
A inserts
B insert violates unique
B catches constraint violation
B reloads tenant+key
B verifies requestHash
B returns existing batch
```

Do not solve this with a JVM `synchronized` block.

## 9.2 Import processing idempotency

If current import currently only parses/maps and does not yet persist Customer/Receivable, make the E2E adapter/upsert deterministic using tenant + external ID.

Use PostgreSQL/JPA upsert-style service logic with DB unique constraints. Do not create duplicate Customer/Receivable rows on batch retry.

## 9.3 Document generation key

Add to `generation_jobs`:

```text
idempotency_key VARCHAR(200)
```

and:

```sql
UNIQUE (tenant_id, idempotency_key)
```

For API-generated jobs, a practical key is preferably supplied by caller (`Idempotency-Key`). If absent and generation is triggered by a stable upstream business operation, derive from stable inputs.

For current generation from mapping/template, deterministic candidate:

```text
SHA-256(
 tenantId +
 mappingConfigSha256 +
 templateConfigSha256 +
 normalizedPayload canonical hash +
 sorted outputFormats
)
```

Do not include timestamps/random values.

## 9.4 Generated object key

Make object-storage output path deterministic from job and format:

```text
reports/{tenantId}/{generationJobId}/{format-lowercase}.{ext}
```

A retry should overwrite/reuse the same object key instead of creating a random new object name.

This simplifies crash recovery after upload-before-DB-complete.

## 9.5 Worker recovery

Current `GenerationJob` has `PENDING/PROCESSING/...` state but no explicit lease metadata.

For this stage choose the simplest recovery approach:

- if Rabbit redelivery finds `COMPLETED`, no-op;
- if it finds `PENDING`, process;
- if it finds `PROCESSING` and `started_at` is older than configured timeout, allow recovery back to `PENDING`;
- otherwise avoid concurrent second processing.

If `started_at` is sufficient, do not add new lock columns just for symmetry with outbox.

---

# 10. Phase 7 — Tenant isolation implementation detail

## 10.1 Inventory table

Maintain a small table in tests/docs listing tenant ownership:

| Aggregate | Tenant column | Required scoped lookup |
|---|---|---|
| ImportBatch | yes | `findByIdAndTenantId` |
| GenerationJob | yes | `findByIdAndTenantId` |
| DocumentTemplate | yes | `findByIdAndTenantId` |
| TemplateVersion | tenant-aware repository query | tenant-scoped |
| Customer | yes | `findByIdAndTenantId` |
| Receivable | yes | `findByIdAndTenantId` |
| CollectionCase | yes | `findByIdAndTenantId` |
| NextAction | yes | `findByIdAndTenantId` |
| Communication | yes | `findByIdAndTenantId` |
| CommunicationAttempt | through Communication | parent-scoped |

## 10.2 Architecture test scope

Do not attempt to prove data isolation mathematically with ArchUnit.

Use ArchUnit only for obvious forbidden patterns in application/API layers, for example calls to repository `findById` for listed tenant-owned repositories.

The definitive proof remains integration tests:

```text
Tenant A principal
+ Tenant B UUID
-> 404
```

for critical APIs/services.

## 10.3 Background workers

Background consumers do not have `TenantContext` from an HTTP request.

Therefore async message payload/header MUST include tenant ID where applicable, and worker repositories MUST query by:

```text
tenantId + aggregateId
```

Never trust an aggregate ID alone in an async consumer when the entity is tenant-owned.

---

# 11. Phase 8 — Reliability test design

## 11.1 Test layers

Use three test levels:

### Unit

For pure logic:

```text
retry policy
state transitions
unknown event routing
idempotency key generation
eligibility rule
DeliveryResult mapping
```

### PostgreSQL integration

For:

```text
unique constraints
SKIP LOCKED
partial indexes
concurrent inserts
optimistic locking
pessimistic parent lock
```

### Full E2E with RabbitMQ Testcontainer

Only for a small number of representative paths:

```text
happy overdue -> SENT
transient -> retry -> SENT
duplicate communication message
```

Do not turn every unit scenario into a full Spring/Testcontainers test.

## 11.2 Concurrency tests

Use `CountDownLatch`, `CyclicBarrier`, or `Phaser` to force overlap.

Example assertion for outbox:

```text
insert 20 PENDING rows
start two claimers simultaneously
claimer A claimed IDs ∩ claimer B claimed IDs = empty
union size = expected claimed count
```

Example template test:

```text
start two createVersion calls simultaneously
both complete
versions are N+1 and N+2
no unique violation escapes to caller
```

## 11.3 No timing sleeps

Avoid tests such as:

```java
Thread.sleep(61000);
```

Use injected `Clock` and direct invocation of scheduler/recovery methods.

## 11.4 Duplicate-message assertion

For every async consumer with side effects, test:

```text
deliver same message/event twice
```

Assert:

```text
same final DB state
one logical entity
one external/mock side effect when terminal state already exists
```

---

# 12. Configuration contract

Add properties under one namespace:

```yaml
collectra:
  messaging:
    outbox-enabled: true
    outbox-publish-delay-ms: 1000
    outbox-batch-size: 50
    publisher-confirm-timeout-ms: 5000
    outbox-processing-timeout: 2m
    outbox-max-attempts: 6
    communication-max-attempts: 4
    communication-processing-timeout: 2m
```

Use `@ConfigurationProperties` rather than many scattered `@Value` fields once more than 3 settings are introduced.

Do not add dynamic DB-backed configuration at this stage.

---

# 13. API surface for the first vertical slice

The vertical slice does not require a large CRUD API.

Minimum useful API during development:

```text
POST /api/v1/customers
POST /api/v1/receivables
POST /api/v1/receivables/{id}/evaluate-overdue
GET  /api/v1/communications/{id}
GET  /api/v1/communications/{id}/attempts
```

If Customer/Receivable creation is supplied exclusively by import in this stage, the first two endpoints may remain test/internal-only and do not need public controllers.

Do not build search/filter/export APIs before the E2E path is green.

Expected communication response should expose operational state without content leakage by default:

```json
{
  "id": "uuid",
  "businessType": "RECEIVABLE",
  "businessId": "uuid",
  "channel": "EMAIL",
  "locale": "kk",
  "status": "SENT",
  "attemptCount": 1,
  "sentAt": "...",
  "lastErrorCode": null
}
```

Do not return rendered message bodies in list endpoints by default.

---

# 14. Error semantics

Use existing `ApiExceptionHandler`; extend it instead of creating a new global handler.

Required stable error codes:

```text
OUTBOX_UNKNOWN_EVENT
COMMUNICATION_NOT_FOUND
COMMUNICATION_STATE_CONFLICT
CUSTOMER_EMAIL_MISSING
RECEIVABLE_NOT_FOUND
RECEIVABLE_NOT_OVERDUE
TEMPLATE_VERSION_CONFLICT
IDEMPOTENCY_KEY_REUSED
```

Recommended HTTP mapping:

```text
400 malformed/invalid request
404 tenant-scoped resource absent
409 optimistic lock / idempotency key reused with different request
422 domain transition invalid when useful to client
503 synchronous infrastructure unavailable
```

Internal async failures are normally persisted in state and not surfaced as HTTP 503 because the caller has already received the asynchronous resource/job.

---

# 15. Observability with minimal implementation cost

Use Micrometer counters/timers if already available through Spring Boot Actuator. Do not introduce an observability platform dependency in this stage.

Minimum metrics:

```text
collectra.outbox.published
collectra.outbox.retry
collectra.outbox.dead
collectra.communication.sent
collectra.communication.retry
collectra.communication.failed
collectra.communication.gateway.duration
```

Recommended gauges may use repository counts only if cheap; avoid querying COUNT(*) on every scrape for large tables.

Structured logs should include IDs but not message content:

```text
tenantId
eventId
communicationId
nextActionId
generationJobId
attempt
status
```

Recipient should be masked in logs.

---

# 16. Migration/commit plan

Keep implementation reviewable.

Recommended migration grouping:

```text
022-outbox-reliability.sql
023-communication-core.sql
024-collection-core.sql
025-template-concurrency.sql
026-idempotency-hardening.sql   only if needed
```

Recommended implementation commits/PRs:

```text
1. outbox schema + states + claim/recovery
2. Rabbit confirm + router + outbox tests
3. communication schema/domain + mock gateway
4. dispatcher + retry/idempotency tests
5. customer/receivable/collection/next-action
6. overdue orchestration + E2E happy/retry path
7. template concurrency
8. import/document idempotency hardening
9. tenant-isolation + reliability regression suite
```

Each commit/PR should leave CI green.

---

# 17. Explicit decisions to prevent overengineering

The following decisions are normative for this stage:

1. **One Spring Boot deployment** — package/module boundaries only.
2. **One shared outbox table** — no per-module outboxes.
3. **One explicit OutboxEventRouter** — no plugin framework until event count justifies it.
4. **Rabbit retry queues for communication timing** — no second retry scheduler/table.
5. **Rendered Communication content is snapshotted** — no rerender on retry.
6. **EMAIL only for first business E2E** — SMS/WhatsApp/Telegram adapters remain mock/deferred.
7. **Overdue is derived**, not a persisted Receivable status.
8. **Two-state CollectionCase (`OPEN`, `RESOLVED`)** for first slice.
9. **One concrete `PaymentOverdueNotificationService`** — no generic action engine.
10. **PostgreSQL constraints resolve races** — no Redis/distributed locks.
11. **Clock injection in new reliability code** — no sleep-based tests.
12. **Existing import/document flows are hardened, not rewritten**.

---

# 18. Final acceptance matrix

| Area | Must prove | Failure if |
|---|---|---|
| Outbox | claim is exclusive, ACK required, retry/dead persisted | duplicate claim or silent publish |
| Unknown event | immediately observable `DEAD` | marked `PUBLISHED` |
| Communication | one logical send intent per idempotency key | duplicate rows under race |
| Attempts | one row per actual gateway invocation | retry history lost |
| Gateway | deterministic mock supports success/transient/permanent | random/flaky tests |
| Collection | one open case + one overdue action | repeated evaluator duplicates work |
| E2E | overdue receivable reaches SENT | external provider required |
| Template | concurrent edit/publish cannot corrupt versions | lost update/two published rows |
| Import | concurrent same key reuses one batch | duplicate batches |
| Document | duplicate request/replay reuses one job/object path | duplicate PDFs/jobs |
| Tenant | cross-tenant IDs behave as absent | IDOR/data leak |
| Recovery | stale PROCESSING/SENDING work resumes | restart permanently stalls work |

## Definition of Done

The stage is complete when a deterministic CI test proves that one overdue receivable produces one logical communication which survives concurrency, duplicate messages, transient failure and process-recovery conditions and ultimately reaches `SENT` through a mock channel gateway, while all tenant and database invariants remain intact.
