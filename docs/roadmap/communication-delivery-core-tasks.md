# Communication / Delivery Core — implementation tasks

> **Статус:** backlog до code audit. При выполнении задач использовать обязательные
> corrections из `docs/roadmap/communication-delivery-core-code-audit.md`. Audit
> уточняет schema, payload/locale contracts, provider command, stale recovery,
> paging, API/security и расширяет обязательный test set.

Этот документ является backlog для реализации ТЗ.

Основной detailed design:

```text
docs/roadmap/communication-delivery-core-implementation.md
```

Цель backlog: каждая задача должна быть достаточно маленькой, чтобы её можно было реализовать отдельным commit/PR slice и проверить тестами.

---

# TASK 1 — Message domain + Liquibase

## Цель

Создать persisted модель одной фактической попытки доставки communication message как aggregate `Message`.

## Новые файлы

```text
src/main/java/io/collectra/api/communication/domain/Message.java
src/main/java/io/collectra/api/communication/domain/MessageStatus.java
src/main/java/io/collectra/api/communication/domain/CommunicationChannel.java
src/main/java/io/collectra/api/communication/infrastructure/MessageRepository.java
src/main/resources/db/changelog/changes/026-communication-delivery-core.sql
```

Также обновить:

```text
src/main/resources/db/changelog/db.changelog-master.yaml
```

## MessageStatus

```java
public enum MessageStatus {
    QUEUED,
    PROCESSING,
    RETRY_WAIT,
    SENT,
    FAILED
}
```

## CommunicationChannel

```java
public enum CommunicationChannel {
    EMAIL,
    SMS,
    WHATSAPP,
    TELEGRAM,
    IN_APP
}
```

## Message required fields

```text
UUID id
UUID tenantId
UUID campaignId
UUID campaignRunId
UUID campaignRecipientId
UUID customerId
UUID invoiceId nullable
UUID templateVersionId
CommunicationChannel channel
String destination
String resolvedLocale
String subject nullable
String body
MessageStatus status
int attemptCount
Instant processingStartedAt nullable
Instant nextRetryAt nullable
String providerMessageId nullable
String lastErrorCode nullable
String lastErrorMessage nullable
Instant sentAt nullable
```

## DB rules

Unique:

```text
(campaign_recipient_id)
```

Indexes:

```text
(campaign_run_id, status)
(status, next_retry_at)
(tenant_id, created_at)
```

## Domain methods

Минимум:

```text
beginAttempt()
markSent(...)
scheduleRetry(...)
markFailed(...)
requeue()
```

Каждый method проверяет допустимый previous status.

## Acceptance tests

Unit tests:

```text
QUEUED -> PROCESSING allowed
PROCESSING -> SENT allowed
PROCESSING -> RETRY_WAIT allowed
PROCESSING -> FAILED allowed
RETRY_WAIT -> QUEUED allowed
SENT -> PROCESSING forbidden
FAILED -> QUEUED forbidden
attemptCount increments on beginAttempt
```

## Не делать

```text
DeliveryAttempt table
provider-specific fields
JSON provider response blob
generic state machine framework
```

---

# TASK 2 — Atomic Message claim

## Цель

Гарантировать, что два Rabbit consumers не отправят один Message одновременно.

## Изменить

```text
MessageRepository.java
```

