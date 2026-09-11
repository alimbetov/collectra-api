# Slice 2 — Message processing core

Status: NEXT  
Depends on: Slice 1 — Message persistence and invariants  
Suggested branch: `feat/message-processing-core`

## 1. Цель

Реализовать application-layer обработку уже сохранённого `Message`: безопасный claim, вызов provider-neutral `DeliveryGateway`, фиксацию `SENT/RETRY_WAIT/FAILED`, deterministic retry и recovery зависших `PROCESSING`.

После Slice 2 реальный KumoMTA ещё не нужен. Для worker достаточно тестовой реализации `DeliveryGateway`.

## 2. Текущий baseline

Уже существует и не переimplementируется:

- `communication.domain.Message`;
- `MessageStatus`, `CommunicationChannel`;
- переходы `QUEUED -> PROCESSING -> SENT/RETRY_WAIT/FAILED`;
- `RETRY_WAIT -> QUEUED`;
- поля `attemptCount`, `processingStartedAt`, `nextRetryAt`, `providerMessageId`, `lastError*`, `sentAt`;
- tenant-scoped `MessageRepository`;
- `CampaignRun` counters;
- application `Clock`.

## 3. Scope

Добавить:

```text
io.collectra.api.communication.application
├── DeliveryGateway.java
├── DeliveryCommand.java
├── DeliveryResult.java
├── DeliveryFailureKind.java
├── MessageDeliverySnapshot.java
├── MessageStateService.java
├── MessageDeliveryWorker.java
├── MessageRetryPolicy.java
├── MessageRecoveryService.java
└── MessageRetryDispatcher.java
```

Расширить:

```text
communication.infrastructure.MessageRepository
campaign.domain.CampaignRun
campaign.infrastructure.CampaignRunRepository   # если lock lookup отсутствует
communication.domain.Message                    # только recovery intent method, если нужен
```

## 4. Claim Message

