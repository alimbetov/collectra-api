# Communication / Delivery Core

> **Статус:** функциональная основа. Обязательные code-level уточнения и исправления
> после аудита зафиксированы в
> `docs/roadmap/communication-delivery-core-code-audit.md`. При противоречии
> приоритет имеет audit document.

## Цель

Реализовать минимальный и расширяемый контур доставки сообщений из `CampaignRecipient` без подключения реальных внешних провайдеров на первом этапе.

Базовый поток:

```text
CampaignRecipient
    -> eligibility recheck
    -> Message
    -> existing shared Outbox
    -> RabbitMQ
    -> MessageDeliveryWorker
    -> ChannelProvider
```

На первом этапе полностью реализуется EMAIL через mock provider. Остальные каналы
подключаются теми же тонкими adapters после стабилизации общего pipeline.

Поддерживаемые каналы:

- EMAIL
- SMS
- WHATSAPP
- TELEGRAM
- IN_APP

## Принципы реализации

1. Не создавать отдельный worker на каждый канал.
2. Использовать один `MessageDeliveryWorker` для всех сообщений.
3. Выбор канала делать через `ChannelProviderRegistry`.
4. Не создавать второй Outbox: использовать существующий `io.collectra.api.shared.outbox`.
5. Retry доставки сообщения отделять от retry публикации Outbox в RabbitMQ.
6. Не создавать generic workflow engine, generic query DSL или provider orchestration framework.
7. Перед постановкой сообщения на отправку обязательно выполнять eligibility recheck.
8. Повторная обработка RabbitMQ-сообщения не должна приводить к повторной успешной доставке уже отправленного Message.

---

## 1. Message

Создать сущность `Message` в модуле `communication`.

Минимальные поля:

```text
id UUID

tenantId UUID
campaignId UUID
campaignRunId UUID
campaignRecipientId UUID
customerId UUID
invoiceId UUID nullable

channel ENUM
destination VARCHAR
templateVersionId UUID
resolvedLocale VARCHAR
subject VARCHAR nullable
body TEXT

status ENUM
attemptCount INT
nextRetryAt TIMESTAMP nullable
processingStartedAt TIMESTAMP nullable

providerMessageId VARCHAR nullable
lastErrorCode VARCHAR nullable
lastErrorMessage TEXT nullable

createdAt TIMESTAMP
updatedAt TIMESTAMP
sentAt TIMESTAMP nullable
```

`subject` обязателен только для каналов, где он используется, например EMAIL.

В `Message` сохраняется уже подготовленный контент. Worker не должен повторно собирать campaign selection или пересчитывать текст сообщения.

---

## 2. Статусная модель

Использовать минимальную статусную модель:

```text
QUEUED
PROCESSING
RETRY_WAIT
SENT
FAILED
```

Отдельный `CREATED` не нужен: Message создаётся сразу готовым к постановке в доставку.

Допустимые переходы:

```text
QUEUED
   -> PROCESSING
        -> SENT
        -> RETRY_WAIT
        -> FAILED

RETRY_WAIT
   -> QUEUED

```

Terminal statuses:

- SENT
- FAILED

Повторно отправлять `SENT` нельзя.

---

## 3. Создание сообщения из CampaignRecipient

Перед созданием сообщения выполнить eligibility recheck существующей campaign-логикой.

```text
CampaignRecipient READY
    -> eligibility recheck
        -> eligible: create Message
        -> not eligible: CampaignRecipient SKIPPED
```

Пример для оплаченного invoice:

```text
CampaignRecipient.status = SKIPPED
CampaignRecipient.skipReason = PAID
```

В этом случае `Message` не создаётся.

### Idempotency создания

Для одного campaign recipient и канала не должно создаваться несколько одинаковых сообщений.

Минимальное решение:

```text
UNIQUE (campaign_recipient_id)
```

Не вводить отдельный idempotency framework на этом этапе.

---

## 4. Outbox

Не создавать новую таблицу или новый Outbox implementation.

Использовать существующие:

```text
io.collectra.api.shared.outbox.OutboxEvent
io.collectra.api.shared.outbox.OutboxService
io.collectra.api.shared.outbox.OutboxPublisher
io.collectra.api.shared.outbox.OutboxEventRouter
```

`Message` и `OutboxEvent` должны создаваться в одной DB-транзакции.

Для нового события использовать, например:

```text
aggregateType = MESSAGE
aggregateId = messageId
eventType = MESSAGE_DELIVERY_REQUESTED
```

Payload минимальный:

```json
{
  "messageId": "..."
}
```

Не дублировать в Outbox весь `Message`.

Существующий Outbox отвечает за надёжную публикацию события в RabbitMQ, включая broker retry.

---

## 5. Один MessageDeliveryWorker

Все каналы обрабатываются одним worker.

```text
RabbitMQ
   -> MessageDeliveryWorker
        -> load Message
        -> validate current status
        -> PROCESSING
        -> ChannelProviderRegistry.get(channel)
        -> provider.send(message)
        -> update Message status
```

