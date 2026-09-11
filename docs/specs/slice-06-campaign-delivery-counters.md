# Slice 6 — Campaign delivery counters and completion

Status: Planned

Depends on: `slice-02-message-processing.md`, `slice-05-message-materialization.md`

Suggested branch: `feat/campaign-delivery-counters`

## Цель

Сделать `CampaignRun` counters и completion устойчивыми к retry, redelivery и concurrent workers, используя durable состояние PostgreSQL.

## Уже есть

Переиспользуем:

- `CampaignRun`;
- `recipientCount`, `sentCount`, `failedCount`, `skippedCount`, `retryCount`;
- `CampaignRun.complete(...)`;
- Message terminal transitions;
- application `Clock`.

## Scope

### 1. Domain methods

Добавить/закрепить domain operations вместо public setters:

```text
messageSent()
messageFailed()
messageRetryScheduled()
```

При необходимости добавить маленький helper для проверки возможности completion, но не переносить state machine в service.

### 2. Locked CampaignRun update

Repository должен поддерживать tenant-scoped locked lookup `CampaignRun` при изменении counters.

Использовать один lock order во всех delivery transitions, например:

```text
Message -> CampaignRun
```

### 3. Atomic counter transitions

При реальном transition:

```text
PROCESSING -> SENT
  -> sentCount + 1

PROCESSING -> FAILED
  -> failedCount + 1

PROCESSING -> RETRY_WAIT
  -> retryCount + 1
```

Message status и соответствующий counter меняются в одной transaction.

### 4. Completion

После terminal transition проверить:

```text
sentCount + failedCount + skippedCount == recipientCount
```

Если invariant выполнен и run находится в допустимом состоянии:

```text
CampaignRun.complete(clock.instant())
```

Completion определяется только durable DB state, а не состоянием RabbitMQ.

### 5. Idempotency

Повторный event/worker invocation для Message, который уже `SENT`/`FAILED`, не должен повторно менять counters.

## Основной поток

```text
lock Message
  -> validate real transition
  -> change Message status
  -> lock CampaignRun
  -> increment corresponding counter
  -> check terminal totals
  -> complete run if ready
  -> commit
```

## Инварианты и правила

- `sentCount` растёт только на первом переходе в `SENT`;
- `failedCount` растёт только на первом переходе в `FAILED`;
- `retryCount` означает число реально назначенных retry, а не число сообщений сейчас в `RETRY_WAIT`;
- retry не является terminal outcome;
- `sent + failed + skipped` не может превышать `recipientCount`;
- Rabbit ACK/queue empty не участвуют в completion;
- lock order должен быть одинаковым во всех code paths.

## Не входит

- изменение semantics counters под multi-channel fan-out;
- delivery statistics API;
- provider-specific metrics;
- redesign CampaignRun state machine без конкретной необходимости.

## Тесты

Обязательные:

```text
CampaignDeliveryCountersIntegrationTest
- SENT increments once
- FAILED increments once
- RETRY_WAIT increments retryCount only
- duplicate SENT/FAILED does not double-count
- concurrent terminal transitions keep totals correct
- completion only after all terminal outcomes
- counters never exceed recipientCount
```

Если concurrency test выявляет deadlock, исправлять lock order, а не добавлять бессистемные retries вокруг transaction.

## Definition of Done

- Message transition и counter update атомарны;
- redelivery не double-count;
- concurrent workers не ломают totals;
- `CampaignRun` завершается только по durable terminal outcomes;
- retry не завершает run;
- completion использует `Clock`;
- `mvn verify` зелёный.
