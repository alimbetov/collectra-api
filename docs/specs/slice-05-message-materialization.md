# Slice 5 — CampaignRun → Message materialization

Status: READY FOR IMPLEMENTATION  
Depends on: Slice 3, Slice 4, existing Campaign Core, existing Template/Localization subsystem  
Implementation branch: `feat/campaign-message-materialization`  
Spec branch: `spec/slice-05-message-materialization`

## 1. Цель

Материализовать подготовленный `CampaignRun` в durable immutable EMAIL `Message` и атомарно поставить каждый созданный message в существующий Transactional Outbox pipeline.

После Slice 5 один `CampaignRun` должен:

```text
CampaignRun
    -> CampaignRecipient snapshot
    -> final eligibility
    -> deterministic destination
    -> deterministic locale + TemplateVersion snapshot
    -> normalized render payload
    -> immutable rendered Message(QUEUED)
    -> MESSAGE_DELIVERY_REQUESTED Outbox event
```

Ключевые свойства:

- final eligibility непосредственно перед созданием Message;
- restart-safe idempotency;
- deterministic destination/locale/template;
- subject/body рендерятся ровно один раз;
- bounded page processing;
- отсутствие очевидного N+1;
- `Message + Outbox` находятся в одной DB transaction;
- materializer не вызывает provider/KumoMTA напрямую.

## 2. Baseline, который уже существует

Использовать существующие компоненты, не дублировать их:

- `Campaign`, `CampaignRun`, `CampaignRecipient`;
- `CampaignService.prepare(...)` уже фиксирует `destination` и `locale` recipient snapshot;
- `CampaignEligibilityService` уже batch-load-ит Customer / CustomerEmail / Invoice;
- `TemplateLocaleResolver` и tenant locale fallback;
- `TemplateVersionRepository`;
- `TemplateCompiler` / `TemplateRenderer`;
- `Message.queued(...)`;
- `messages.campaign_recipient_id UNIQUE`;
- `MessageDeliveryEventPublisher.requestDelivery(...)`;
- `OutboxService` и route `MESSAGE_DELIVERY_REQUESTED`;
- message delivery state machine из Slice 2–4.

Важно: `MessageDeliveryEventPublisher.requestDelivery(...)` имеет `Propagation.MANDATORY`. Это сознательно использовать как часть atomicity contract: materializer вызывает его только внутри transaction, в которой сохраняется `Message`.

## 3. Scope

Основной application service:

```text
io.collectra.api.campaign.application.CampaignMessageMaterializer
```

Допустимые узкие компоненты:

```text
CampaignMessagePayloadFactory
CampaignMessageTemplateResolver
CampaignRunTemplateBindingService
```

Не создавать generic workflow/process framework.

Slice 5 также имеет право доработать:

```text
CampaignEligibilityService
CampaignRecipientRepository
CampaignRunRepository
TemplateVersionRepository
```

только в объёме, необходимом для bounded batch materialization.

## 4. Entry point

Основной контракт:

```java
public MaterializationBatchResult materializeNextBatch(
        UUID tenantId,
        UUID campaignRunId,
        int batchSize)
```

Допустим facade:

```java
public MaterializationResult materialize(
        UUID tenantId,
        UUID campaignRunId)
```

который вызывает bounded batch до `hasNext=false`, но production processing не должен держать одну transaction на весь run.

Materializer принимает durable IDs, а не REST/UI DTO.

## 5. CampaignRun lifecycle и concurrency

Разрешённый lifecycle:

```text
PREPARING -> READY -> RUNNING
```

Первый materialization batch:

1. tenant-scoped загружает `CampaignRun` с `PESSIMISTIC_WRITE`;
2. принимает только `READY` или `RUNNING`;
3. для `READY` создаёт immutable run-scoped template bindings, описанные ниже;
4. делает `run.start(clock.instant())`;
5. выбирает bounded batch recipients;
6. materialize batch;
7. commit.

Следующие batch принимают только `RUNNING` и используют уже сохранённые template bindings.

`COMPLETED`, `FAILED`, `CANCELLED` materialize нельзя.

Run lock сериализует competing materializers одного run. DB unique constraint на `messages.campaign_recipient_id` остаётся final safety net, но unique violation не является normal control flow.

### Clock