Добавить conditional update:

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("""
    update Message m
       set m.status = io.collectra.api.communication.domain.MessageStatus.PROCESSING,
           m.attemptCount = m.attemptCount + 1,
           m.processingStartedAt = :now,
           m.updatedAt = :now,
           m.version = m.version + 1
     where m.id = :messageId
       and m.status = io.collectra.api.communication.domain.MessageStatus.QUEUED
""")
int claim(@Param("messageId") UUID messageId, @Param("now") Instant now);
```

Можно использовать enum parameters, если Hibernate/query style проекта это предпочитает.

## Правило worker

```java
if (messages.claim(messageId) == 0) {
    return;
}
```

Provider вызывается только после `claim == 1`.

## Acceptance

Concurrent test:

```text
two callers -> same Message
only one claim returns 1
provider must later be invoked once
```

## Не делать

```text
Redis lock
ShedLock
distributed lock table
synchronized Java lock
```

---

# TASK 3 — CampaignRun delivery counters

## Цель

Общая CampaignRun получает результат всех канальных доставок.

## Изменить

```text
CampaignRun.java
CampaignRunRepository.java
Liquibase campaign_runs changeSet
```

## Новые fields

```java
private int recipientCount;
private int sentCount;
private int failedCount;
private int skippedCount;
private int retryCount;
```

DB defaults:

```text
0
```

## Что НЕ хранить

Не хранить counters:

```text
queuedCount
processingCount
retryWaitCount
```

Причина: это transient status distribution и легко получить рассинхронизацию. При необходимости считать query по `messages`.

## Atomic repository updates

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("""
    update CampaignRun r
       set r.sentCount = r.sentCount + :delta,
           r.updatedAt = :now,
           r.version = r.version + 1
     where r.id = :runId
       and r.tenantId = :tenantId
       and r.status = io.collectra.api.campaign.domain.CampaignRunStatus.RUNNING
""")
int incrementSent(UUID tenantId, UUID runId, int delta, Instant now);
```

Аналогично:

```text
incrementFailed
incrementSkipped
incrementRetry
```

## recipientCount

В `CampaignService.prepare(...)` после завершения page loop уже есть `created`.

Вместо:

```java
run.ready();
```

целевой API:

```java
run.ready(created, Instant.now(clock));
```

или отдельный setter domain method.

## Clock cleanup

Сейчас `CampaignRun` использует `Instant.now()` внутри entity. При этой задаче заменить на переданный `Instant now`, чтобы lifecycle был тестируемым и соответствовал уже существующему application `Clock`.

## Completion condition

```text
sentCount + failedCount + skippedCount == recipientCount
```

при `status == RUNNING`.

## Acceptance

```text
recipientCount persists after prepare
sent increments atomically
failed increments atomically
skipped increments atomically
retry increments atomically
```

---

# TASK 4 — Eligibility result for delivery

## Цель

Eligibility recheck перед delivery должен возвращать не только current totals, но и позволять безопасно учитывать newly skipped recipients.

## Текущий код

`CampaignEligibilityService.recheck(...)` проходит по всем recipients и вызывает:

```text
eligible()
skip(...)
```

## Проблема

Если второй раз recheck обработает уже `SKIPPED` recipient и application просто сделает `skippedCount += result.skipped()`, CampaignRun counter удвоится.

## Доработка

Расширить result:

```java
public record EligibilityResult(
    int total,
    int eligible,
    int skipped,
    int newlySkipped
) {}
```

Внутри:

```java
boolean wasSkipped = recipient.getStatus() == CampaignRecipientStatus.SKIPPED;

recipient.skip("PAID");

if (!wasSkipped) {
    newlySkipped++;
}
```

То же для:

```text
CUSTOMER_INACTIVE
NO_CONTACT
PAID
```

## Важное правило

`Message` создаётся только для final status:

```text
ELIGIBLE
```

## Acceptance

```text
first recheck PAID -> newlySkipped=1
second recheck same recipient -> newlySkipped=0
CampaignRun skippedCount must not double
```

---

# TASK 5 — CampaignDeliveryStatsService

## Цель

Сделать одну точку, куда общий delivery pipeline докладывает итог: success/failure/retry/skip.

## Новый файл

```text
src/main/java/io/collectra/api/communication/application/CampaignDeliveryStatsService.java
```

## API

```java
void recordSent(UUID runId);
void recordFailed(UUID runId);
void recordSkipped(UUID runId, int count);
void recordRetry(UUID runId);
void completeIfDone(UUID runId);
```

## Правило

`ChannelProvider` НЕ вызывает этот service.

Его вызывает delivery application layer только после успешного transition Message.

## Example

```java
@Transactional
public void markSent(UUID messageId, ProviderSendResult result) {
    Message message = requireProcessing(messageId);
    message.markSent(result.providerMessageId(), Instant.now(clock));
    stats.recordSent(message.getCampaignRunId());
}
```

