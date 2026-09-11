# Review: Communication / Delivery Core

## Итог

ТЗ можно реализовывать без отдельной инфраструктуры на каждый канал. Для v1 оптимальная схема — один общий delivery pipeline, один worker и простые channel adapters.

Ключевая модель:

```text
CampaignRun
   |
   +--> CampaignRecipient
            |
            v
          Message
            |
            v
      existing Outbox
            |
            v
         RabbitMQ
            |
            v
   MessageDeliveryWorker
            |
            v
   ChannelProviderRegistry
      |      |      |
    EMAIL   SMS   WHATSAPP ...
            |
            v
     ProviderSendResult
            |
            v
       Message status
            |
            v
   CampaignRun counters
```

## 1. Один worker для всех каналов

Не создавать отдельные:

```text
EmailWorker
SmsWorker
WhatsAppWorker
TelegramWorker
InAppWorker
```

Оставить один:

```text
MessageDeliveryWorker
```

Worker получает `messageId`, читает `Message`, определяет `channel`, выбирает `ChannelProvider` и выполняет один и тот же lifecycle.

Разделение workers понадобится только если реальные каналы начнут существенно отличаться по rate limit, SLA, throughput или topology очередей.

## 2. Канал не должен управлять CampaignRun

`ChannelProvider` должен быть максимально тупым адаптером:

```java
ProviderSendResult send(Message message);
```

Он только сообщает результат:

```text
SUCCESS
RETRYABLE_ERROR
PERMANENT_ERROR
```

Provider не должен напрямую обновлять `CampaignRun`, `CampaignRecipient`, retry scheduler или Outbox.

Это делает adapters заменяемыми и не связывает Email/SMS/WhatsApp с campaign domain.

## 3. Общая задача собирает результат доставки

После обработки provider worker обновляет `Message`, а затем отражает результат в общей задаче `CampaignRun`.

Минимальные агрегированные показатели:

```text
totalCount
queuedCount
processingCount
sentCount
failedCount
retryWaitCount
skippedCount
```

`skippedCount` относится к `CampaignRecipient`, потому что message для такого recipient может вообще не создаваться.

Для первого этапа достаточно как минимум:

```text
sentCount
failedCount
retryWaitCount
```

если остальные показатели уже можно получить из campaign preparation.

## 4. Как обновлять counters без усложнения

Не создавать отдельную event bus / analytics pipeline для counters.

После успешного изменения `Message` выполнять маленький application service:

```java
campaignDeliveryStats.recordTransition(
        campaignRunId,
        oldStatus,
        newStatus
);
```

Он атомарно обновляет нужные counters в `CampaignRun`.

Пример:

```text
PROCESSING -> SENT
sentCount += 1
processingCount -= 1
```

```text
PROCESSING -> RETRY_WAIT
retryWaitCount += 1
processingCount -= 1
```

```text
PROCESSING -> FAILED
failedCount += 1
processingCount -= 1
```

```text
RETRY_WAIT -> QUEUED
retryWaitCount -= 1
queuedCount += 1
```

Обновление должно выполняться только при реальном успешном переходе статуса Message.

## 5. Защита counters от двойного инкремента

RabbitMQ допускает повторную delivery, поэтому нельзя делать:

```text
получили event -> sentCount++
```

Сначала должен произойти атомарный transition `Message`.

Например:

```sql
update messages
set status = 'SENT'
where id = :id
  and status = 'PROCESSING'
```

Только если affected rows = 1, обновлять counters.

Если affected rows = 0, событие уже обработано или Message находится в другом статусе — counters не меняются.

Это позволяет не вводить отдельную таблицу deduplication.

## 6. Важное различие: попытка и итог

`retryCount`/`attemptCount` — техническая метрика.

`sentCount` и `failedCount` — итоговые показатели.

При `RETRYABLE_ERROR` нельзя увеличивать `failedCount`.

Пример:

```text
attempt 1 -> RETRYABLE_ERROR
attempt 2 -> RETRYABLE_ERROR
attempt 3 -> SUCCESS
```

Итог:

```text
sentCount = 1
failedCount = 0
attemptCount = 3
```

## 7. Когда CampaignRun считается завершённым

Не нужно вводить отдельный orchestration engine.

CampaignRun можно считать завершённым, когда для него больше нет сообщений в активных статусах:

```text
QUEUED
PROCESSING
RETRY_WAIT
```

То есть:

```text
activeCount == 0
```

и все recipients находятся в terminal state (`SENT`, `FAILED`, `SKIPPED`, `CANCELLED` через соответствующие Message/Recipient состояния).

Для v1 completion можно проверять после terminal transition сообщения или периодическим lightweight reconciliation job.

Предпочтение для начала: проверять после terminal transition, без отдельного сложного coordinator.