Не создавать:

```text
EmailWorker
SmsWorker
WhatsAppWorker
TelegramWorker
InAppWorker
```

Это преждевременное усложнение.

Разделять workers по каналам можно позднее, если реально появятся разные:

- rate limits;
- SLA;
- очереди;
- throughput;
- provider-specific operational requirements.

---

## 6. ChannelProvider

Минимальный контракт:

```java
public interface ChannelProvider {
    CommunicationChannel channel();
    ProviderSendResult send(ProviderSendCommand command);
}
```

`ProviderSendCommand` — immutable DTO, содержащий `messageId`, channel,
destination, subject, body и `idempotencyKey=messageId`. JPA entity за границу
provider adapter не передаётся.

Результат:

```java
public record ProviderSendResult(
        ProviderResultStatus status,
        String providerMessageId,
        String errorCode,
        String errorMessage
) {}
```

```java
public enum ProviderResultStatus {
    SUCCESS,
    RETRYABLE_ERROR,
    PERMANENT_ERROR
}
```

Ожидаемые provider/business ошибки должны возвращаться как `ProviderSendResult`.

Не нужно строить сложную exception hierarchy для обычных provider-ответов.

---

## 7. ChannelProviderRegistry

Worker не должен содержать цепочку `if/switch` с логикой каждого канала.

Использовать простой registry:

```java
@Component
public class ChannelProviderRegistry {
    private final Map<CommunicationChannel, ChannelProvider> providers;

    public ChannelProvider get(CommunicationChannel channel) {
        ...
    }
}
```

На первом этапе все реализации mock.

---

## 8. Mock providers

Можно реализовать один общий configurable mock provider или отдельные тонкие mock providers.

Предпочтительный простой вариант:

```text
MockEmailProvider
MockSmsProvider
MockWhatsAppProvider
MockTelegramProvider
MockInAppProvider
```

Они могут использовать один общий `MockDeliveryBehavior`.

Это делает канал явным, но не дублирует механику ошибок.

### Dev-режим: ошибка каждый N-й вызов

Настройка:

```yaml
collectra:
  communication:
    mock:
      fail-every: 10
```

Поведение:

```text
1..9   -> SUCCESS
10     -> RETRYABLE_ERROR
11..19 -> SUCCESS
20     -> RETRYABLE_ERROR
```

`fail-every: 0` отключает автоматические ошибки.

### Deterministic test mode

Для integration tests должна быть возможность заранее задать последовательность результатов, например:

```text
RETRYABLE_ERROR
RETRYABLE_ERROR
SUCCESS
```

Это предпочтительнее, чем зависеть от глобального номера вызова в тестах.

---

## 9. Retry доставки

Retry публикации Outbox и retry доставки — разные уровни.

Существующий `shared.outbox` отвечает только за доставку события до RabbitMQ.

`Message` отвечает за retry вызова `ChannelProvider`.

Retry выполняется только для:

```text
RETRYABLE_ERROR
```

Для:

```text
PERMANENT_ERROR
```

сразу устанавливать:

```text
FAILED
```

Начальная политика:

```text
maxAttempts = 5
```

Backoff:

```text
attempt 1 -> +1 minute
attempt 2 -> +5 minutes
attempt 3 -> +15 minutes
attempt 4 -> +30 minutes
attempt 5 -> FAILED
```

Конфигурация:

```yaml
collectra:
  communication:
    retry:
      max-attempts: 5
      delays:
        - 1m
        - 5m
        - 15m
        - 30m
```

Использовать уже существующий application `Clock`, не вызывать `Instant.now()` напрямую в бизнес-логике.

---

## 10. Поведение при retryable error

```text
PROCESSING
   -> provider RETRYABLE_ERROR
   -> attemptCount уже увеличен при atomic claim
   -> RETRY_WAIT
   -> nextRetryAt = calculated backoff
```

Нужен простой scheduler/requeue service, который периодически выбирает due-сообщения:

```text
status = RETRY_WAIT
and nextRetryAt <= now
```

и переводит их обратно в `QUEUED`, одновременно создавая новый существующий Outbox event `MESSAGE_DELIVERY_REQUESTED`.

Не требуется отдельная retry queue topology на первом этапе.

---

## 11. Максимальное количество попыток

Если следующая попытка превышает `maxAttempts`:

```text
status = FAILED
nextRetryAt = null
```

Сохранить:

```text
lastErrorCode
lastErrorMessage
```

---

## 12. Permanent error

Для mock provider предусмотреть простой способ вызвать permanent error, например специальным destination либо test behavior.

Пример:

```text
permanent-error@example.com
```

Результат:

```text
PROCESSING
   -> PERMANENT_ERROR
   -> FAILED
```

Retry не выполняется.

---

## 13. Успешная доставка

Provider возвращает:

```text
SUCCESS
providerMessageId
```

