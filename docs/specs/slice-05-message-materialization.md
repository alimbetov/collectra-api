# Slice 5 — CampaignRun to Message materialization

Status: Planned

Depends on: `slice-02-message-processing.md`, `slice-03-message-delivery-messaging.md`

Suggested branch: `feat/campaign-message-materialization`

## Цель

Идемпотентно превращать подготовленный `CampaignRun` и его recipient snapshot в durable `Message`, готовые к асинхронной доставке.

## Уже есть

Переиспользуем:

- Campaign/CampaignRun/CampaignRecipient;
- eligibility logic;
- existing template/version/locale services;
- `TemplateRenderer`;
- `Message.queued(...)`;
- `MessageRepository`;
- `OutboxService`;
- событие `MESSAGE_DELIVERY_REQUESTED`.

## Scope

### 1. Materializer

Добавить application service:

```text
CampaignMessageMaterializer
```

Он получает `tenantId` + `campaignRunId` и обрабатывает recipients bounded pages/batches.

### 2. Recipient resolution

Для каждого recipient:

```text
final eligibility recheck where required
  -> destination
  -> locale
  -> template version
  -> template model
```

Для EMAIL destination должен быть разрешён до создания `Message`.

### 3. Rendering snapshot

Использовать существующий `TemplateRenderer` и сохранить результат в `Message`:

```text
templateVersionId
resolvedLocale
subject
body
```

После materialization delivery worker не должен повторно рендерить template.

### 4. Persistence + Outbox

Для каждого создаваемого Message:

```text
Message.queued(...)
  -> save Message
  -> append MESSAGE_DELIVERY_REQUESTED
```

`Message` и соответствующий Outbox event должны commit/rollback вместе в одной bounded transaction.

### 5. Idempotency

Для EMAIL-only MVP сохранить текущий invariant:

```text
one campaign_recipient -> one Message
```

Использовать существующий unique constraint `campaign_recipient_id` как последнюю защиту от duplicate creation.

Повторный materialization не должен создавать второй Message или второй delivery request для уже materialized recipient без причины.

### 6. Batch processing

- не загружать весь run в память;
- batch/page size configurable;
- commit bounded chunks;
- избегать N+1 для destination/template/recipient data;
- порядок не должен зависеть от offset при изменяемой выборке — выбирать стабильную paging strategy.

## Основной поток

```text
CampaignRun READY
  -> page recipients
  -> eligibility
  -> resolve destination/locale/template
  -> render subject/body
  -> Message.queued
  -> Message + Outbox commit
  -> next page
```

## Инварианты и правила

- tenant scope обязателен на всём пути;
- rendered body/subject — immutable delivery snapshot;
- template edit после materialization не меняет существующий Message;
- Message и delivery Outbox event не должны расходиться;
- duplicate materialization безопасен;
- provider call здесь запрещён;
- ошибки одного bounded batch не должны требовать держать одну транзакцию на весь большой campaign run.

## Не входит

- KumoMTA implementation;
- attachments/document generation;
- multi-channel fan-out;
- redesign Template Engine;
- переход unique constraint на `(campaign_recipient_id, channel)` до реального multi-channel scope.

## Тесты

Обязательные:

```text
CampaignMessageMaterializerIntegrationTest
- READY run -> Message created
- subject/body snapshot persisted
- locale/template version persisted
- tenant isolation
- duplicate materialization idempotent
- Message + Outbox rollback together
- ineligible recipient skipped according to current campaign rules
- multiple pages processed without duplicates
```

Отдельно проверить отсутствие очевидного N+1 на batch уровне, если текущая repository structure позволяет это сделать устойчивым test-ом.

## Definition of Done

- prepared recipients превращаются в durable EMAIL `Message`;
- rendering использует существующий template subsystem;
- Message содержит immutable rendered snapshot;
- Message + Outbox записываются атомарно;
- повторный запуск не создаёт duplicates;
- large runs обрабатываются bounded batches;
- provider call отсутствует;
- `mvn verify` зелёный.