Не использовать `Instant.now()` / `LocalDate.now()` непосредственно. Использовать уже существующий injected `Clock`.

## 6. Recipient claim/query contract

Текущий repository, возвращающий весь run целиком, недостаточен для materialization.

Добавить bounded tenant-scoped query с устойчивым порядком:

```text
WHERE tenant_id = :tenantId
  AND run_id = :runId
  AND status <> 'SKIPPED'
  AND NOT EXISTS (
      SELECT 1
      FROM messages m
      WHERE m.campaign_recipient_id = campaign_recipients.id
  )
ORDER BY created_at ASC, id ASC
LIMIT :batchSize
```

Предпочтительно native query или эквивалент, который не загружает весь run.

Так как processed recipient исчезает из candidate set после появления `Message`, всегда читается следующий `LIMIT N`; offset pagination здесь не использовать — это исключает offset drift.

Нужен tenant scope во всех queries.

## 7. Final eligibility — один batch read, без повторной загрузки

Текущий `CampaignEligibilityService.recheck(...)` загружает весь run. Его надо рефакторить так, чтобы существовала page-scoped evaluation операция.

Рекомендуемый контракт:

```java
EligibilityBatch evaluateBatch(
        UUID tenantId,
        List<CampaignRecipient> recipients)
```

Она:

- batch-load-ит customers;
- batch-load-ит emails;
- batch-load-ит invoices;
- применяет существующие eligibility rules;
- переводит recipient в `ELIGIBLE` или `SKIPPED`;
- возвращает уже загруженный Customer/Invoice context для `ELIGIBLE` recipients, чтобы payload builder не делал второй read.

Существующий `recheck(runId)` должен переиспользовать ту же batch evaluation logic, а не иметь вторую копию eligibility rules.

Обязательный сценарий:

```text
prepare -> recipient SNAPSHOT
payment/allocation -> outstandingAmount <= 0
materialization final recheck
-> recipient SKIPPED / PAID
-> Message отсутствует
-> Outbox отсутствует
```

Другие существующие reasons сохраняются:

```text
CUSTOMER_INACTIVE
NO_CONTACT
PAID
```

Не вводить альтернативные reason codes для тех же outcomes.

## 8. Destination determinism

Для EMAIL authoritative destination:

```text
CampaignRecipient.destination
```

Он уже выбран в `CampaignService.prepare(...)` по правилу:

```text
ACTIVE primary email
    -> first ACTIVE email
    -> null
```

Materializer не выбирает другой email и не пересчитывает primary preference.

Final eligibility только подтверждает, что snapshot destination всё ещё существует как ACTIVE email данного Customer.

Если snapshot destination больше не существует/не ACTIVE:

```text
CampaignRecipient -> SKIPPED / NO_CONTACT
Message -> не создаётся
```

Это защищает от отправки на контакт, изменённый после prepare.

## 9. Locale/template determinism — обязательный run-scoped snapshot

### 9.1 Почему исходного `latest PUBLISHED` недостаточно

`Campaign` содержит anchor `templateVersionId`, но locale fallback может выбрать другую locale-версию того же template family.

Если каждый batch делает:

```text
resolve locale -> latest PUBLISHED version
```

то публикация новой template version между batch A и batch B приведёт к разному контенту внутри одного `CampaignRun`. После crash/restart эффект тот же.

Это запрещено.

### 9.2 Run template binding

В Slice 5 добавить durable run-scoped snapshot, например таблицу:

```text
campaign_run_template_bindings
--------------------------------
tenant_id              UUID NOT NULL
campaign_run_id        UUID NOT NULL
requested_locale       VARCHAR(35) NOT NULL
resolved_locale        VARCHAR(35) NOT NULL
template_version_id    UUID NOT NULL
resolution_source      VARCHAR(30) NOT NULL
created_at              TIMESTAMPTZ NOT NULL

UNIQUE(campaign_run_id, requested_locale)
```

FK:

```text
campaign_run_id -> campaign_runs(id) ON DELETE CASCADE
template_version_id -> template_versions(id)
```

Index для tenant/run lookup:

```text
(tenant_id, campaign_run_id)
```

Не требуется отдельная surrogate identity, если composite key удобно реализовать; допустим UUID id, если это соответствует текущему persistence style.

