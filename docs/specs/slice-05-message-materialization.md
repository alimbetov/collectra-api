# Slice 5 — CampaignRun to Message materialization

Status: PLANNED  
Depends on: Slice 3, existing Campaign Core, existing Template subsystem  
Suggested branch: `feat/campaign-message-materialization`

## 1. Цель

Создавать durable EMAIL `Message` из подготовленного `CampaignRun` и его recipient snapshot, используя существующий template subsystem и Transactional Outbox.

После Slice 5 один READY/RUNNING CampaignRun должен детерминированно породить набор immutable messages без N+1 и без дубликатов при повторном запуске.

## 2. Текущий baseline

Уже существует:

- `Campaign`, `CampaignRun`, campaign recipient snapshot;
- eligibility logic/recheck;
- customer/contact/segment/invoice data;
- `TemplateVersion`/repository;
- `TemplateCompiler`;
- `TemplateRenderer` с HTML/text render и missing-placeholder validation;
- `Message.queued(...)`;
- unique `messages.campaign_recipient_id` для текущего EMAIL-only MVP;
- `OutboxService`;
- `MESSAGE_DELIVERY_REQUESTED` route из Slice 3.

Не строить новый template engine и не менять Message body во время delivery.

## 3. Scope

Добавить application service, например:

```text
io.collectra.api.campaign.application.CampaignMessageMaterializer
```

Допустимые вспомогательные компоненты:

```text
CampaignMessagePayloadFactory
CampaignMessageDestinationResolver
CampaignMessageTemplateResolver
```

Добавлять их только если materializer становится слишком большим. Не создавать generic workflow framework.

Текущий `CampaignEligibilityService.recheck` загружает run целиком. В рамках
этого Slice выделить page-scoped evaluation contract, который принимает уже
загруженный batch и возвращает counts + eligible Customer/Invoice context. И
обычный recheck, и materializer используют его; второй полный read того же batch
не допускается.

## 4. Entry point

Рекомендуемый контракт:

```java
public MaterializationResult materialize(UUID tenantId, UUID campaignRunId)
```

или bounded batch method:

```java
public MaterializationBatchResult materializeNextBatch(
        UUID tenantId,
        UUID campaignRunId,
        int batchSize)
```

Для больших кампаний предпочтителен второй вариант.

Materializer принимает только durable IDs, не UI DTO.

### Single materialization claim

Каждый batch начинается с tenant-scoped `PESSIMISTIC_WRITE` lock на
`CampaignRun`. Это сериализует competing materializers одного run. После
получения lock batch заново выбирает только recipients:

```text
status != SKIPPED
AND NOT EXISTS Message(campaign_recipient_id)
ORDER BY created_at, id
LIMIT batchSize
```

Первый batch атомарно делает `READY -> RUNNING`; следующие принимают только
`RUNNING`. Lock отпускается после commit одного bounded batch. DB unique остаётся
последней защитой, но normal concurrent flow не должен завершаться unique
violation.

## 5. Allowed CampaignRun state

До начала обработки явно проверить состояние run.

Рекомендуемая последовательность:

```text
PREPARING -> READY -> RUNNING
```

Materialization выполняется только для `READY`/`RUNNING` согласно уже существующему orchestration contract.

Если текущий campaign service переводит `READY -> RUNNING` до materialization — переиспользовать его правило. Не добавлять альтернативный status lifecycle.

Terminal `COMPLETED/FAILED/CANCELLED` materialize нельзя.

## 6. Processing flow

Для каждого bounded page/batch:

```text
load CampaignRun tenant-scoped
        |
        v
load CampaignRecipient snapshot page
        |
        v
final eligibility recheck where required
        |
        +-- not eligible -> existing SKIPPED handling
        |
        v
resolve EMAIL destination
        |
        v
resolve locale + TemplateVersion
        |
        v
build normalized payload
        |
        v
render subject + body
        |
        v
Message.queued(...)
        |
        v
save Message
        |
        v
OutboxService.append(MESSAGE_DELIVERY_REQUESTED)
```

Outbox payload соответствует Slice 3 и содержит оба durable scope identifiers:
`tenantId` и `messageId`.

## 7. Transaction boundary

Один bounded batch должен обрабатываться транзакционно в разумном размере.

Для каждого created Message:

```text
Message row + Outbox row
```

