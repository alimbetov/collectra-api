# Slice 2 — Message processing core

Status: NEXT

Depends on: Slice 1 — Message persistence and invariants (merged)

Suggested branch: `feat/message-processing-core`

## Цель

Добавить безопасную provider-independent обработку уже сохранённых `Message`: atomic claim, отправку через `DeliveryGateway`, retry/fail transitions и восстановление зависших `PROCESSING`.

## Уже есть

Не реализуем повторно:

- `Message`, `MessageStatus`, `CommunicationChannel`;
- `attemptCount`, `processingStartedAt`, `nextRetryAt`, `providerMessageId`;
- `beginAttempt`, `markSent`, `scheduleRetry`, `markFailed`, `requeue`;
- application `Clock`;
- базовый tenant-scoped `MessageRepository`;
- counters в `CampaignRun`.

## Scope

### 1. Atomic claim

Расширить `MessageRepository` tenant-scoped pessimistic lock lookup:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        select m
        from Message m
        where m.id = :id
          and m.tenantId = :tenantId
        """)
Optional<Message> findLockedByIdAndTenantId(UUID id, UUID tenantId);
```

Один `Message` не должен одновременно перейти в `PROCESSING` двумя worker-ами.

### 2. Application port

Добавить:

```text
DeliveryGateway
DeliveryCommand
DeliveryResult
DeliveryFailureKind
```

Минимальный контракт:

```java
public interface DeliveryGateway {
    DeliveryResult deliver(DeliveryCommand command);
}
```

KumoMTA здесь не подключается.

### 3. MessageStateService

Добавить короткие transactional операции:

```text
begin(tenantId, messageId)
sent(tenantId, messageId, providerMessageId)
retry(tenantId, messageId, nextRetryAt, code, message)
fail(tenantId, messageId, code, message)
```

`begin()` должен вернуть immutable snapshot данных, необходимых provider call.

### 4. MessageRetryPolicy

Начальная политика:

```text
attempt 1 -> +1 minute
attempt 2 -> +10 minutes
attempt 3 -> +1 hour
attempt 4+ -> FAILED
```

Расчёт времени — через `Clock`.

### 5. MessageDeliveryWorker

Основной поток:

```text
lock + QUEUED -> PROCESSING + commit
        -> DeliveryGateway.deliver()
        -> lock
            -> SENT
            -> RETRY_WAIT
            -> FAILED
        -> commit
```

Внешний provider call всегда выполняется вне DB-транзакции.

### 6. Recovery

Добавить `MessageRecoveryService` для stale сообщений:

```text
PROCESSING + processingStartedAt < threshold
  -> RETRY_WAIT
  -> или FAILED при exhausted attempts
```

Обработка bounded batch/page. Не нужен отдельный distributed scheduling framework.

### 7. Retry dispatcher

Добавить bounded обработку:

```text
RETRY_WAIT + nextRetryAt <= now
  -> lock
  -> requeue()
  -> QUEUED
```

Сам dispatcher provider не вызывает. Публикация RabbitMQ/Outbox события оформляется в Slice 3.

### 8. CampaignRun counters

Подготовить безопасное обновление существующих counters при реальных transitions:

```text
messageSent()
messageFailed()
messageRetryScheduled()
```

Повторный вызов для уже terminal/неподходящего state не должен double-count counters.

## Инварианты и правила

- tenant id обязателен в worker/state-service lookup;
- entity `Message` остаётся единственным владельцем status transitions;
- DB lock не держится во время provider call;
- `attemptCount` увеличивается только при реальном `QUEUED -> PROCESSING`;
- terminal message нельзя claim повторно;
- retry schedule хранится в PostgreSQL;
- recovery не должен напрямую SQL-обновлять status в обход domain methods.

## Не входит

- KumoMTA;
- RabbitMQ listener/exchange;
- template rendering;
- materialization;
- PDF/QR/attachments;
- SMS/WhatsApp/Telegram;
- универсальный retry framework.

## Тесты

Обязательные:

```text
MessageStateServiceIntegrationTest
- QUEUED -> PROCESSING
- duplicate begin безопасен
- tenant isolation

MessageDeliveryWorkerTest
- Accepted -> SENT
- RETRYABLE -> RETRY_WAIT
- PERMANENT -> FAILED
- exhausted -> FAILED

MessageRetryPolicyTest
- 1m / 10m / 1h

MessageRecoveryIntegrationTest
- stale PROCESSING восстанавливается
- fresh PROCESSING не меняется

MessageConcurrencyIntegrationTest
- два concurrent claim
- только один beginAttempt

ArchitectureTest
- communication.domain не зависит от infrastructure/provider packages
```

## Definition of Done

- claim одного сообщения атомарен;
- concurrent/duplicate processing не создаёт вторую попытку;
- network call не выполняется под row lock;
- success/retry/failure корректно сохраняются;
- stale `PROCESSING` имеет recovery path;
- counters не double-count на повторной обработке;
- KumoMTA dependency отсутствует;
- `mvn verify` зелёный.