### 9.3 Когда binding создаётся

При первом `READY -> RUNNING`, под run lock:

1. tenant-scoped загрузить `Campaign`;
2. tenant-scoped загрузить anchor `Campaign.templateVersionId`;
3. определить его `templateId` и EMAIL channel;
4. одним query получить `DISTINCT CampaignRecipient.locale` данного run;
5. blank/null locale нормализовать в tenant default locale;
6. для каждого distinct requested locale вызвать существующий `TemplateLocaleResolver`;
7. для resolved locale выбрать конкретный `PUBLISHED TemplateVersion`;
8. сохранить binding;
9. после этого перейти к recipient batches.

Количество binding rows ограничено количеством locale, а не количеством recipients.

После создания bindings materializer никогда не выполняет `latest PUBLISHED` для конкретного recipient. Он читает только binding данного run.

Публикация/архивация template после старта run не меняет уже зафиксированный `templateVersionId`.

### 9.4 Anchor invariant

Все selected locale versions должны принадлежать:

```text
same tenant
same templateId as Campaign.templateVersionId
the Campaign channel (EMAIL)
status = PUBLISHED на момент snapshot creation
```

Arbitrary template fallback запрещён.

## 10. Normalized render payload

JPA entities не передавать renderer-у напрямую.

Target pipeline:

```text
EligibleRecipientContext
    -> CampaignMessagePayloadFactory
    -> JsonNode normalizedPayload
```

Минимальный payload:

```text
document.number
document.date

customer.externalId
customer.type
customer.displayName
customer.name
customer.firstName
customer.lastName
customer.middleName
customer.companyName
customer.locale
customer.timezone

invoice.externalId
invoice.invoiceNumber
invoice.invoiceDate
invoice.dueDate
invoice.amount
invoice.originalAmount
invoice.paidAmount
invoice.outstandingAmount
invoice.currency
invoice.paymentStatus
invoice.contractId
invoice.documentFileId

custom.customer
custom.invoice
```

Aliases:

```text
document.number = invoice.invoiceNumber
document.date   = invoice.invoiceDate
customer.name   = customer.displayName
invoice.amount  = invoice.originalAmount
```

Serialization rules:

- dates -> ISO strings;
- decimals -> JSON numbers;
- missing custom objects -> `{}`;
- `items[]` не создавать искусственно, пока canonical item source реально отсутствует.

Не добавлять новый generic mapping DSL в Slice 5.

## 11. Rendering contract

EMAIL:

```text
subject:
  TemplateCompiler.compileText(...)
  -> TemplateRenderer.renderText(...)

body:
  existing HTML compile/render path
```

Missing required placeholder или invalid template data должны завершать current batch exception-ом до commit. Такой recipient нельзя автоматически превращать в `SKIPPED`, потому что это configuration/data defect, а не eligibility outcome.

Materializer не swallow-ит rendering exceptions.

## 12. Immutable Message snapshot

После успешного render вызвать существующий factory:

```java
Message.queued(
    tenantId,
    campaignId,
    campaignRunId,
    campaignRecipientId,
    customerId,
    invoiceId,
    templateVersionId,
    CommunicationChannel.EMAIL,
    destination,
    resolvedLocale,
    renderedSubject,
    renderedBody)
```

Immutable delivery snapshot образуют:

```text
Message.templateVersionId
Message.destination
Message.resolvedLocale
Message.subject
Message.body
```

После insert эти поля Slice 5 не изменяет.

Delivery worker/KumoMTA adapter никогда:

- не выбирают locale;
- не выбирают template version;
- не re-render-ят subject/body;
- не выбирают другой destination.

## 13. Atomic Message + Outbox

Для каждого successfully rendered recipient внутри текущей batch transaction:

```text
Message message = messages.save(Message.queued(...));
deliveryEvents.requestDelivery(tenantId, message.getId());
```

`MessageDeliveryEventPublisher.requestDelivery(...)` уже использует `Propagation.MANDATORY`, поэтому отдельный `REQUIRES_NEW` запрещён.

Инвариант commit:

```text
Message exists <=> MESSAGE_DELIVERY_REQUESTED Outbox row exists
```

Не допускаются состояния:

```text
Message exists, Outbox missing
Outbox exists, Message missing
```