## 8. Не создавать DeliveryAttempt в v1

В `Message` достаточно:

```text
attemptCount
nextRetryAt
providerMessageId
lastErrorCode
lastErrorMessage
```

Отдельную таблицу `DeliveryAttempt` стоит добавлять только при реальной потребности в полном аудите каждой попытки, стоимости отправки или хранении provider response history.

## 9. Не создавать новый Outbox

В репозитории уже существует `shared.outbox`, включая claim, retry, lock/recovery, routing и RabbitMQ publisher.

Communication Core должен использовать его, а не создавать `communication_outbox`.

`Message + OutboxEvent` сохраняются в одной транзакции.

## 10. Retry: два разных уровня

Не смешивать:

```text
Outbox retry
```

и

```text
Message delivery retry
```

Outbox retry отвечает за то, чтобы событие дошло до RabbitMQ.

Message retry отвечает за повторный вызов Email/SMS/WhatsApp provider.

Это две разные причины ошибки.

## 11. Mock fault injection

Dev/staging режим:

```yaml
collectra:
  communication:
    mock:
      fail-every: 10
```

Каждый десятый вызов возвращает:

```text
RETRYABLE_ERROR
```

Для тестов использовать deterministic sequence:

```text
RETRYABLE_ERROR -> RETRYABLE_ERROR -> SUCCESS
```

Так тесты не зависят от глобального счётчика.

## 12. Что не добавлять сейчас

Чтобы не усложнять v1, не нужны:

- отдельный worker на каждый канал;
- отдельная RabbitMQ queue на каждый канал;
- отдельная таблица DeliveryAttempt;
- distributed lock;
- generic workflow engine;
- provider orchestration framework;
- generic retry framework;
- отдельная analytics/event pipeline для counters;
- provider balancing;
- fallback EMAIL -> SMS;
- rate limiter до появления реального provider contract.

## 13. Минимальный набор компонентов

```text
communication/
  Message
  MessageStatus
  CommunicationChannel
  MessageRepository

  MessageService
  MessageDeliveryWorker
  MessageRetryScheduler
  CampaignDeliveryStatsService

  ChannelProvider
  ChannelProviderRegistry
  ProviderSendResult
  ProviderResultStatus
  CommunicationRetryPolicy

  mock/
    MockDeliveryBehavior
    MockEmailProvider
    MockSmsProvider
    MockWhatsAppProvider
    MockTelegramProvider
    MockInAppProvider
```

Плюс существующий:

```text
shared.outbox
```

## 14. Рекомендуемый vertical slice

Первую реализацию лучше делать насквозь только на одном mock-канале EMAIL, но общую enum/registry модель сразу оставить для остальных каналов.

Порядок:

1. Message + migration.
2. `CampaignRecipient -> eligibility recheck -> Message`.
3. `Message + OutboxEvent` в одной транзакции.
4. RabbitMQ routing.
5. один `MessageDeliveryWorker`.
6. `MockEmailProvider`.
7. `SUCCESS -> SENT`.
8. counters в CampaignRun.
9. `RETRYABLE_ERROR -> RETRY_WAIT -> retry -> SENT`.
10. `PERMANENT_ERROR -> FAILED`.
11. duplicate Rabbit delivery test.
12. добавить остальные mock providers как тонкие adapters.

Так мы проверим весь delivery pipeline на одном канале и не будем писать пять копий незавершённой логики.

## 15. Definition of Done после ревью

Минимальный DoD:

1. один `MessageDeliveryWorker` обслуживает все каналы;
2. `ChannelProvider` не содержит campaign orchestration;
3. используется существующий `shared.outbox`;
4. Message создаётся только после eligibility recheck;
5. duplicate Rabbit delivery не вызывает повторной успешной отправки;
6. `SUCCESS`, `RETRYABLE_ERROR`, `PERMANENT_ERROR` корректно меняют status Message;
7. retry/backoff работает;
8. каждый десятый mock-вызов может вернуть retryable error;
9. deterministic retry tests присутствуют;
10. terminal transitions обновляют общие counters CampaignRun;
11. retryable error не увеличивает `failedCount`;
12. CampaignRun может определить, что активных доставок больше нет;
13. никаких отдельных per-channel workers/queues/frameworks в v1 не создаётся.

## Вывод

После ревью архитектура остаётся простой:

```text
одна CampaignRun
    -> много CampaignRecipient
        -> Message
            -> один Outbox
                -> одна delivery pipeline
                    -> один Worker
                        -> разные ChannelProvider
                            -> результат обратно в Message
                                -> агрегатные counters CampaignRun
```

Это достаточно для первого production-oriented delivery core и оставляет понятные точки расширения без преждевременного усложнения.