## Completion

После:

```text
recordSent
recordFailed
recordSkipped
```

проверить завершение run.

Для v1 можно reload `CampaignRun` и сравнить counters.

Нагрузка здесь небольшая и generic coordinator не нужен.

## Acceptance

```text
retry does not increase failedCount
success increases sentCount once
permanent failure increases failedCount once
skip increases skippedCount only by newly skipped
last terminal outcome completes run
```

---

# TASK 6 — Message content snapshot

## Цель

Message должен хранить уже готовый текст, чтобы worker не зависел от изменения template/customer/invoice после queue.

## Новый component

```text
communication/application/MessageContentFactory.java
communication/application/CampaignMessagePayloadFactory.java
```

или переиспользовать существующий template rendering service напрямую, если подходящий публичный service уже существует.

## Contract

```java
public record RenderedMessage(
    UUID templateVersionId,
    String resolvedLocale,
    String subject,
    String body
) {}
```

```java
RenderedMessage render(
    UUID tenantId,
    CampaignRecipient recipient,
    Campaign campaign
);
```

Payload factory обязан сформировать существующие canonical paths
`document.number`, `document.date`, `customer.name`, `customer.*`, `invoice.*` и
перенести entity custom fields под `custom.customer`/`custom.invoice`. Exact mapping
и locale fallback algorithm зафиксированы в code audit.

## Phase 1

Только EMAIL template rendering.

Не менять Campaign creation для остальных channels в этой задаче.

## Правило snapshot

После создания `Message` worker использует:

```text
message.destination
message.subject
message.body
```

и НЕ читает повторно:

```text
TemplateVersion
Customer
Invoice
CampaignSelection
```

## Acceptance

Изменить template после создания Message -> отправляемый Message body остаётся прежним snapshot.

---

# TASK 7 — MessageService + transactional Outbox append

## Цель

ELIGIBLE CampaignRecipient превращается ровно в один Message и `MESSAGE_DELIVERY_REQUESTED` Outbox event.

## Новый файл

```text
communication/application/MessageService.java
```

## Method

```java
@Transactional
public CreateMessageResult createFromRecipient(
    Campaign campaign,
    CampaignRecipient recipient
)
```

## Algorithm

```text
1. validate recipient == ELIGIBLE
2. parse CommunicationChannel
3. check existing Message recipient+channel
4. render final content
5. save Message status=QUEUED
6. append shared Outbox event
7. commit
```

## Outbox

```java
outbox.append(
    tenantId,
    "MESSAGE",
    messageId,
    "MESSAGE_DELIVERY_REQUESTED",
    payload
);
```

Payload:

```json
{"messageId":"..."}
```

## Idempotency

Application check + DB unique constraint.

Application check improves normal behavior; unique constraint protects race.

## Acceptance

```text
eligible -> Message + Outbox
skipped -> no Message
same recipient called twice -> one Message
failed transaction -> neither Message nor Outbox persisted
```

---

# TASK 8 — CampaignDeliveryService start orchestration

## Цель

Запустить delivery для READY run.

## Новый файл

```text
communication/application/CampaignDeliveryService.java
```

## Public method

```java
DeliveryStartResult start(UUID tenantId, UUID runId)
```

## Preconditions

```text
run exists for tenant
run.status == READY
```

Run загружается через `PESSIMISTIC_WRITE`; неверный lifecycle возвращает domain
exception, отображаемый в HTTP 409.

## Flow

```text
READY -> RUNNING
eligibility.recheck
stats.recordSkipped(newlySkipped)
load recipients
for ELIGIBLE:
    messageService.createFromRecipient(...)
```

Recipients обрабатываются стабильными pages/slices по 500 с batch-load Customer,
Email и Invoice. Unpaged run list и per-recipient repository calls запрещены.

## Important

Не создавать Message для:

```text
SNAPSHOT
SKIPPED
```

После recheck нормальное состояние должно быть ELIGIBLE или SKIPPED.

## Empty run