Outbox payload/route не переизобретать. Использовать существующий `MessageDeliveryRequested(tenantId, messageId)`.

Никакого direct RabbitMQ publish из materializer.

## 14. Transaction boundary

Одна transaction = один bounded materialization batch.

Внутри transaction:

1. lock run;
2. при первом batch initialize template bindings + `READY -> RUNNING`;
3. load recipients;
4. final eligibility batch evaluation;
5. build/render всех eligible records данного batch;
6. persist Messages;
7. append Outbox events;
8. commit.

Если system/render/persistence exception возникает до commit — изменения этого batch полностью rollback, включая eligibility status changes, Messages и Outbox rows.

Не держать transaction на десятки тысяч recipients.

## 15. Idempotency и restart semantics

Текущая DB гарантия:

```text
UNIQUE(messages.campaign_recipient_id)
```

для EMAIL-only Slice 5 означает one Message per CampaignRecipient.

Повторный вызов после successful commit:

- не возвращает уже materialized recipient в candidate query;
- не создаёт второй Message;
- не создаёт второй `MESSAGE_DELIVERY_REQUESTED` Outbox event.

Crash before commit:

```text
ни Message, ни Outbox не существуют -> batch безопасно повторяется
```

Crash after commit:

```text
Message + Outbox существуют -> recipient больше не выбирается
```

Concurrent calls одного run сериализуются run lock-ом.

Перед multi-channel fan-out unique constraint будет пересмотрен отдельно, например до `(campaign_recipient_id, channel)`. В Slice 5 его не менять.

## 16. Batch size / memory

Configuration:

```yaml
collectra:
  campaign:
    message-materialization:
      batch-size: 200
```

Default: `200`.

Validation:

```text
1 <= batchSize <= 1000
```

или эквивалентный разумный hard upper bound.

Materialization memory должна зависеть от batch size, а не от total run size.

## 17. N+1 requirements

Для одного batch разрешены bounded queries приблизительно следующих типов:

```text
1 run lock/read
1 recipient candidate batch
1 customer batch
1 email/contact batch
1 invoice batch
1 template binding lookup (или один preload всех bindings run)
Message batch inserts
Outbox batch/individual inserts
```

Запрещено:

```text
for each recipient:
    customerRepository.find...
    emailRepository.find...
    invoiceRepository.find...
    templateRepository.find...
```

SQL count для read-side одного page не должен линейно расти с N recipients.

Если test infrastructure позволяет Hibernate statistics/query counting — добавить guard test. Если нет, integration test + repository API review должны явно подтверждать batch methods.

## 18. MaterializationBatchResult

Минимальный application result:

```java
public record MaterializationBatchResult(
        int scanned,
        int created,
        int skipped,
        boolean hasNext) {}
```

`alreadyMaterialized` не обязателен: normal candidate query уже исключает recipients с Message. Если нужен для diagnostics, допускается добавить, но не делать отдельный per-recipient existence query.

`hasNext` определяется bounded follow-up/candidate semantics, а не offset/page number.

## 19. Campaign counters — граница со Slice 6

Slice 5 не реализует полноценные delivery counters/completion — это Slice 6.

В Slice 5:

- final eligibility меняет `CampaignRecipient.status` на `SKIPPED`;
- `Message` создаётся как `QUEUED`;
- `CampaignRun` переводится `READY -> RUNNING`.

Не добавлять ad-hoc обновление `sentCount/failedCount/retryCount`.

`skippedCount` и run completion будут окончательно связаны с durable outcomes в Slice 6. Не дублировать будущую counter logic внутри materializer.

## 20. Database changes

В отличие от предыдущей версии ТЗ, **одна migration в Slice 5 нужна** для run-scoped template binding, иначе deterministic locale/template across pages/restarts не гарантируется.

Добавить следующий changelog после `026` согласно текущей numbering convention, если номер ещё свободен на момент реализации.

Также проверить существующие indexes:

```text
campaign_recipients(tenant_id, run_id, status, created_at)
messages(campaign_recipient_id) UNIQUE
messages(tenant_id, campaign_run_id, status, created_at)
```

Candidate query с `NOT EXISTS Message` должен быть проверен PostgreSQL integration test; новый recipient index добавлять только если существующий `idx_campaign_recipients_run_status` реально недостаточен.

## 21. Error policy