`MessageRepository` должен иметь tenant-scoped pessimistic lock:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        select m
        from Message m
        where m.id = :messageId
          and m.tenantId = :tenantId
        """)
Optional<Message> findLockedByIdAndTenantId(
        @Param("tenantId") UUID tenantId,
        @Param("messageId") UUID messageId);
```

`findById()` без tenant id в worker path не использовать.

### Claim rules

`MessageStateService.begin(tenantId, messageId)`:

1. открыть короткую transaction;
2. загрузить `Message` с `PESSIMISTIC_WRITE`;
3. если `QUEUED` — вызвать `message.beginAttempt(clock.instant())`;
4. вернуть immutable snapshot;
5. commit transaction;
6. если статус `PROCESSING`, `RETRY_WAIT`, `SENT` или `FAILED` — ничего не начинать повторно, вернуть empty result.

Рекомендуемая сигнатура:

```java
@Transactional
public Optional<MessageDeliverySnapshot> begin(UUID tenantId, UUID messageId)
```

Snapshot должен содержать только данные, нужные provider call:

```java
public record MessageDeliverySnapshot(
        UUID messageId,
        UUID tenantId,
        UUID campaignRunId,
        CommunicationChannel channel,
        String destination,
        String subject,
        String body,
        int attemptCount) {}
```

## 5. DeliveryGateway contract

Application layer не знает KumoMTA/SMTP/HTTP DTO.

```java
public interface DeliveryGateway {
    DeliveryResult deliver(DeliveryCommand command);
}
```

```java
public record DeliveryCommand(
        UUID messageId,
        UUID tenantId,
        CommunicationChannel channel,
        String destination,
        String subject,
        String body) {}
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

Provider-specific exceptions не должны выходить за infrastructure adapter boundary.

## 6. MessageStateService transitions

Методы:

```java
Optional<MessageDeliverySnapshot> begin(UUID tenantId, UUID messageId);
void markSent(UUID tenantId, UUID messageId, String providerMessageId);
void scheduleRetry(UUID tenantId, UUID messageId, Instant nextRetryAt,
                   String errorCode, String errorMessage);
void markFailed(UUID tenantId, UUID messageId,
                String errorCode, String errorMessage);
```

Каждый метод:

- `@Transactional`;
- tenant-scoped lookup;
- row lock;
- вызывает domain method `Message`, не пишет `status` напрямую.

## 7. Transaction boundary

Критическое правило:

```text
TX 1
lock Message
QUEUED -> PROCESSING
commit

NO DB TRANSACTION
DeliveryGateway.deliver(...)

TX 2
lock Message
PROCESSING -> SENT / RETRY_WAIT / FAILED
commit
```

Нельзя держать DB row lock во время network call.

## 8. MessageDeliveryWorker

Алгоритм:

```text
begin()
  |
  +-- empty -> return (duplicate/redelivery/not claimable)
  |
  v
DeliveryGateway.deliver()
  |
  +-- Accepted -> markSent()
  |
  +-- RETRYABLE and attempts remain -> scheduleRetry()
  |
  +-- PERMANENT or attempts exhausted -> markFailed()
```

Worker не должен:

- сам менять JPA entity status;
- управлять RabbitMQ;
- рендерить template;
- обращаться к CampaignRecipient/Customer;
- держать transaction вокруг provider call.

## 9. Retry policy

Первая версия фиксированная:

```text
attempt 1 -> +1 minute
attempt 2 -> +10 minutes
attempt 3 -> +1 hour
attempt 4 -> terminal FAILED
```

```java
@Component
public class MessageRetryPolicy {
    public static final int MAX_ATTEMPTS = 4;

    public boolean exhausted(int attemptCount) { ... }
    public Instant nextRetryAt(int attemptCount, Instant now) { ... }
}
```

Retry рассчитывается от `clock.instant()`.

`Message.attemptCount` — source of truth. Rabbit header retry count не использовать как business state.

## 10. Recovery stale PROCESSING

Проблема:

```text
QUEUED -> PROCESSING -> process crash
```

после restart сообщение не должно навсегда остаться `PROCESSING`.

Configuration:

```yaml
collectra:
  communication:
    processing-timeout: 5m
    recovery-batch-size: 100
```

Repository должен уметь выбирать bounded set:

```text
status = PROCESSING
processing_started_at < now - processingTimeout
```

Для первой реализации допустима bounded page + per-row locked transition. Если несколько replicas запускают recovery одновременно, preferred PostgreSQL strategy — `FOR UPDATE SKIP LOCKED`.

Recovery transition:

```text
stale PROCESSING
  +-- attempts < MAX_ATTEMPTS -> RETRY_WAIT
  +-- attempts >= MAX_ATTEMPTS -> FAILED
```

Error code для interrupted processing:

```text
PROCESSING_TIMEOUT
```

Если нужен новый domain method, он должен выражать intent, например `recoverInterruptedAttempt(...)`, а не позволять arbitrary setStatus.

## 11. Retry dispatcher

Выбирает due records:

```text
status = RETRY_WAIT
next_retry_at <= now
```

и делает только:

```text
lock
message.requeue()
commit
```

Публикация нового `MESSAGE_DELIVERY_REQUESTED` будет полноценно подключена в Slice 3. Slice 2 должен подготовить dispatcher API так, чтобы broker-specific код туда не попал.

## 12. CampaignRun counters

В этом slice допускается добавить domain methods:

```java
messageSent();
messageFailed();
messageRetryScheduled();
```

Но atomic integration counters + completion окончательно закрывается Slice 6.

До Slice 6 запрещено создавать альтернативные counters вне `CampaignRun`.

## 13. Ошибки и idempotency

- повторный `begin()` для уже `PROCESSING` не увеличивает `attemptCount`;
- повторный broker/event вызов для `SENT/FAILED` является no-op;
- provider accepted result применяется только к `PROCESSING`;
- late provider result для message, уже ушедшего из `PROCESSING`, не должен тихо переписывать status;
- tenant mismatch должен выглядеть как not found, а не раскрывать существование чужого message;
- `lastErrorMessage` остаётся bounded и sanitised domain rule.

## 14. Database changes

Новая migration не нужна, если текущих колонок/индексов достаточно.

Перед реализацией проверить наличие индексов для:

```text
(status, processing_started_at)
(status, next_retry_at)
(tenant_id, campaign_run_id)
```

Если первых двух нет и recovery/retry query приводит к scan — добавить отдельную Liquibase migration в этом PR.

## 15. Tests

### Unit

`MessageRetryPolicyTest`

- attempt 1 -> +1m;
- attempt 2 -> +10m;
- attempt 3 -> +1h;
- attempt 4 exhausted.

`MessageDeliveryWorkerTest`

- accepted -> sent;
- retryable -> retry;
- permanent -> failed;
- exhausted retryable -> failed;
- empty claim -> provider не вызывается.

### PostgreSQL integration

`MessageStateServiceIntegrationTest`

- QUEUED -> PROCESSING;
- `attemptCount` increment once;
- second begin no-op;
- tenant isolation;
- SENT/FAILED terminal behavior.

`MessageConcurrencyIntegrationTest`

- два concurrent `begin()` одного message;
- ровно один получает snapshot;
- attemptCount становится 1.

`MessageRecoveryIntegrationTest`

- stale PROCESSING -> RETRY_WAIT;
- exhausted stale -> FAILED;
- fresh PROCESSING untouched.

`MessageRetryDispatcherIntegrationTest`

- due RETRY_WAIT -> QUEUED;
- future RETRY_WAIT untouched.

### Architecture

- `communication.domain` не зависит от application/infrastructure;
- application не зависит от `infrastructure.kumomta`;
- KumoMTA classes отсутствуют в Slice 2.

## 16. Порядок реализации

1. repository lock methods;
2. `Delivery*` contracts;
3. `MessageDeliverySnapshot`;
4. `MessageStateService`;
5. `MessageRetryPolicy`;
6. `MessageDeliveryWorker`;
7. recovery query/service;
8. retry dispatcher;
9. missing indexes migration only if required;
10. unit/integration/concurrency/architecture tests;
11. `mvn verify`.

## 17. Out of scope

- RabbitMQ listener/exchange;
- Outbox routing for delivery;
- real KumoMTA client;
- template rendering/materialization;
- attachments/PDF/QR;
- public API;
- SMS/WhatsApp/Telegram/In-App.

## 18. Definition of Done

Slice 2 готов, если:

- `QUEUED` atomically claims only one worker;
- duplicate claim не создаёт вторую attempt;
- provider call идёт вне DB transaction;
- success -> `SENT` + provider id;
- retryable -> `RETRY_WAIT` + deterministic `nextRetryAt`;
- permanent/exhausted -> `FAILED`;
- stale `PROCESSING` recoverable;
- due retry можно вернуть в `QUEUED`;
- tenant isolation соблюдён;
- concurrency test доказал single claim;
- `mvn verify` green.
