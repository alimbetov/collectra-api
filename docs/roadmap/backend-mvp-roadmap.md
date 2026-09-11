# Collectra Backend MVP — Technical Implementation Roadmap

Updated: 2026-09-11

This roadmap is re-baselined against the current `main` branch. It is intentionally implementation-oriented: existing subsystems are reused instead of being redesigned, package names match the repository, and code fragments describe the target shape of the next PRs.

Detailed Slice contracts in [`../specs/README.md`](../specs/README.md) are
authoritative when this overview omits a field, test or transaction detail.

## 1. Current baseline verified in `main`

Already implemented and **must not be rebuilt**:

- Campaign Core and campaign preparation/eligibility flow.
- `communication.domain.Message`, `MessageStatus`, `CommunicationChannel`.
- Message state transitions:
  - `QUEUED -> PROCESSING` via `beginAttempt(Instant)`;
  - `PROCESSING -> SENT` via `markSent(...)`;
  - `PROCESSING -> RETRY_WAIT` via `scheduleRetry(...)`;
  - `PROCESSING -> FAILED` via `markFailed(...)`;
  - `RETRY_WAIT -> QUEUED` via `requeue()`.
- Message persistence fields already include `attemptCount`, `processingStartedAt`, `nextRetryAt`, provider id and last error.
- Tenant-scoped/paged `MessageRepository`.
- `CampaignRun` already contains `recipientCount`, `sentCount`, `failedCount`, `skippedCount`, `retryCount`.
- Application `Clock` infrastructure.
- Transactional Outbox infrastructure (`OutboxService`, publisher/retry/state machinery).
- RabbitMQ-based asynchronous document worker.
- Template compiler/renderer, HTML escaping, placeholder validation and `renderText`.
- Async document/PDF generation pipeline.
- RustFS/FileService lifecycle foundation.
- CSV/Excel/XML/JSON import foundation.
- Campaign stabilization integration coverage already exists in the test suite.
- Slice 2 communication processing core is merged in PR #39 / commit
  `2dcefe7dc39300a338304560ff0490ce29dc0f8f`.

### Consequence

The immediate blocker is the shared integration-test connection budget documented
in [`../specs/integration-test-runtime.md`](../specs/integration-test-runtime.md).
After that repair, the next missing capability is Slice 3 messaging between the
existing Outbox and the merged processing core.

---

# 2. Slice 2 — Communication processing core

**Status: MERGED / FULL-SUITE VERIFY REPAIR REQUIRED**

Suggested branch:

```text
feat/message-processing-core
```

No KumoMTA network call in this slice. The slice creates a provider-independent processing core and an in-memory/test delivery implementation only.

## 2.1 Target package structure

```text
io.collectra.api.communication
├── application
│   ├── MessageDeliveryWorker.java
│   ├── MessageStateService.java
│   ├── MessageRetryPolicy.java
│   ├── MessageRecoveryService.java
│   ├── DeliveryGateway.java
│   ├── DeliveryCommand.java
│   ├── DeliveryResult.java
│   └── DeliveryFailureKind.java
├── domain
│   ├── Message.java                     # existing
│   ├── MessageStatus.java               # existing
│   └── CommunicationChannel.java        # existing
└── infrastructure
    ├── MessageRepository.java            # extend existing
    └── ...                               # broker/provider adapters come later
```

Follow the existing document subsystem pattern:

```text
DocumentGenerationWorker        -> MessageDeliveryWorker
GenerationJobStateService       -> MessageStateService
GenerationJobRepository lock    -> MessageRepository lock
```

## 2.2 Add locked tenant-scoped lookup

Do not use plain `findById()` inside a worker. Message mutations need an exclusive row lock, and tenant id remains part of the lookup contract.