Если `recipientCount == 0`, run можно завершить сразу.

## Endpoint

Добавить команду в существующий CampaignController либо отдельный thin controller.

Предпочтение: текущий CampaignController, если route naturally относится к campaign run.

## Acceptance

```text
READY run starts
non-READY rejected
PAID recipient skipped
eligible recipient creates Message
repeat start on RUNNING/COMPLETED rejected
```

---

# TASK 9 — Communication RabbitMQ topology

## Цель

Одна очередь для всех communication channels.

## Новый файл

```text
communication/infrastructure/CommunicationMessagingConfig.java
```

## Constants

```java
public static final String EXCHANGE = "collectra.communication";
public static final String QUEUE = "collectra.communication.delivery";
public static final String ROUTING_KEY = "communication.delivery.requested";
```

## Beans

```text
DirectExchange
Queue
Binding
```

## Изменить

```text
shared/outbox/OutboxEventRouter.java
```

Добавить:

```text
MESSAGE_DELIVERY_REQUESTED
 -> CommunicationMessagingConfig.EXCHANGE
 -> CommunicationMessagingConfig.ROUTING_KEY
```

## Не делать

```text
EMAIL queue
SMS queue
WhatsApp queue
per-channel exchange
```

## Acceptance

Router returns correct route for communication event.

---

# TASK 10 — Provider result model

## Новые файлы

```text
communication/domain/ProviderResultStatus.java
communication/domain/ProviderSendResult.java
communication/domain/ProviderSendCommand.java
communication/infrastructure/provider/ChannelProvider.java
communication/infrastructure/provider/ChannelProviderRegistry.java
```

## Result enum

```java
SUCCESS
RETRYABLE_ERROR
PERMANENT_ERROR
```

## Interface

```java
public interface ChannelProvider {
    CommunicationChannel channel();
    ProviderSendResult send(ProviderSendCommand command);
}
```

`ProviderSendCommand` содержит immutable delivery snapshot и
`idempotencyKey=messageId.toString()`; JPA entity provider не получает.

## Registry

Build `Map<CommunicationChannel, ChannelProvider>` from injected list.

Duplicate provider for same channel should fail at startup via duplicate key.

Missing provider should fail when requested with clear exception.

## Acceptance

```text
EMAIL -> MockEmailProvider
missing provider -> clear error
no switch in worker
```

---

# TASK 11 — Mock provider + every 10th retryable failure

## Новые файлы

```text
provider/mock/MockDeliveryBehavior.java
provider/mock/MockEmailProvider.java
```

## Config

```yaml
collectra:
  communication:
    mock:
      fail-every: 10
```

## Behavior

```text
1..9 SUCCESS
10 RETRYABLE_ERROR
11..19 SUCCESS
20 RETRYABLE_ERROR
```

## Success providerMessageId

```text
mock-email-<uuid>
```

## Permanent failure

Для tests можно дать scripted result; не зашивать business destination `permanent-error@example.com` в production component, если это можно избежать.

## Acceptance

Unit:

```text
call 10 retryable
call 20 retryable
failEvery=0 always success
```

---

# TASK 12 — MessageDeliveryWorker

## Новый файл

```text
communication/infrastructure/MessageDeliveryWorker.java
```

## DTO

```text
communication/infrastructure/MessageDeliveryRequested.java
```

```java
public record MessageDeliveryRequested(UUID messageId) {}
```

## Worker

```java
@RabbitListener(queues = CommunicationMessagingConfig.QUEUE)
public void handle(MessageDeliveryRequested event) {
    delivery.deliver(event.messageId());
}
```

Worker должен быть thin adapter.

## Не помещать сюда

```text
repository claim SQL
retry calculation
CampaignRun counters
template rendering
provider switch
```

---

# TASK 13 — MessageDeliveryService

## Новый файл

```text
communication/application/MessageDeliveryService.java
```

## Flow

```text
claim QUEUED -> PROCESSING, attemptCount++
if claim=0 return
load Message
provider = registry.get(channel)
provider.send(message)
apply result
```

## Transaction boundary

