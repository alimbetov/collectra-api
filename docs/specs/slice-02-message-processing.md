# Slice 2 — Message processing

Status: NEXT

Roadmap: `docs/roadmap/backend-mvp-roadmap.md`

## Цель

Добавить безопасную обработку уже сохранённых `Message` без привязки к KumoMTA.

После этой задачи система должна уметь:

- взять сообщение в обработку только один раз;
- перевести его `QUEUED -> PROCESSING`;
- зафиксировать `SENT`, `RETRY_WAIT` или `FAILED`;
- планировать повторную попытку;
- восстановить сообщение, зависшее в `PROCESSING` после падения worker;
- работать одинаково при одном и нескольких экземплярах приложения.

## Что уже есть

Не реализуем повторно:

- `Message`;
- `MessageStatus`;
- `CommunicationChannel`;
- `attemptCount`;
- `processingStartedAt`;
- `nextRetryAt`;
- `providerMessageId`;
- `beginAttempt(...)`;
- `markSent(...)`;
- `scheduleRetry(...)`;
- `markFailed(...)`;
- `requeue()`;
- `Clock`;
- базовый `MessageRepository`.

## Что нужно сделать

### 1. Блокировка Message

Расширить `MessageRepository` tenant-scoped методом с `PESSIMISTIC_WRITE`.

Пример целевой сигнатуры:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        select m
        from Message m
        where m.id = :id
          and m.tenantId = :tenantId
        """)
Optional<Message> findLockedByIdAndTenantId(
        UUID id,
        UUID tenantId);
```

Цель: два worker не должны одновременно начать обработку одного сообщения.

### 2. MessageStateService

Добавить application service:

```text
io.collectra.api.communication.application.MessageStateService
```

Ответственность:

```text
begin()
sent()
retry()
fail()
```

Каждая операция выполняется в короткой транзакции и загружает `Message` под lock.

`begin()` должен:

```text
QUEUED
  -> beginAttempt(clock.instant())
  -> PROCESSING
```

и вернуть snapshot данных, необходимых для отправки.

### 3. DeliveryGateway

Добавить простой provider-independent интерфейс:

```java
public interface DeliveryGateway {
    DeliveryResult deliver(DeliveryCommand command);
}
```

Минимальный command:

```java
public record DeliveryCommand(
        UUID messageId,
        UUID tenantId,
        CommunicationChannel channel,
        String destination,
        String subject,
        String body) {}
```

Результат:

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

KumoMTA в этом PR не подключаем.

### 4. Retry policy

Добавить `MessageRetryPolicy`.

Для первой версии достаточно:

```text
1-я ошибка -> retry через 1 минуту
2-я ошибка -> retry через 10 минут
3-я ошибка -> retry через 1 час
4-я ошибка -> FAILED
```

Все расчёты времени должны использовать `Clock`.

### 5. MessageDeliveryWorker

Добавить orchestration service:

```text
MessageDeliveryWorker
```

Логика:

```text
MessageStateService.begin()
        |
        v
DeliveryGateway.deliver()
        |
        +--> Accepted
        |      -> MessageStateService.sent()
        |
        +--> RETRYABLE
        |      -> MessageStateService.retry()
        |
        +--> PERMANENT / attempts exhausted
               -> MessageStateService.fail()
```

## Важное правило транзакций

Не держать транзакцию PostgreSQL во время вызова внешнего provider.

Правильно:

```text
TX 1
lock Message
QUEUED -> PROCESSING
commit

external provider call

TX 2
lock Message
PROCESSING -> SENT / RETRY_WAIT / FAILED
commit
```

## Recovery

Нужно обработать случай, когда приложение упало после перехода в `PROCESSING`.

Добавить `MessageRecoveryService`.

Минимальная логика:

```text
PROCESSING
processingStartedAt < now - timeout
        |
        +--> попытки ещё есть -> RETRY_WAIT
        |
        +--> попытки закончились -> FAILED
```

Для первой версии достаточно bounded batch/page. Не нужен сложный distributed scheduler.

## Retry dispatcher

Добавить обработку сообщений:

```text
RETRY_WAIT
nextRetryAt <= now
        -> requeue()
        -> QUEUED
```

Отправку письма этот код не выполняет. Он только возвращает сообщение в очередь обработки.

## CampaignRun counters

При успешной отправке / terminal failure / retry должны корректно обновляться существующие counters `CampaignRun`.

Минимально нужны domain methods вместо setters:

```java
messageSent();
messageFailed();
messageRetryScheduled();
```

Повторная доставка одного и того же broker event не должна повторно увеличивать counters.

## Что не входит в Slice 2

Не делать в этом PR:

- KumoMTA client;
- SMTP/HTTP интеграцию;
- новую RabbitMQ архитектуру;
- template rendering;
- PDF/QR;
- attachments;
- SMS/WhatsApp/Telegram;
- новый универсальный retry framework.

## Тесты

Минимальный обязательный набор:

```text
MessageStateServiceIntegrationTest
- QUEUED -> PROCESSING
- tenant isolation
- повторный begin не создаёт вторую попытку

MessageDeliveryWorkerTest
- Accepted -> SENT
- RETRYABLE -> RETRY_WAIT
- PERMANENT -> FAILED
- max attempts -> FAILED

MessageRetryPolicyTest
- 1m / 10m / 1h

MessageRecoveryIntegrationTest
- старый PROCESSING восстанавливается
- свежий PROCESSING не трогаем

MessageConcurrencyIntegrationTest
- два concurrent worker
- сообщение забирает только один
```

## Definition of Done

Slice считается готовым, когда:

- одно сообщение нельзя одновременно обработать двумя worker;
- сетевой вызов не выполняется внутри DB-транзакции;
- retry deterministic и покрыт тестами;
- stuck `PROCESSING` можно восстановить;
- tenant isolation соблюдён;
- terminal errors сохраняются;
- `CampaignRun` counters не дублируются;
- `mvn verify` проходит успешно.

После этого следующий отдельный PR: **KumoMTA email adapter**.