Target addition to `MessageRepository`:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        select m
        from Message m
        where m.id = :id
          and m.tenantId = :tenantId
        """)
Optional<Message> findLockedByIdAndTenantId(
        @Param("id") UUID id,
        @Param("tenantId") UUID tenantId);
```

Why:

- broker redelivery can produce duplicate execution attempts;
- multiple application replicas may consume concurrently;
- the row lock makes the state transition the serialization point;
- tenant isolation is preserved even in an asynchronous path.

Do **not** add a second status state machine in a service. `Message` remains responsible for legal transitions.

## 2.3 Delivery port

The application layer must not know SMTP, KumoMTA HTTP endpoints or provider DTOs.

```java
public interface DeliveryGateway {
    DeliveryResult deliver(DeliveryCommand command);
}
```

For the first version the gateway can support only `EMAIL`, while the interface remains provider-neutral.

```java
public record DeliveryCommand(
        UUID messageId,
        UUID tenantId,
        CommunicationChannel channel,
        String destination,
        String subject,
        String body) {
}
```

```java
public sealed interface DeliveryResult {

    record Accepted(String providerMessageId) implements DeliveryResult {}

    record Rejected(
            DeliveryFailureKind kind,
            String code,
            String message) implements DeliveryResult {}
}
```

```java
public enum DeliveryFailureKind {
    RETRYABLE,
    PERMANENT
}
```

Provider exceptions should be translated at the adapter boundary. The worker should normally operate on `DeliveryResult`, not parse provider-specific exceptions.

## 2.4 MessageStateService

Use the same transaction boundary style already used by `GenerationJobStateService`: short transactions for state changes, with the external network call **outside** the database transaction.

```java
@Service
public class MessageStateService {
    private final MessageRepository messages;
    private final Clock clock;

    public MessageStateService(MessageRepository messages, Clock clock) {
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional
    public Snapshot begin(UUID tenantId, UUID messageId) {
        Message message = messages.findLockedByIdAndTenantId(messageId, tenantId)
                .orElseThrow();

        if (message.getStatus() == MessageStatus.SENT
                || message.getStatus() == MessageStatus.FAILED) {
            return null;
        }

        if (message.getStatus() != MessageStatus.QUEUED) {
            return null;
        }

        message.beginAttempt(clock.instant());

        return new Snapshot(
                message.getId(),
                message.getTenantId(),
                message.getCampaignRunId(),
                message.getChannel(),
                message.getDestination(),
                message.getSubject(),
                message.getBody(),
                message.getAttemptCount());
    }

    @Transactional
    public void sent(UUID tenantId, UUID messageId, String providerMessageId) {
        Message message = locked(tenantId, messageId);
        message.markSent(providerMessageId, clock.instant());
    }

    @Transactional
    public void retry(
            UUID tenantId,
            UUID messageId,
            Instant nextRetryAt,
            String code,
            String errorMessage) {
        Message message = locked(tenantId, messageId);
        message.scheduleRetry(nextRetryAt, code, errorMessage);
    }

    @Transactional
    public void fail(
            UUID tenantId,
            UUID messageId,
            String code,
            String errorMessage) {
        locked(tenantId, messageId).markFailed(code, errorMessage);
    }

    private Message locked(UUID tenantId, UUID messageId) {
        return messages.findLockedByIdAndTenantId(messageId, tenantId)
                .orElseThrow();
    }

    public record Snapshot(
            UUID messageId,
            UUID tenantId,
            UUID campaignRunId,
            CommunicationChannel channel,
            String destination,
            String subject,
            String body,
            int attemptCount) {}
}
```

Important: the exact handling of `RETRY_WAIT` belongs in the retry dispatcher/recovery step. `begin()` must not silently bypass the entity invariant by directly mutating status.

## 2.5 Retry policy

Retry timing is application policy, not domain time calculation.

Initial deterministic policy:

```text
attempt 1 -> +1 minute
attempt 2 -> +10 minutes
attempt 3 -> +1 hour
attempt 4+ -> terminal FAILED
```

Target class:

```java
@Component
public class MessageRetryPolicy {
    private static final int MAX_ATTEMPTS = 4;

    public boolean exhausted(int attemptCount) {
        return attemptCount >= MAX_ATTEMPTS;
    }

    public Instant nextRetryAt(int attemptCount, Instant now) {
        Duration delay = switch (attemptCount) {
            case 1 -> Duration.ofMinutes(1);
            case 2 -> Duration.ofMinutes(10);
            default -> Duration.ofHours(1);
        };
        return now.plus(delay);
    }
}
```

Keep this policy independent of RabbitMQ TTL configuration so it can be tested against a fixed `Clock` and so the database remains authoritative.

## 2.6 Worker orchestration

```java
@Service
public class MessageDeliveryWorker {
    private final MessageStateService states;
    private final DeliveryGateway delivery;
    private final MessageRetryPolicy retryPolicy;
    private final Clock clock;

    public void deliver(UUID tenantId, UUID messageId) {
        var snapshot = states.begin(tenantId, messageId);
        if (snapshot == null) {
            return; // duplicate/redelivered/terminal message
        }

        DeliveryResult result = delivery.deliver(
                new DeliveryCommand(
                        snapshot.messageId(),
                        snapshot.tenantId(),
                        snapshot.channel(),
                        snapshot.destination(),
                        snapshot.subject(),
                        snapshot.body()));

        switch (result) {
            case DeliveryResult.Accepted accepted ->
                    states.sent(tenantId, messageId, accepted.providerMessageId());

            case DeliveryResult.Rejected rejected -> {
                if (rejected.kind() == DeliveryFailureKind.PERMANENT
                        || retryPolicy.exhausted(snapshot.attemptCount())) {
                    states.fail(
                            tenantId,
                            messageId,
                            rejected.code(),
                            rejected.message());
                } else {
                    states.retry(
                            tenantId,
                            messageId,
                            retryPolicy.nextRetryAt(
                                    snapshot.attemptCount(), clock.instant()),
                            rejected.code(),
                            rejected.message());
                }
            }
        }
    }
}
```

### Critical transaction rule

Never hold the pessimistic database row lock while calling KumoMTA:

```text
TX 1: lock -> QUEUED to PROCESSING -> commit
                 |
                 v
        external provider call
                 |
                 v
TX 2: lock -> SENT / RETRY_WAIT / FAILED -> commit
```

This avoids long transactions and database connection starvation during provider latency/outage.

## 2.7 Recovery of stuck PROCESSING messages

`Message.processingStartedAt` already exists, so a new database column is not required for the first recovery implementation.

Add repository query for stale processing messages, tenant-aware and bounded:

```java
Slice<Message> findAllByStatusAndProcessingStartedAtBefore(
        MessageStatus status,
        Instant threshold,
        Pageable pageable);
```

For stronger multi-node recovery, prefer a PostgreSQL native claim using `FOR UPDATE SKIP LOCKED` rather than loading an unbounded collection.

Recovery policy:

```text
PROCESSING older than configured timeout
    -> classify as interrupted attempt
    -> RETRY_WAIT (or FAILED when max attempts exhausted)
    -> later requeue through retry dispatcher
```

Do not simply write `status = QUEUED` in SQL; recovery should preserve the domain transition contract. If the current domain API cannot express recovery from stale `PROCESSING`, add an explicit domain method such as:

```java
public void recoverInterruptedAttempt(
        Instant nextRetryAt,
        String errorCode,
        String errorMessage) {
    scheduleRetry(nextRetryAt, errorCode, errorMessage);
}
```

The method name documents intent and prevents infrastructure code from bypassing invariants.

## 2.8 Requeue due retries

A scheduler should only move due retry records back to `QUEUED`; it must not send mail itself.

```text
RETRY_WAIT where nextRetryAt <= now
    -> lock bounded page
    -> message.requeue()
    -> append MESSAGE_DELIVERY_REQUESTED to Outbox
```

Suggested class:

```text
communication.application.MessageRetryDispatcher
```

This makes retry dispatch restart-safe and broker-independent.

The merged Slice 2 intentionally provides `dispatchDue()` without scheduling or
Outbox publication. Slice 3 must enable scheduling only when it can commit
`message.requeue()` and `MESSAGE_DELIVERY_REQUESTED` in the same transaction.

## 2.9 CampaignRun counters

`CampaignRun` has delivery counters but currently needs behavior to mutate them.

Add explicit methods instead of exposing setters:

```java
public void messageSent() {
    sentCount++;
}

public void messageFailed() {
    failedCount++;
}

public void messageRetryScheduled() {
    retryCount++;
}
```

Before committing this exact API, decide and test the semantic meaning:

- `sentCount` and `failedCount` are terminal recipient/message outcomes;
- `retryCount` is number of retry scheduling events, not number of recipients currently waiting;
- counters must never be double-incremented on broker redelivery.

Recommended implementation: update the relevant counter in the same transaction that moves the `Message` to its new status. Lock the `CampaignRun` row when mutating counters.

If one campaign recipient can later produce multiple channel messages, rename/extend counters before enabling multi-channel fan-out. Current `recipientCount == sent + failed + skipped` completion invariant assumes one terminal delivery outcome per recipient.

This is an important design constraint for Slice 4.

## 2.10 Slice 2 tests

Required:

```text
MessageStateServiceIntegrationTest
- begin locks and changes QUEUED -> PROCESSING
- duplicate begin does not start a second attempt
- tenant A cannot claim tenant B message

MessageDeliveryWorkerTest
- Accepted -> SENT
- retryable rejection -> RETRY_WAIT
- permanent rejection -> FAILED
- max attempts -> FAILED

MessageRetryPolicyTest
- deterministic 1m / 10m / 1h schedule using fixed Clock

MessageRecoveryIntegrationTest
- stale PROCESSING is recovered
- recent PROCESSING is untouched

MessageConcurrencyIntegrationTest
- two concurrent claims for same message
- exactly one transition/attempt succeeds

ArchitectureTest
- communication.domain does not depend on infrastructure/provider packages
- KumoMTA types cannot leak into domain/application ports
```

Definition of done:

```text
mvn verify
```

must be green.

---

# 3. Slice 3 — RabbitMQ + Outbox route for message delivery

Suggested branch:

```text
feat/message-delivery-messaging
```

The Outbox subsystem already exists. Extend it; do not build another outbox.

## 3.1 New event

Use a stable event type:

```text
MESSAGE_DELIVERY_REQUESTED
```

Minimal payload:

```json
{
  "tenantId": "...",
  "messageId": "..."
}
```

Do not copy subject/body into the broker event. The database `Message` is authoritative; RabbitMQ carries only the work reference.

## 3.2 Outbox router extension

Target:

```java
if ("MESSAGE_DELIVERY_REQUESTED".equals(eventType)) {
    return new OutboxRoute(
            CommunicationMessagingConfig.EXCHANGE,
            CommunicationMessagingConfig.ROUTING_KEY);
}
```

Prefer replacing the growing `if` chain with a small route map once a second/third event type makes that cleaner, but do not turn this PR into a generic event-bus redesign.

## 3.3 CommunicationMessagingConfig

Mirror the document messaging conventions where useful:

```text
collectra.communication
collectra.communication.message-delivery
```

Use routing key `message.delivery.requested`. Do not add a Rabbit business-retry
topology: `Message.nextRetryAt` remains the retry source of truth.

The exact exchange/queue names should be constants in one config class.

## 3.4 Listener

```java
@Component
public class MessageDeliveryListener {
    private final MessageDeliveryWorker worker;

    @RabbitListener(queues = CommunicationMessagingConfig.QUEUE)
    public void consume(MessageDeliveryRequested event) {
        worker.deliver(event.tenantId(), event.messageId());
    }
}
```

The listener stays thin. Provider classification, state transitions and retry policy do not belong in the listener.

### Difference from existing document listener

The existing document listener keeps retry count in Rabbit headers. For communication delivery, prefer the persisted `Message.attemptCount` + `nextRetryAt` as the source of truth. Rabbit headers may be diagnostic, but must not determine business retry state.

---

# 4. Slice 4 — KumoMTA email adapter

Suggested branch:

```text
feat/kumomta-email-provider
```

Dependency direction:

```text
MessageDeliveryWorker
        |
        v
DeliveryGateway                         application port
        ^
        |
KumoMtaEmailDeliveryGateway             infrastructure adapter
        |
        v
KumoMTA
```

## 4.1 Adapter boundary

Suggested package:

```text
io.collectra.api.communication.infrastructure.kumomta
├── KumoMtaEmailDeliveryGateway.java
├── KumoMtaClient.java
├── KumoMtaProperties.java
├── KumoMtaRequest.java
├── KumoMtaResponse.java
└── KumoMtaErrorClassifier.java
```

`DeliveryGateway` remains free of KumoMTA classes.

## 4.2 Failure classification

Initial policy:

```text
2xx / accepted                       -> Accepted
408 / timeout / connection failure   -> RETRYABLE
429                                  -> RETRYABLE
5xx                                  -> RETRYABLE
invalid destination / provider 4xx   -> PERMANENT
authentication/configuration failure -> PERMANENT + alert
```

The final HTTP/API mapping must be adjusted to the actual KumoMTA endpoint/protocol used in deployment.

Never persist credentials, full authorization headers or unsafe provider response bodies in `lastErrorMessage`.

## 4.3 Configuration

```yaml
collectra:
  communication:
    kumomta:
      base-url: ${KUMOMTA_BASE_URL}
      connect-timeout: 2s
      read-timeout: 10s
```

Credentials must come from environment/secret management, not repository YAML.

## 4.4 Tests

- adapter request mapping;
- accepted provider response;
- timeout -> retryable;
- 429/5xx -> retryable;
- invalid recipient/4xx -> permanent where applicable;
- sanitized errors;
- no KumoMTA classes visible outside infrastructure package.

---

# 5. Slice 5 — CampaignRun -> Message materialization

Suggested branch:

```text
feat/campaign-message-materialization
```

This slice connects Campaign Core, existing templates and communication persistence.

## 5.1 Existing components to reuse

Do not rebuild template rendering. Reuse:

```text
TemplateVersionRepository
TemplateCompiler
TemplateRenderer
TemplateLocaleResolver / localization services
```

`TemplateRenderer` already:

- detects missing values;
- HTML-escapes placeholder values;
- supports text rendering;
- returns rendered HTML.

## 5.2 Materialization service

Suggested target:

```text
campaign.application.CampaignMessageMaterializer
```

Flow:

```text
CampaignRun READY
    -> page CampaignRecipient snapshot
    -> re-check final eligibility where required
    -> resolve destination
    -> resolve locale/template version
    -> build normalized payload
    -> render subject/body
    -> Message.queued(...)
    -> save Message
    -> OutboxService.append(MESSAGE_DELIVERY_REQUESTED)
    -> continue page
```

Message + Outbox row must be written in the **same database transaction** for a batch/page.

## 5.3 Idempotency

The existing unique constraint on `messages.campaign_recipient_id` already gives a strong one-message-per-recipient invariant.

For the current EMAIL-only MVP this is useful and should be preserved.

Before enabling SMS/WhatsApp parallel fan-out, this constraint must evolve to something like:

```text
unique(campaign_recipient_id, channel)
```

Do not change it early unless multi-channel delivery is actually introduced, because the current invariant simplifies retry/counter semantics.

## 5.4 Rendering snapshot

`Message` already persists:

```text
templateVersionId
resolvedLocale
subject
body
```

Therefore the rendered subject/body are the immutable delivery snapshot. Editing a template later must not mutate already materialized messages.

Do not re-render the body inside `MessageDeliveryWorker`.

## 5.5 Large campaign handling

- use `Slice`/keyset-style bounded processing;
- avoid loading all recipients into memory;
- batch size should be configurable;
- avoid N+1 resolution of customer email/segment/template data;
- commit per bounded batch rather than one transaction for an entire large campaign.

---

# 6. Slice 6 — Campaign delivery counters and completion

This may be combined with Slice 5 only if the diff remains narrow; otherwise keep it separate.

Target responsibility:

```text
Message terminal transition
    -> CampaignRun counters
    -> if all recipient outcomes terminal
       -> CampaignRun.complete(clock.instant())
```

Important invariant already present in `CampaignRun`:

```text
sentCount + failedCount + skippedCount == recipientCount
```

Do not mark a run completed based on queue emptiness or RabbitMQ acknowledgements. Completion is derived from durable database state.

For counter correctness under redelivery:

- transition and counter update occur in the same transaction;
- only increment when a real state transition happened;
- lock `Message` and `CampaignRun` consistently to avoid races/deadlocks;
- define a deterministic lock order, for example Message first, CampaignRun second, and keep it everywhere.

---

# 7. Slice 7 — Attachments / generated documents integration

Depends on Slice 3 messaging, Slice 4 KumoMTA adapter and Slice 5
materialization. Before implementation, close the CJK font readiness gate from
the detailed Slice 7 contract.

The document/PDF and FileService foundations already exist. The remaining work is integration with communication, not rebuilding PDF/RustFS.

Target flow:

```text
Campaign materialization
    -> request document generation when attachment is required
    -> existing document worker renders/stores output
    -> persist message attachment reference
    -> only then enqueue MESSAGE_DELIVERY_REQUESTED
```

Likely new persistence:

```text
message_attachments
- id
- tenant_id
- message_id
- generation_job_id
- generated_document_id (required when READY)
- filename
- content_type
- required
- status (PENDING / READY / FAILED)
- created_at
```

KumoMTA adapter receives resolved immutable attachment descriptors. It must not generate PDFs or query campaign business rules.

QR generation belongs to document/template preparation, not the email provider adapter.

---

# 8. Delivery API and observability

After the end-to-end EMAIL pipeline works:

## API

Add paged operational endpoints under the communication/campaign boundary, for example:

```text
GET /api/v1/campaigns/{campaignId}/runs/{runId}/messages
GET /api/v1/campaigns/{campaignId}/runs/{runId}/messages/{messageId}
```

Filters:

```text
status
channel
customerId
```

Never expose another tenant's message by raw UUID lookup.

Useful response fields:

```text
id
campaignRunId
customerId
channel
destination (masked where appropriate)
status
attemptCount
nextRetryAt
providerMessageId
lastErrorCode
lastErrorMessage
sentAt
createdAt
```

## Metrics

At minimum:

```text
collectra_message_delivery_total{channel,result}
collectra_message_delivery_latency_seconds{channel}
collectra_message_retry_total{channel,code}
collectra_message_stuck_processing
collectra_message_queue_age_seconds
```

Add structured log correlation keys:

```text
tenantId
campaignId
campaignRunId
messageId
providerMessageId
```

Do not use destination/email address as the primary correlation key.

---

# 9. Items removed from the old critical path

These remain valid product areas but are **not current blockers for the communication MVP** because substantial implementations already exist:

- generic Template Engine construction;
- generic CSV/Excel/XML/JSON parser construction;
- generic RustFS/FileService lifecycle construction;
- campaign stabilization work already represented by current code/tests;
- generic Outbox construction.

Future changes in those modules should be driven by a concrete missing integration or failing requirement rather than rebuilt as roadmap milestones.

---

# 10. Revised implementation order

```text
DONE  Campaign Core / stabilization baseline
  |
DONE  Message persistence + invariants (Slice 1)
  |
DONE  Message processing core (PR #39)
  |
NEXT  Integration-test runtime repair
      - central test Hikari connection budget
      - reuse shared PostgreSQL Testcontainer and Spring contexts
      - full-suite verification in one Maven JVM
  |
      RabbitMQ + existing Outbox integration
      - MESSAGE_DELIVERY_REQUESTED
      - communication exchange/queue/listener
  |
      KumoMTA adapter
      - provider mapping
      - timeout/error classification
      - providerMessageId
  |
      CampaignRun -> Message materialization
      - existing TemplateRenderer
      - rendered snapshot
      - idempotency
      - paged batches
  |
      Campaign counters + durable completion
  |
      Attachments / generated-document integration
  |
      Delivery API + metrics + operational hardening
  |
      SMS / WhatsApp / Telegram / In-App expansion
```

---

# 11. PR discipline

Every slice should remain narrow:

- one primary responsibility;
- Liquibase migration only when the slice truly requires schema change;
- domain invariants remain inside entities;
- external network calls never run while holding database row locks;
- async payloads contain durable identifiers rather than duplicated business state;
- tenant id is explicit in asynchronous commands/events;
- application time comes from `Clock`;
- unit + PostgreSQL integration + architecture tests;
- `mvn verify` green;
- no automatic merge unless explicitly requested.

## Immediate next PR acceptance criteria

The next PR is complete when all of the following are true:

1. test profile centrally limits Hikari to `maximum-pool-size: 3` and
   `minimum-idle: 0`;
2. production datasource settings are unchanged;
3. concurrent persistence tests still pass;
4. full `mvn verify` runs in one Maven JVM without `too many clients already`;
5. the repair remains a narrow test-infrastructure PR.

Only after this repair is merged and `main` is green should Slice 3 begin.