Не держать transaction во время provider.send.

### TX 1

```text
claim + commit
```

### outside transaction

```text
provider.send
```

### TX 2

```text
apply result + stats + commit
```

## Runtime exception

Provider unexpected exception:

```text
RETRYABLE_ERROR / PROVIDER_EXCEPTION
```

если нет явной permanent classification.

## Acceptance

```text
SUCCESS -> SENT
RETRYABLE -> RETRY_WAIT
PERMANENT -> FAILED
terminal/processing duplicate event -> no provider call
```

---

# TASK 14 — Retry policy

## Новые files

```text
communication/application/CommunicationRetryPolicy.java
communication/application/CommunicationRetryProperties.java
```

## Defaults

```text
maxAttempts=5
1m
5m
15m
30m
```

Prefer ISO-8601 durations in yaml:

```yaml
- PT1M
- PT5M
- PT15M
- PT30M
```

## Logic

If:

```text
attemptCount >= maxAttempts
```

on retryable provider result:

```text
FAILED
```

else:

```text
RETRY_WAIT
nextRetryAt=now+delay
retryCount++
```

## Acceptance

Boundary tests around attempt 1 and maxAttempts.

---

# TASK 15 — Retry scheduler/requeue

## Новые files

```text
communication/application/MessageRetryService.java
communication/infrastructure/MessageRetryScheduler.java
```

## Query

Find due:

```text
status=RETRY_WAIT
nextRetryAt<=now
```

batch size default:

```text
100
```

## Requeue atomically

```text
RETRY_WAIT -> QUEUED
```

Выбирать batch через PostgreSQL `FOR UPDATE SKIP LOCKED`, затем выполнять entity
transition. Это сохраняет auditing/version и исключает двойную обработку двумя
scheduler instances.

Only if transition success:

```text
append MESSAGE_DELIVERY_REQUESTED Outbox event
```

same transaction.

## Acceptance

```text
not due -> untouched
one due -> QUEUED + one outbox
concurrent schedulers -> one requeue + one outbox
```

---

# TASK 15B — Stale PROCESSING recovery

## Цель

Не оставлять Message навсегда в `PROCESSING`, если process остановился после claim
либо provider call, но до TX2.

## Новые/изменяемые файлы

```text
communication/application/MessageRecoveryService.java
communication/infrastructure/MessageRecoveryScheduler.java
MessageRepository.java
```

## Query

Выбирать rows:

```text
status=PROCESSING
processingStartedAt < now-processingTimeout
```

через `FOR UPDATE SKIP LOCKED`, batch size default `100`.

## Result

```text
attemptCount < maxAttempts -> RETRY_WAIT, nextRetryAt=now,
                              PROCESSING_TIMEOUT_RECOVERED, retryCount++
attemptCount >= maxAttempts -> FAILED,
                               PROCESSING_TIMEOUT_MAX_ATTEMPTS, failedCount++
```

После terminal recovery проверить CampaignRun completion. Recovery в `RETRY_WAIT`
не создаёт Outbox сам: обычный retry scheduler делает единственный requeue path.

## Acceptance

```text
fresh PROCESSING -> untouched
stale PROCESSING with attempts left -> RETRY_WAIT
stale PROCESSING at limit -> FAILED
two recovery schedulers -> one transition/counter update
```

---

# TASK 16 — Apply provider results + run statistics

## SUCCESS

Transaction:

```text
load PROCESSING Message
markSent
recordSent(runId)
completeIfDone
```

## RETRYABLE

If attempts remain:

```text
scheduleRetry
recordRetry(runId)
```

Do NOT:

```text
failedCount++
```

## RETRYABLE exhausted

```text
markFailed
recordFailed
completeIfDone
```

## PERMANENT

```text
markFailed
recordFailed
completeIfDone
```

## Important duplicate protection

Only a real `PROCESSING -> terminal/retry` transition is allowed to update stats.

If Message no longer PROCESSING, no counters changed.

---

# TASK 17 — Completion of CampaignRun

## Rule

```text
terminalRecipients = sentCount + failedCount + skippedCount
```