должны commit together.

Нельзя получить состояние:

```text
Message exists, Outbox missing
```

или:

```text
Outbox event exists, Message missing
```

При rendering/validation exception текущая unit of work должна rollback.

Не держать одну transaction на десятки тысяч recipients.

## 8. Paging

Configuration:

```yaml
collectra:
  campaign:
    message-materialization:
      batch-size: 200
```

Default 200 допустим; точное значение можно скорректировать нагрузочными тестами.

Требования:

- bounded memory;
- deterministic ordering (`id` или snapshot sequence);
- no offset drift при изменении processed state;
- если repository поддерживает stable keyset/ID pagination — предпочесть её;
- `PageRequest` допустим для MVP, если recipient snapshot immutable в рамках run.

## 9. Idempotency

Текущая DB гарантия:

```text
unique(campaign_recipient_id)
```

для EMAIL-only означает one Message per CampaignRecipient.

Повторный materialize одного run:

- не создаёт второй Message;
- не создаёт второй delivery Outbox event для уже materialized recipient;
- не ломается на unique violation как на нормальном control flow, если можно проверить существование batch query заранее.

DB unique constraint остаётся final safety net.

Перед multi-channel fan-out constraint будет пересмотрен отдельно, например:

```text
unique(campaign_recipient_id, channel)
```

В Slice 5 его не менять.

## 10. Destination resolution

Для EMAIL MVP authoritative destination — immutable
`CampaignRecipient.destination`, выбранный текущим campaign prepare rule.

Требования:

- destination не выбирается повторно из другого email после prepare;
- final eligibility tenant-scoped проверяет, что snapshot email всё ещё ACTIVE;
- пустой/недоступный email не создаёт invalid Message;
- recipient получает существующий `NO_CONTACT`, чтобы не вводить второй reason
  для того же business outcome;
- не читать contact повторно N раз, если его можно batch-load для page.

Если customer имеет несколько email, materializer не пересчитывает выбор: правило
детерминированно применяется один раз в `CampaignService.prepare` и фиксируется
отдельным test.

## 11. Locale resolution

Resolution должен быть deterministic:

```text
recipient/customer preferred locale
    -> campaign/template allowed locale
    -> tenant/default locale
```

Использовать существующий locale resolver, если он уже присутствует.

Результат обязательно сохранить в `Message.resolvedLocale`.

Не определять locale повторно в delivery worker.

Exact algorithm для текущего template subsystem:

1. tenant-scoped загрузить anchor `Campaign.templateVersionId`;
2. взять из anchor `templateId` и EMAIL channel;
3. requested locale = recipient locale, а при blank — tenant default;
4. вызвать `TemplateLocaleResolver.resolve(...)`;
5. загрузить latest `PUBLISHED` version для resolved locale/template/channel;
6. сохранить фактические version id и locale в Message.

## 12. Template resolution

На materialization выбрать конкретный immutable `TemplateVersion`.

`Message.templateVersionId` хранит выбранную version.

Если template/version не найдены или не подходят channel/locale:

- не создавать partially valid Message;
- дать контролируемую materialization error;
- не fallback-ить на arbitrary latest version.

## 13. Payload building

Normalized payload строится из уже существующих canonical/custom data rules.

Materializer не должен передавать JPA entities напрямую renderer-у.

Target:

```text
CampaignRecipient + Customer + Invoice + Custom fields
        -> JsonNode normalizedPayload
```

Поля должны соответствовать placeholder contracts, например:

```text
invoice.number
invoice.dueDate
invoice.amount
invoice.currency
customer.*
custom.*
items[]
```

Минимальный normalized payload этого Slice фиксирован:

```text
document.number/date
customer.externalId/type/displayName/name/firstName/lastName/middleName/
         companyName/locale/timezone
invoice.externalId/invoiceNumber/invoiceDate/dueDate/amount/originalAmount/
        paidAmount/outstandingAmount/currency/paymentStatus/contractId/documentFileId
custom.customer/custom.invoice
```

Aliases: `document.number = invoice.invoiceNumber`,
`document.date = invoice.invoiceDate`, `customer.name = customer.displayName`,
`invoice.amount = invoice.originalAmount`. Dates — ISO strings, decimals — JSON
numbers, отсутствующие custom objects — `{}`. `items[]` добавляется только когда
в actual canonical model появляется соответствующий source; пустой искусственный
массив не создавать.