Mock provider формирует ID, например:

```text
mock-email-{UUID}
```

Message обновляется:

```text
status = SENT
providerMessageId = ...
sentAt = now
nextRetryAt = null
lastErrorCode = null
lastErrorMessage = null
```

---

## 14. Защита от повторной обработки

RabbitMQ может доставить сообщение повторно.

Worker должен быть идемпотентным на уровне `Message`.

Если Message уже:

```text
SENT
FAILED
```

worker ничего не отправляет.

Переход `QUEUED -> PROCESSING` должен выполняться атомарно, чтобы два consumer не отправили одно сообщение одновременно.

Допустимые простые варианты:

- optimistic locking (`@Version`);
- conditional update по status.

Предпочтение — conditional update, если он хорошо ложится на текущий repository style.

Не вводить distributed lock.

---

## 15. API

Минимальный API для наблюдаемости:

```http
GET /api/v1/messages/{id}
GET /api/v1/messages?campaignRunId={runId}&page=0&size=50
```

Минимальные фильтры списка:

- campaignId
- campaignRunId
- customerId
- channel
- status

Не создавать generic query DSL.

Отдельный ручной resend endpoint не нужен на первом этапе.

---

## 16. Tests

### Successful delivery

```text
CampaignRecipient
-> eligibility recheck
-> Message QUEUED
-> Outbox
-> RabbitMQ
-> MessageDeliveryWorker
-> MockProvider SUCCESS
-> SENT
```

Проверить:

```text
status = SENT
providerMessageId != null
sentAt != null
```

### Eligibility before delivery creation

```text
CampaignRecipient READY
-> invoice paid
-> execute delivery
```

Ожидается:

```text
CampaignRecipient = SKIPPED
skipReason = PAID
Message отсутствует
```

### Retryable error

```text
PROCESSING
-> RETRYABLE_ERROR
-> RETRY_WAIT
```

Проверить:

```text
attemptCount incremented
nextRetryAt != null
```

После due retry:

```text
RETRY_WAIT
-> QUEUED
-> new Outbox event
-> worker
-> SUCCESS
-> SENT
```

### Retry exhausted

Provider всегда возвращает `RETRYABLE_ERROR`.

После `maxAttempts`:

```text
status = FAILED
```

### Permanent error

```text
PERMANENT_ERROR
-> FAILED
```

без retry.

### Idempotency

Повторно обработать один и тот же RabbitMQ event.

Ожидается одна фактическая успешная доставка.

---

## 17. Что не входит в этот этап

Не реализовывать сейчас:

- SMTP / SendGrid / SES;
- SMS provider;
- WhatsApp Business API;
- Telegram Bot API;
- push provider;
- delivery receipts/webhooks;
- open/click tracking;
- provider failover;
- provider balancing;
- per-channel RabbitMQ queues;
- per-channel workers;
- rate limiting providers;
- bulk provider APIs;
- distributed locks;
- generic workflow engine;
- generic retry framework;
- generic query DSL.

---

## 18. Definition of Done

Задача завершена, когда:

1. `CampaignRecipient` после eligibility recheck может создать `Message`.
2. Для уже оплаченного invoice Message не создаётся.
3. Message создаётся сразу в `QUEUED`.
4. Message и существующий Outbox event сохраняются транзакционно.
5. Существующий Outbox публикует `MESSAGE_DELIVERY_REQUESTED` в RabbitMQ.
6. Один `MessageDeliveryWorker` обрабатывает все каналы.
7. Worker выбирает provider через `ChannelProviderRegistry`.
8. EMAIL имеет mock provider; остальные thin mock adapters добавляются после
   зелёного EMAIL pipeline.
9. `fail-every: 10` моделирует retryable failure в dev mode.
10. Integration tests могут задавать deterministic mock sequence.
11. `RETRYABLE_ERROR` переводит Message в `RETRY_WAIT`.
12. Retry scheduler повторно ставит due Message в доставку через существующий Outbox.
13. После исчерпания попыток Message становится `FAILED`.
14. `PERMANENT_ERROR` сразу приводит к `FAILED`.
15. `SUCCESS` приводит к `SENT` и сохраняет `providerMessageId`.
16. Повторный RabbitMQ delivery не вызывает повторную отправку `SENT` сообщения.
17. Основные сценарии покрыты unit/integration tests.

## Итоговая архитектура v1

```text
CampaignRecipient
       |
       | eligibility recheck
       v
     Message
       |
       | same DB transaction
       v
existing OutboxEvent
       |
       v
OutboxPublisher
       |
       v
    RabbitMQ
       |
       v
MessageDeliveryWorker
       |
       v
ChannelProviderRegistry
       |
       +--> MockEmailProvider
       +--> MockSmsProvider
       +--> MockWhatsAppProvider
       +--> MockTelegramProvider
       +--> MockInAppProvider
```

Это целевая минимальная архитектура для первого production-ready vertical slice доставки.