If:

```text
status == RUNNING
terminalRecipients == recipientCount
```

then:

```text
COMPLETED
completedAt = Instant.now(clock)
```

## Race consideration

Two terminal messages may finish concurrently.

Preferred simple implementation:

- atomic counter increments;
- after increment reload run;
- attempt conditional completion query if still RUNNING and formula matches.

Possible repository method:

```java
@Modifying
@Query("""
    update CampaignRun r
       set r.status = :completed,
           r.completedAt = :now
     where r.id = :runId
       and r.status = :running
       and r.sentCount + r.failedCount + r.skippedCount >= r.recipientCount
""")
int completeIfFinished(...);
```

Using `>=` protects against pathological duplicate counters while tests should prove duplicates are not generated.

## Acceptance

Last terminal delivery completes run exactly once.

---

# TASK 18 — Message read API

## Минимум

```http
GET /api/v1/messages/{id}
GET /api/v1/messages?campaignRunId={runId}
```

Optional filters:

```text
status
channel
customerId
campaignId
```

## DTO

Не возвращать JPA entity напрямую.

Response fields:

```text
id
campaignRunId
campaignRecipientId
customerId
channel
destination
status
attemptCount
providerMessageId
lastErrorCode
lastErrorMessage
nextRetryAt
sentAt
createdAt
```

Body/subject можно возвращать в detail endpoint; list DTO лучше держать компактным.

---

# TASK 19 — Integration tests

## Required scenarios

### A success

```text
recipient eligible
message created
outbox created
worker success
SENT
run sentCount=1
COMPLETED
```

### B paid before delivery

```text
prepare
payment/allocation
start delivery
SKIPPED/PAID
no message
skippedCount=1
COMPLETED
```

### C retry then success

```text
attempt1 retryable
RETRY_WAIT
retryCount=1
scheduler requeue
attempt2 success
SENT
failedCount=0
```

### D retry exhausted

```text
attempts=max
FAILED
failedCount=1
```

### E permanent

```text
PERMANENT_ERROR
FAILED
no nextRetryAt
```

### F duplicate Rabbit delivery

```text
same event twice
provider invoked once
sentCount=1
```

### G concurrent claim

```text
two calls claim same message
one wins
```

### H concurrent retry scan

```text
same RETRY_WAIT due
one requeue
one outbox
```

## Test provider

Integration tests MUST use deterministic scripted provider, not global `fail-every=10` counter.

---

# TASK 20 — Other mock channels

Только после EMAIL pipeline зелёный.

Добавить thin adapters:

```text
MockSmsProvider
MockWhatsAppProvider
MockTelegramProvider
MockInAppProvider
```

Они используют тот же:

```text
MockDeliveryBehavior
MessageDeliveryWorker
MessageDeliveryService
RetryPolicy
StatsService
Rabbit queue
```

## Важное ограничение

Current Campaign Core пока EMAIL-only.

Поэтому наличие providers не означает, что Campaign creation автоматически должен начать принимать все channels в этом же PR.

Расширение Campaign contact selection для phone/chat channels — отдельная следующая задача.

---

# Рекомендуемые PR slices

Чтобы не делать огромный PR:

```text
PR 1: Message domain + DB + CampaignRun counters
PR 2: CampaignDeliveryService + Message creation + Outbox route
PR 3: Worker + provider registry + MockEmail + success flow
PR 4: retry/backoff + scheduler
PR 5: stats/completion hardening + integration tests
PR 6: thin mock providers for remaining channels
```

Если проект удобнее вести одним feature branch — commits всё равно сохранять в этих логических slices.

---

# Итоговая простая архитектура

```text
CampaignRun
  -> CampaignRecipient
      -> Message
          -> shared Outbox
              -> RabbitMQ
                  -> one MessageDeliveryWorker
                      -> ChannelProviderRegistry
                          -> Provider
                      <- ProviderSendResult
                  -> Message state
                  -> CampaignRun counters
```

Это и есть граница v1. Всё, что выходит за неё, должно иметь отдельное обоснование.