Разделение ошибок:

```text
Eligibility/business outcome
    -> recipient SKIPPED
    -> batch continues

Template/render/data-contract defect
    -> throw
    -> rollback current batch
    -> no Message / no Outbox for batch

Database/system/infrastructure failure
    -> throw
    -> rollback current batch
    -> orchestration may retry later
```

Не превращать технические ошибки в `SKIPPED`.

Не логировать rendered body или destination на WARN/ERROR без необходимости; destination является customer contact data.

## 22. Required tests

### 22.1 Unit tests

`CampaignMessageMaterializerTest` и узкие component tests:

- snapshot destination используется без reselection;
- inactive/missing snapshot email -> `SKIPPED/NO_CONTACT`;
- paid invoice -> `SKIPPED/PAID`;
- locale binding возвращает deterministic TemplateVersion;
- subject/body rendered once;
- missing placeholder -> exception/no message request;
- existing materialized recipient не выбирается повторно.

### 22.2 PostgreSQL integration

Обязательные сценарии:

1. one eligible recipient -> exactly one `Message(QUEUED)` + one delivery Outbox row;
2. transaction rollback -> neither Message nor Outbox remains;
3. rerun materialization -> still one Message + one Outbox event;
4. tenant isolation;
5. `prepare -> payment/allocation -> materialize -> SKIPPED/PAID`, no Message;
6. destination removed/deactivated after prepare -> `SKIPPED/NO_CONTACT`;
7. new template version published **after run starts** -> remaining pages still use snapshotted template binding;
8. crash/restart simulation between batches -> same binding/version is reused;
9. template modified/published later -> already rendered Message subject/body stay unchanged;
10. two concurrent materializers for same run -> no duplicates/unique violation as normal outcome.

### 22.3 Paging test

Создать recipients > `batchSize`:

- all eligible recipients materialized exactly once;
- skipped recipients have no Message;
- order stable;
- no offset drift;
- final `hasNext=false`.

### 22.4 N+1 guard

Representative batch, например 50 recipients:

- customer/email/invoice/template reads выполняются batch-wise;
- read query count не масштабируется как `O(N)`.

Не делать brittle assertion на абсолютное число SQL, если framework добавляет служебные queries; проверять bounded behavior.

## 23. Recommended implementation order

1. migration + `CampaignRunTemplateBinding` repository/domain;
2. run lock repository method;
3. bounded recipient candidate query;
4. refactor `CampaignEligibilityService` на reusable batch evaluation;
5. run template binding initialization/resolution;
6. normalized payload factory;
7. subject/body rendering;
8. `CampaignMessageMaterializer` transaction;
9. `Message + MessageDeliveryEventPublisher` atomic path;
10. idempotency/concurrency tests;
11. paging/N+1 tests;
12. full `mvn --batch-mode --no-transfer-progress clean verify`.

## 24. Out of scope

- KumoMTA/provider HTTP call;
- changing delivery state machine;
- SMS/WhatsApp/Telegram/In-App materialization;
- multi-channel unique constraint;
- attachments/document generation;
- campaign delivery counters/completion (Slice 6);
- generic query DSL;
- generic workflow engine;
- replacing `TemplateRenderer`;
- direct RabbitMQ publishing.

## 25. Definition of Done

Slice 5 готов, когда одновременно выполняются все условия:

- `READY CampaignRun` начинает bounded materialization и становится `RUNNING`;
- final eligibility выполняется на каждом candidate batch;
- paid/inactive/no-contact recipient не получает Message;
- destination берётся только из immutable recipient snapshot;
- locale + concrete TemplateVersion фиксируются run-scoped и переживают restart;
- subject/body рендерятся один раз и сохраняются в `Message`;
- `Message.templateVersionId`, locale, destination, subject, body образуют immutable delivery snapshot;
- `Message + MESSAGE_DELIVERY_REQUESTED Outbox` commit/rollback атомарны;
- повторный и concurrent materialization не создаёт дубликатов;
- batch processing не использует offset drift;
- read side не содержит N+1;
- tenant isolation соблюдается;
- materializer не вызывает provider;
- PostgreSQL integration tests покрывают atomicity/idempotency/template snapshot/final eligibility;
- `mvn --batch-mode --no-transfer-progress clean verify` green.