Missing required placeholder обрабатывается как rendering/materialization error до создания delivery event.

## 14. Subject/body rendering

EMAIL:

- `subject` рендерится через text rendering;
- `body` — через existing HTML renderer;
- `subject` компилируется `TemplateCompiler.compileText`, затем
  `TemplateRenderer.renderText`;
- `body` рендерится `TemplateRenderer.render`;
- rendered values сохраняются в Message;
- последующее изменение template не меняет уже созданный Message.

Snapshot invariant:

```text
Message.templateVersionId
Message.resolvedLocale
Message.subject
Message.body
```

полностью описывают отправляемый контент.

`MessageDeliveryWorker` никогда не re-render-ит template.

## 15. Eligibility recheck

Использовать существующий final eligibility mechanism, особенно для receivable cases, где после prepare invoice мог стать paid/allocated.

Сценарий:

```text
prepare recipient as eligible
payment/allocation happens
materialization final recheck
-> SKIPPED/PAID
-> Message не создаётся
```

Не копировать eligibility rules внутрь materializer. Вызывать existing service/domain contract.

## 16. Avoid N+1

Для одной page заранее batch-load то, что нужно:

- customer contacts/email;
- segment-related data, если используется;
- invoice data;
- template versions/locales where applicable.

Acceptance criterion: SQL count для page не растёт линейно как `N recipients * multiple repositories`.

Не требуется абсолютный minimum query count, но очевидный N+1 должен отсутствовать.

## 17. MaterializationResult

Полезный минимальный result:

```java
public record MaterializationBatchResult(
        int scanned,
        int created,
        int skipped,
        int alreadyMaterialized,
        boolean hasNext) {}
```

Это application result, не REST contract.

## 18. Error handling

Разделить:

```text
recipient-specific business skip
    -> продолжить batch

recipient-specific invalid render/data
    -> controlled failure according to campaign policy

system/infrastructure error
    -> rollback current transaction/batch and retry orchestration later
```

Не swallow-ить template/render exceptions и не enqueue invalid message.

## 19. Database changes

Ожидаемо новая migration не нужна.

Перед реализацией проверить indexes:

```text
campaign_recipients(campaign_run_id, ...paging key...)
messages(campaign_recipient_id) unique
messages(tenant_id, campaign_run_id)
```

Новый index добавлять только при доказанной необходимости query plan/integration test.

## 20. Tests

### Unit

`CampaignMessageMaterializerTest`

- resolves destination;
- resolves locale/template;
- renders subject/body;
- missing destination -> skipped/no Message;
- failed eligibility -> no Message;
- existing Message -> no duplicate.

### PostgreSQL integration

- one recipient -> Message + Outbox committed;
- rollback -> neither Message nor Outbox remains;
- rerun -> one Message only;
- tenant isolation;
- final paid eligibility -> skipped/no Message;
- rendered snapshot remains unchanged after template version modification/new version.

### Paging/batch

- > batch size recipients processed across multiple batches;
- every eligible recipient exactly once;
- no skipped recipient accidentally materialized;
- deterministic completion of scan.

### Query/N+1 guard

Если project test infrastructure позволяет SQL statistics — добавить bounded query-count assertion для representative page. Иначе review/test fixture должен явно использовать batch repository methods.

## 21. Порядок реализации

1. определить existing recipient paging contract;
2. destination/locale/template resolution reuse;
3. normalized payload builder reuse;
4. materializer batch service;
5. Message + Outbox same transaction;
6. idempotency path;
7. final eligibility integration;
8. batch loading/N+1 cleanup;
9. unit/integration/paging tests;
10. `mvn verify`.

## 22. Out of scope

- attachment generation;
- SMS/WhatsApp/Telegram;
- changing unique constraint for multi-channel;
- provider HTTP call;
- generic query DSL;
- replacing TemplateRenderer.

## 23. Definition of Done

Slice 5 готов, если:

- prepared recipients materialize into immutable EMAIL Messages;
- final eligibility is respected;
- destination/locale/template resolution deterministic;
- subject/body rendered once and persisted;
- Message + Outbox atomic;
- rerun idempotent;
- bounded batch/paging works;
- no obvious N+1;
- tenant isolation enforced;
- no provider call from materializer;
- `mvn verify` green.
