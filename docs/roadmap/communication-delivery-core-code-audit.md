# Communication / Delivery Core — code audit and binding decisions

## 0. Статус документа

Этот документ фиксирует аудит ТЗ относительно фактического состояния
`collectra-api` на commit `e639082` (`main`) и уточняет решения, обязательные для
реализации.

Связанные документы:

- `communication-delivery-core.md` — исходные функциональные границы;
- `communication-delivery-core-review.md` — ранний архитектурный review;
- `communication-delivery-core-implementation.md` — подробный implementation design;
- `communication-delivery-core-tasks.md` — backlog.

При противоречии между ранними документами и этим аудитом приоритет имеет этот
документ. После принятия решений ниже implementation и tasks должны читаться вместе
с ним.

Scope остаётся прежним: модульный монолит, один delivery worker, одна основная
communication queue, существующий shared Outbox, EMAIL vertical slice первым.

---

## 1. Итог аудита

Архитектурное направление верное, но текущая версия ТЗ ещё не готова к прямой
реализации без уточнений. Основной happy path описан хорошо, однако восемь мест
могут привести к зависшим сообщениям, неверным counters либо к коду, который нельзя
собрать поверх существующих API.

| Приоритет | Разрыв | Решение |
|---|---|---|
| BLOCKER | Не определён payload для `TemplateRenderer` | Ввести `CampaignMessagePayloadFactory` с точной JSON-схемой |
| BLOCKER | Не определено разрешение locale/template version | Использовать `TemplateLocaleResolver`, сохранять фактические `templateVersionId` и `resolvedLocale` в `Message` |
| BLOCKER | Message может навсегда остаться `PROCESSING` | Хранить `processingStartedAt` и добавить stale recovery |
| BLOCKER | ТЗ обещает фактически exactly-once provider send | Зафиксировать at-least-once и обязательный provider idempotency key = `Message.id` |
| HIGH | TX2 может дважды увеличить counters | В TX2 блокировать Message через `SELECT FOR UPDATE`; counters менять только победителю transition |
| HIGH | JPQL bulk updates обходят auditing/version | Во всех conditional updates менять `updatedAt` и `version`, всегда фильтровать по `tenantId` |
| HIGH | `CampaignEligibilityService` и start читают весь run | Добавить page/slice API и batch-load внутри страницы |
| HIGH | `CANCELLED` объявлен terminal, но counter отсутствует | Убрать `MessageStatus.CANCELLED` из v1; cancellation delivery вынести в отдельный slice |
| HIGH | Rabbit listener error/ack policy не определена | Одна общая DLQ, `defaultRequeueRejected=false`, invalid event не зацикливать |
| HIGH | API paths не совпадают с текущим controller | Использовать `/api/v1/campaigns/runs/{runId}/deliver` и `/api/v1/messages` |
| MEDIUM | `UNIQUE(recipient, channel)` слабее доменной модели | В v1 использовать `UNIQUE(campaign_recipient_id)` |
| MEDIUM | Read API раскрывает destination/body без правил | Tenant scope, `CAMPAIGN_READ`, masked destination в list, content только в detail |
| MEDIUM | Outbox `DEAD` оставляет Message `QUEUED` | Метрика + reconciliation task до production provider; run не завершать ложно |
| DEFERRED | Consent, suppression, quiet hours отсутствуют в коде | Mock slice разрешён; реальный provider запрещён до policy gate |
| DEFERRED | Attachments/PDF не входят в модель | Добавить отдельным slice после стабильного text EMAIL path |

---

## 2. Что подтверждено текущим кодом

### 2.1 Campaign Core

Фактические классы:

```text
campaign/domain/Campaign.java
campaign/domain/CampaignRun.java
campaign/domain/CampaignRecipient.java
campaign/application/CampaignService.java
campaign/application/CampaignEligibilityService.java
```

Подтверждено:

- `CampaignService.create(...)` принимает только published `TemplateChannel.EMAIL`;
- `prepare(...)` использует `Clock` для overdue dates;
- invoice selection уже выполняется в PostgreSQL страницами по 500;
- customer/email/segment data уже загружаются batch-методами внутри страницы;
- recipient snapshot хранит destination и locale;
- eligibility проверяет active customer, snapshot email и outstanding amount;
- integration test `CampaignStabilizationIntegrationTest` фиксирует сценарий
  `prepare -> payment allocation -> recheck -> SKIPPED/PAID`.

Следствие: Communication Core не должен повторно реализовывать selection. Его вход —
готовый `CampaignRun` в `READY`.

### 2.2 Template Core

Фактические API:

```java
TemplateRenderer.render(TemplateVersion version, JsonNode payload)
TemplateCompiler.compileText(UUID templateVersionId, String text)
TemplateRenderer.renderText(CompiledTemplate template, JsonNode payload)
TemplateLocaleResolver.resolve(UUID tenantId, UUID templateId,
                               TemplateChannel channel, String requestedLocale)
```

`TemplateRenderer` не умеет сам получить Customer/Invoice и не строит normalized
payload. Следовательно фраза «переиспользовать существующий renderer» недостаточна:
до вызова renderer нужен новый boundary component.

### 2.3 Shared Outbox

Существующий Outbox уже реализует:

- PostgreSQL persistence;
- `FOR UPDATE SKIP LOCKED` claim;
- publisher confirms и returned message detection;
- retry/backoff публикации;
- stale `PROCESSING` recovery;
- terminal `DEAD`;
- route по `eventType`.

Communication Core должен добавить только новый route. Новый Outbox не нужен.

Важно: надёжность Outbox заканчивается в RabbitMQ. Она не делает внешний EMAIL
provider exactly-once.

### 2.4 Persistence style

`AuditableEntity` содержит:

```java
@CreatedDate Instant createdAt;
@LastModifiedDate Instant updatedAt;
@Version long version;
```

JPA entity updates заполняют audit автоматически. JPQL/native bulk update обходит
entity callbacks, поэтому conditional SQL обязан явно менять `updated_at` и `version`.

---

## 3. Binding domain model v1

### 3.1 Один Message на один CampaignRecipient

В текущем Campaign Core один `CampaignRecipient` уже содержит ровно один channel и
destination. Поэтому v1-инвариант:

```text
CampaignRecipient 1 -> 0..1 Message
```

Использовать:

```sql
CONSTRAINT uk_messages_campaign_recipient UNIQUE (campaign_recipient_id)
```

Не использовать `(campaign_recipient_id, channel)` в v1. Такой ключ разрешил бы
создать второй Message при ошибочном изменении channel у recipient и не усиливает
нормальный сценарий.

Fallback EMAIL -> SMS в будущем должен создавать отдельный CampaignRecipient либо
явную delivery plan model. Скрывать fallback вторым Message одного recipient нельзя.

### 3.2 MessageStatus

Binding enum для v1:

```java
public enum MessageStatus {
    QUEUED,
    PROCESSING,
    RETRY_WAIT,
    SENT,
    FAILED
}
```

Переходы:

```text
QUEUED -> PROCESSING
PROCESSING -> SENT
PROCESSING -> RETRY_WAIT
PROCESSING -> FAILED
RETRY_WAIT -> QUEUED
```

`CANCELLED` убрать из v1. В текущем ТЗ он объявлен terminal, но:

- отсутствует `cancelledCount`;
- отсутствует массовая остановка queued/retry messages;
- не определена гонка cancel с provider call;
- completion formula его не учитывает.

Cancellation — отдельный vertical slice после базовой доставки.

### 3.3 Поля Message

Обязательная модель:

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
Instant createdAt
Instant updatedAt
long version
```

Новые по сравнению с прежним ТЗ поля:

- `templateVersionId` — какая published locale version реально была отрендерена;
- `resolvedLocale` — результат fallback resolution;
- `processingStartedAt` — основа recovery зависшего provider call.

`destination` должен быть NOT NULL для EMAIL v1. Nullable destination уже
отбрасывается eligibility как `NO_CONTACT`; создавать Message без destination нельзя.

### 3.4 CommunicationChannel

Оставить отдельный enum:

```java
EMAIL, SMS, WHATSAPP, TELEGRAM, IN_APP
```

Не использовать `TemplateChannel` как delivery enum: он содержит `PDF` и пока не
содержит `IN_APP`. Mapping выполняется явно в application layer.

Phase 1 фактически разрешает только `EMAIL`. Наличие будущих enum values не означает
готовность соответствующего Campaign contact resolution.

---

## 4. Binding Liquibase changeSet

Следующий changeSet должен быть SQL, как текущие `022`–`025`:

```text
src/main/resources/db/changelog/changes/026-communication-delivery-core.sql
```

Минимальная структура:

```sql
CREATE TABLE messages (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    campaign_id UUID NOT NULL REFERENCES campaigns(id),
    campaign_run_id UUID NOT NULL REFERENCES campaign_runs(id),
    campaign_recipient_id UUID NOT NULL REFERENCES campaign_recipients(id),
    customer_id UUID NOT NULL REFERENCES customers(id),
    invoice_id UUID REFERENCES invoices(id),
    template_version_id UUID NOT NULL REFERENCES template_versions(id),

    channel VARCHAR(30) NOT NULL,
    destination VARCHAR(500) NOT NULL,
    resolved_locale VARCHAR(35) NOT NULL,
    subject VARCHAR(500),
    body TEXT NOT NULL,

    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    processing_started_at TIMESTAMPTZ,
    next_retry_at TIMESTAMPTZ,

    provider_message_id VARCHAR(255),
    last_error_code VARCHAR(80),
    last_error_message VARCHAR(1000),
    sent_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uk_messages_campaign_recipient UNIQUE (campaign_recipient_id),
    CONSTRAINT ck_messages_channel CHECK
        (channel IN ('EMAIL','SMS','WHATSAPP','TELEGRAM','IN_APP')),
    CONSTRAINT ck_messages_status CHECK
        (status IN ('QUEUED','PROCESSING','RETRY_WAIT','SENT','FAILED')),
    CONSTRAINT ck_messages_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_messages_run_status
    ON messages(tenant_id, campaign_run_id, status, created_at);

CREATE INDEX idx_messages_retry_due
    ON messages(next_retry_at, id)
    WHERE status = 'RETRY_WAIT';

CREATE INDEX idx_messages_processing_stale
    ON messages(processing_started_at, id)
    WHERE status = 'PROCESSING';

CREATE INDEX idx_messages_tenant_created
    ON messages(tenant_id, created_at DESC, id);

ALTER TABLE campaign_runs
    ADD COLUMN recipient_count INT NOT NULL DEFAULT 0,
    ADD COLUMN sent_count INT NOT NULL DEFAULT 0,
    ADD COLUMN failed_count INT NOT NULL DEFAULT 0,
    ADD COLUMN skipped_count INT NOT NULL DEFAULT 0,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0;

ALTER TABLE campaign_runs
    ADD CONSTRAINT ck_campaign_runs_delivery_counters CHECK (
        recipient_count >= 0 AND sent_count >= 0 AND failed_count >= 0
        AND skipped_count >= 0 AND retry_count >= 0
        AND sent_count + failed_count + skipped_count <= recipient_count
    );
```

Cross-tenant consistency остаётся application invariant: все repository commands
принимают `tenantId`, а Message создаётся только из Campaign/Run/Recipient,
загруженных этим tenant. Добавлять составные FK во все существующие таблицы ради
этого slice не требуется.

---

## 5. Content snapshot: точный исполнимый контракт

### 5.1 Новый component

```text
communication/application/CampaignMessagePayloadFactory.java
communication/application/MessageContentFactory.java
```

Contracts:

```java
public interface CampaignMessagePayloadFactory {
    JsonNode create(Customer customer, Invoice invoice);
}

public record RenderedMessage(
        UUID templateVersionId,
        String resolvedLocale,
        String subject,
        String body) {}

public interface MessageContentFactory {
    RenderedMessage render(
            UUID tenantId,
            Campaign campaign,
            CampaignRecipient recipient,
            Customer customer,
            Invoice invoice);
}
```

Не передавать JPA entity в provider. Entities допустимы только внутри content
factory transaction.

### 5.2 Normalized JSON v1

Payload должен поддержать существующие field catalog и presets:

```json
{
  "document": {
    "number": "INV-2026-001",
    "date": "2026-09-01"
  },
  "customer": {
    "name": "Example LLP",
    "externalId": "CUST-001",
    "type": "COMPANY",
    "displayName": "Example LLP",
    "firstName": null,
    "lastName": null,
    "middleName": null,
    "companyName": "Example LLP",
    "locale": "ru-KZ",
    "timezone": "Asia/Almaty"
  },
  "invoice": {
    "externalId": "INV-001",
    "invoiceNumber": "INV-2026-001",
    "invoiceDate": "2026-09-01",
    "dueDate": "2026-09-15",
    "amount": 150000.00,
    "originalAmount": 150000.00,
    "paidAmount": 50000.00,
    "outstandingAmount": 100000.00,
    "currency": "KZT",
    "paymentStatus": "PARTIALLY_PAID",
    "contractId": null,
    "documentFileId": null
  },
  "custom": {
    "customer": {},
    "invoice": {}
  }
}
```

Binding mappings:

| Placeholder | Source |
|---|---|
| `document.number` | `Invoice.invoiceNumber` |
| `document.date` | `Invoice.invoiceDate` |
| `customer.name` | `Customer.displayName` |
| `invoice.amount` | `Invoice.originalAmount` |
| `custom.customer.*` | `Customer.customFields` under `custom.customer` |
| `custom.invoice.*` | `Invoice.customFields` under `custom.invoice` |

Dates serialize as ISO `yyyy-MM-dd`; decimal values remain JSON numbers. Null field
may exist in payload, but `TemplateRenderer` correctly treats a referenced null as a
missing required value and fails Message creation atomically.

### 5.3 Locale resolution

`Campaign.templateVersionId` points to an anchor published version. Алгоритм:

1. Load anchor via `findByIdAndTenantId`.
2. Read `anchor.templateId` and channel.
3. Requested locale = `recipient.locale`; if blank, tenant default locale.
4. Call `TemplateLocaleResolver.resolve(...)`.
5. Load latest published `TemplateVersion` for resolved locale and channel through
   `findFirstByTemplateIdAndLocaleAndChannelAndStatusOrderByTemplateVersionDesc(...)`.
6. Render subject with `compileText + renderText`.
7. Render body with `TemplateRenderer.render(version, payload)`.
8. Persist resolved version ID and locale in Message.

Это использует уже существующий fallback chain и не создаёт второй localization
framework.

Для EMAIL subject обязателен. Отсутствие resolved published version — preparation
error for that recipient, а не provider retry. В v1 весь start transaction можно
отклонить, чтобы оператор исправил template/locale до отправки.

---

## 6. Message creation and transaction

Binding API:

```java
@Transactional
public CreateMessageResult createFromRecipient(
        UUID tenantId,
        Campaign campaign,
        CampaignRecipient recipient)
```

Preconditions:

```text
recipient.tenantId == tenantId
recipient.campaignId == campaign.id
recipient.status == ELIGIBLE
recipient.destination is not blank
recipient.channel == campaign.channel
```

Algorithm:

1. Check `findByCampaignRecipientId(recipient.id)`.
2. Batch-loaded Customer and Invoice должны передаваться из orchestrator; не делать
   N+1 внутри factory loop.
3. Resolve locale/template and render immutable content.
4. Save `Message(QUEUED)`.
5. Append shared Outbox event in the same transaction.
6. Flush before returning from orchestration tests, чтобы unique/check constraints
   проверялись внутри service boundary.

Outbox payload:

```json
{"messageId":"<uuid>"}
```

Application pre-check удобен, DB unique constraint является последней защитой. Это
не надо называть полной idempotency concurrent create: проигравшая transaction может
получить constraint violation. Concurrency start защищается transition/locking run.

---

## 7. Start orchestration

### 7.1 Endpoint

Точный route в существующем style:

```http
POST /api/v1/campaigns/runs/{runId}/deliver
```

Authorization:

```java
@PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_MANAGE')")
```

Response:

```json
{
  "runId": "...",
  "recipients": 100,
  "eligible": 92,
  "skipped": 8,
  "messagesCreated": 92,
  "status": "RUNNING"
}
```

### 7.2 Concurrency

Load run with a pessimistic write lock for start:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select r from CampaignRun r where r.id=:id and r.tenantId=:tenantId")
Optional<CampaignRun> findForStart(UUID tenantId, UUID id);
```

Only `READY` may transition to `RUNNING`. Second concurrent call sees `RUNNING` and
returns conflict without creating messages.

Не полагаться только на `@Version`: ошибка commit после полного rendering loop даёт
пользователю поздний и плохо объяснимый conflict.

Ввести domain exception:

```java
CampaignRunStateException
```

и map в `ApiExceptionHandler`:

```text
HTTP 409
code = CAMPAIGN_RUN_STATE_CONFLICT
```

Текущий generic handler не обрабатывает `IllegalStateException`, поэтому без этого
невалидный start вернёт HTTP 500.

### 7.3 Paging

Не использовать существующий unpaged method
`findAllByTenantIdAndRunIdOrderByCreatedAtAsc(...)` для delivery start.

Добавить:

```java
Slice<CampaignRecipient> findAllByTenantIdAndRunId(
        UUID tenantId, UUID runId, Pageable pageable);
```

Order должен быть стабильным:

```java
Sort.by(ASC, "createdAt").and(Sort.by(ASC, "id"))
```

Page size v1: `500`, configurable as
`collectra.communication.start-page-size`.

Для каждой страницы одним запросом загрузить customers, emails и invoices. Нельзя
вызывать `customers.get(...)` и `receivables.invoice(...)` на каждого recipient.

V1 оставляет один start transaction ради атомарности `RUNNING + eligibility +
Message + Outbox`. Paging ограничивает heap, но не длительность transaction. При
подтверждённой потребности в очень больших campaigns следующий slice переводит
dispatch в resumable async batches; не добавлять этот coordinator заранее.

### 7.4 Eligibility transition result

Не использовать только `wasSkipped`. Точный результат страницы:

```java
public record EligibilityPageResult(
        int processed,
        int eligible,
        int skipped,
        int newlySkipped) {}
```

Recheck разрешён только для recipient в `SNAPSHOT` или `ELIGIBLE` до создания
Message. Уже `SKIPPED` recipient остаётся terminal и не может стать ELIGIBLE снова в
том же run. Это сохраняет snapshot decision и counters.

После каждой страницы добавить только `newlySkipped` в run. Начальный
`recipientCount` фиксируется в `CampaignService.prepare` через
`run.ready(created, now)`.

---

## 8. Provider boundary and idempotency

### 8.1 Не передавать JPA Message в provider

Binding contract:

```java
public record ProviderSendCommand(
        UUID messageId,
        UUID tenantId,
        CommunicationChannel channel,
        String destination,
        String subject,
        String body,
        String idempotencyKey) {}

public interface ChannelProvider {
    CommunicationChannel channel();
    ProviderSendResult send(ProviderSendCommand command);
}
```

`idempotencyKey` для v1:

```text
messageId.toString()
```

Причины:

- provider call выполняется вне transaction, entity уже detached;
- adapter не должен видеть campaign persistence API;
- contract явно фиксирует неизменяемый payload;
- реальный provider получает стабильный deduplication key.

### 8.2 Реальная гарантия

Гарантия v1:

```text
Rabbit duplicate while Message is terminal -> provider is not called again
Concurrent consumers -> one QUEUED -> PROCESSING winner
Process crash around remote call -> at-least-once unless provider honors idempotency key
```

Нельзя заявлять exactly-once. Сценарий:

1. Provider принял EMAIL.
2. Process упал до `PROCESSING -> SENT` commit.
3. Stale recovery вернул Message на retry.
4. Без provider idempotency письмо может уйти повторно.

Mock provider должен уметь запоминать successful `idempotencyKey` в test scope,
чтобы integration test проверял recovery contract. Production adapter обязан
использовать idempotency capability провайдера, если она есть; иначе риск duplicate
должен быть явно принят для данного provider profile.

---

## 9. Worker transaction boundaries

### 9.1 TX1 claim

Conditional update обязан включать tenant-independent lookup only because Rabbit
event содержит только trusted internal `messageId`, но update должен записать время
и version:

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("""
    update Message m
       set m.status = :processing,
           m.attemptCount = m.attemptCount + 1,
           m.processingStartedAt = :now,
           m.updatedAt = :now,
           m.version = m.version + 1
     where m.id = :messageId
       and m.status = :queued
""")
int claim(UUID messageId, MessageStatus queued,
          MessageStatus processing, Instant now);
```

TX1 commit происходит до provider call.

### 9.2 Provider call

После claim загрузить immutable `ProviderSendCommand`. Provider call выполняется без
DB transaction. Adapter timeout должен быть меньше
`collectra.communication.processing-timeout`.

Unexpected runtime exception нормализуется в:

```text
RETRYABLE_ERROR / PROVIDER_EXCEPTION
```

### 9.3 TX2 result application

В TX2 использовать pessimistic lock:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select m from Message m where m.id=:id")
Optional<Message> findLockedById(UUID id);
```

Algorithm:

1. Lock Message.
2. Если status уже не `PROCESSING`, return no-op.
3. Выполнить один domain transition.
4. В той же transaction обновить CampaignRun counter.
5. Conditional-complete CampaignRun.
6. Commit.

Так duplicate result application не увеличит counter дважды. Pessimistic lock здесь
проще набора bulk status transitions и соответствует уже существующему
`GenerationJobStateService` style.

---

## 10. Retry and stale recovery

### 10.1 Retry semantics

`attemptCount` увеличивается только при успешном claim перед provider call.

`retryCount` увеличивается только при переходе:

```text
PROCESSING -> RETRY_WAIT
```

Это число запланированных дополнительных попыток, а не число всех attempts.

Defaults:

```yaml
collectra:
  communication:
    retry:
      max-attempts: 5
      delays: [PT1M, PT5M, PT15M, PT30M]
    retry-scan-ms: 5000
    retry-batch-size: 100
    processing-timeout: PT2M
    recovery-scan-ms: 60000
```

Properties validation:

```text
maxAttempts >= 1
delays is not empty
all delays > 0
processingTimeout > max provider connect+read timeout
batchSize >= 1
```

### 10.2 Retry scheduler

Query due IDs with `FOR UPDATE SKIP LOCKED` instead of сначала читать IDs, потом
делать много competing conditional updates:

```sql
SELECT *
FROM messages
WHERE status = 'RETRY_WAIT'
  AND next_retry_at <= :now
ORDER BY next_retry_at, id
FOR UPDATE SKIP LOCKED
LIMIT :batch_size;
```

Для каждого locked Message:

```text
RETRY_WAIT -> QUEUED
append MESSAGE_DELIVERY_REQUESTED
```

Обе операции в одной transaction.

### 10.3 Stale PROCESSING recovery

Добавить scheduler, аналогичный shared Outbox recovery:

```sql
SELECT *
FROM messages
WHERE status = 'PROCESSING'
  AND processing_started_at < :cutoff
ORDER BY processing_started_at, id
FOR UPDATE SKIP LOCKED
LIMIT :batch_size;
```

Для каждого stale Message:

- если `attemptCount < maxAttempts`: `PROCESSING -> RETRY_WAIT`,
  `nextRetryAt = now`, error code `PROCESSING_TIMEOUT_RECOVERED`, `retryCount++`;
- иначе: `PROCESSING -> FAILED`, error code
  `PROCESSING_TIMEOUT_MAX_ATTEMPTS`, `failedCount++`;
- затем выполнить run completion check.

Recovery не создаёт Outbox немедленно: обычный retry scheduler подхватит
`nextRetryAt=now`. Это оставляет одну точку постановки retry event.

Обязательный test имитирует crash после TX1 без вызова TX2.

---

## 11. CampaignRun counters

Binding counters:

```text
recipientCount = snapshots created during prepare
sentCount      = terminal SENT messages
failedCount    = terminal FAILED messages
skippedCount   = recipients skipped before Message creation
retryCount     = PROCESSING -> RETRY_WAIT transitions
```

Invariant:

```text
sentCount + failedCount + skippedCount <= recipientCount
```

Completion:

```text
status == RUNNING
and sentCount + failedCount + skippedCount == recipientCount
```

Atomic counter update должен включать tenant, status, audit/version:

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("""
    update CampaignRun r
       set r.sentCount = r.sentCount + :delta,
           r.updatedAt = :now,
           r.version = r.version + 1
     where r.id = :runId
       and r.tenantId = :tenantId
       and r.status = :running
""")
int incrementSent(UUID tenantId, UUID runId, int delta,
                  CampaignRunStatus running, Instant now);
```

Если affected rows != 1 — transaction должна завершиться exception; молча терять
итог provider send нельзя.

Completion query:

```sql
UPDATE campaign_runs
SET status = 'COMPLETED',
    completed_at = :now,
    updated_at = :now,
    version = version + 1
WHERE id = :run_id
  AND tenant_id = :tenant_id
  AND status = 'RUNNING'
  AND sent_count + failed_count + skipped_count = recipient_count;
```

Использовать `=`, а не `>=`: DB constraint запрещает превышение, а `>=` маскирует
реальный double-count bug.

Run с `recipientCount=0` становится `COMPLETED` в start transaction.

---

## 12. RabbitMQ topology and listener policy

Основная topology остаётся общей:

```text
exchange:    collectra.communication
queue:       collectra.communication.delivery
routing key: communication.delivery.requested
```

Добавить одну общую operational DLQ, не per-channel:

```text
queue:       collectra.communication.delivery.dead
routing key: communication.delivery.dead
```

Main queue arguments:

```text
x-dead-letter-exchange = collectra.communication
x-dead-letter-routing-key = communication.delivery.dead
```

Listener policy:

- valid event + terminal/non-claimable Message: normal return, ACK;
- valid event + applied provider result: normal return, ACK;
- invalid UUID/payload: reject without requeue -> common DLQ;
- DB temporarily unavailable before claim: throw -> container retry/requeue policy;
- exception after provider result: TX2 rollback; event may redeliver, stale recovery and
  provider idempotency protect semantics.

Set listener `defaultRequeueRejected=false` for non-transient unhandled errors либо
использовать классифицирующий error handler. Нельзя оставлять poison event в
бесконечном hot loop.

Один DLQ не нарушает правило «одна delivery queue/worker для всех каналов».

---

## 13. Read API and data protection

Routes:

```http
GET /api/v1/messages/{messageId}
GET /api/v1/messages?campaignRunId={runId}&status={status}&page=0&size=50
```

`campaignRunId` обязателен для list v1. Это не даёт случайно сканировать все
сообщения tenant и упрощает индекс.

Authorization:

```java
@PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_READ')")
```

Каждый repository query содержит `tenantId` из `TenantContext`.

List DTO:

```text
id, campaignRunId, campaignRecipientId, customerId, invoiceId,
channel, maskedDestination, status, attemptCount,
lastErrorCode, nextRetryAt, sentAt, createdAt
```

Detail DTO может содержать destination/subject/body для пользователя с
`CAMPAIGN_READ` в v1, но эти значения нельзя логировать. Перед production rollout
следует решить, нужен ли отдельный `COMMUNICATION_CONTENT_READ` permission.

Masking examples:

```text
ruslan@example.com -> r***@example.com
+77011234567       -> +7701***4567
```

Не возвращать JPA entity напрямую.

---

## 14. Operational gaps consciously deferred

### 14.1 Consent, suppression, quiet hours

Текущий codebase не содержит consent/suppression/quiet-hours model. Поэтому EMAIL
mock vertical slice допустим, но подключение реального provider не проходит
production DoD, пока перед Message creation не появится policy gate:

```text
DeliveryPolicyService.evaluate(tenant, customer, channel, now)
```

Ожидаемые terminal skip reasons будущего slice:

```text
NO_CONSENT
UNSUBSCRIBED
SUPPRESSED
QUIET_HOURS
CHANNEL_DISABLED
```

`QUIET_HOURS` обычно не terminal, а schedule decision. Точную модель не добавлять в
текущий mock slice без tenant policy tables.

### 14.2 Attachments

Текущий Message v1 хранит text/html body без attachments. FileService и RustFS уже
существуют, но attachment lifecycle, antivirus/readiness, provider size limit и
retention ещё не связаны с campaign delivery.

Следующий slice может добавить `message_attachments(message_id, stored_file_id,
file_name, content_type, size_bytes)`. Не добавлять один nullable `attachmentId` в
Message: EMAIL уже допускает несколько вложений.

### 14.3 Outbox DEAD reconciliation

Если shared Outbox исчерпал broker publish attempts, Message остаётся `QUEUED`, а run
остаётся `RUNNING`. Это безопаснее ложного `FAILED`, потому что provider ещё не был
вызван, но требует наблюдаемости.

До production provider нужны:

- metric `communication.messages.queued.age.max`;
- dashboard для `QUEUED` старше порога;
- reconciliation command, создающая новый outbox event только если для Message нет
  active (`PENDING/PROCESSING/RETRY_WAIT`) outbox event.

В первый mock slice достаточно test/metric и явного operational note; автоматический
reconciler можно реализовать hardening PR.

---

## 15. Required tests after audit

### 15.1 Unit

1. Все допустимые и запрещённые Message transitions.
2. Attempt increment exactly on claim.
3. Retry delay boundaries and configuration validation.
4. Payload factory mappings, nulls and custom fields.
5. Subject and body render use one resolved TemplateVersion.
6. Locale exact/fallback/tenant-default behavior.
7. Provider registry duplicate and missing adapter.
8. Destination masking.

### 15.2 PostgreSQL/Testcontainers integration

1. Existing `prepare -> payment/allocation -> recheck -> SKIPPED/PAID` remains green.
2. `prepare` persists `recipientCount` using fixed Clock.
3. Start success creates Message + Outbox atomically.
4. Rendering failure rolls back `RUNNING`, Message and Outbox.
5. Exact normalized payload renders invoice/customer/custom placeholders.
6. Two concurrent start calls: one success, one 409, one Message.
7. Two concurrent Rabbit claims: one provider call.
8. Duplicate Rabbit event after `SENT`: no provider call, counter remains one.
9. Retryable -> RETRY_WAIT -> scheduler -> QUEUED + one Outbox -> SENT.
10. Two retry schedulers: one requeue and one Outbox.
11. Retry exhausted -> FAILED exactly once.
12. Permanent failure -> FAILED without retry.
13. Stale PROCESSING -> RETRY_WAIT and retryCount increment.
14. Stale PROCESSING at max attempts -> FAILED and run completion.
15. Two concurrent terminal results cannot double-increment counters.
16. Last terminal outcome completes run exactly once.
17. Tenant A cannot read Message of tenant B.
18. Invalid Rabbit payload is dead-lettered, not hot-looped.

### 15.3 Test boundary

Integration tests не обязаны поднимать реальный RabbitMQ для всей state machine.
Минимум:

- Testcontainers PostgreSQL для SQL/locks/constraints;
- direct listener/service invocation для deterministic provider scenarios;
- отдельный messaging integration test с RabbitMQ только если CI environment уже
  предоставляет надёжный Rabbit container.

Нельзя подменять PostgreSQL H2 для `SKIP LOCKED`, partial indexes и concurrency tests.

---

## 16. Исправленный порядок реализации

### PR slice 1 — persistence and invariants

- `026-communication-delivery-core.sql`;
- Message entity/status/channel;
- `recipientCount` + run lifecycle Clock cleanup;
- repository tenant queries;
- domain and migration integration tests.

### PR slice 2 — content snapshot and start

- paged eligibility;
- `CampaignMessagePayloadFactory`;
- locale/template resolution;
- `MessageContentFactory`;
- Message + Outbox transaction;
- locked start + HTTP 409 mapping;
- paid-before-start and rendering rollback tests.

### PR slice 3 — worker success path

- communication exchange/main queue/common DLQ;
- Outbox router branch;
- `ProviderSendCommand` and registry;
- deterministic MockEmailProvider;
- TX1 claim / provider outside TX / locked TX2;
- duplicate and concurrent claim tests.

### PR slice 4 — retry and recovery

- validated retry properties;
- retry scheduler with `SKIP LOCKED`;
- stale PROCESSING recovery;
- retry/exhaustion/concurrency tests.

### PR slice 5 — API and operations

- paged Message read API;
- masking and tenant isolation;
- metrics for state counts, stale processing, queued age and provider outcomes;
- operational documentation.

### Later slices

- real provider + consent/suppression/quiet-hours gate;
- attachments through FileService;
- additional channel contact resolution and thin adapters;
- webhook delivery receipts;
- outbox-dead reconciliation.

Не добавлять другие mock channels до зелёного EMAIL pipeline: enum/registry уже
доказывают расширяемость, пять одинаковых adapters не снижают основной риск.

---

## 17. Updated Definition of Done v1

Communication / Delivery Core EMAIL mock v1 готов, когда:

1. Message schema содержит resolved template/locale и processing timestamp.
2. Один CampaignRecipient создаёт не более одного Message.
3. `recipientCount` фиксируется при prepare.
4. Eligibility обрабатывается страницами и считает только newly skipped.
5. Paid/no-contact/inactive recipient не создаёт Message.
6. Payload factory поддерживает существующие canonical и custom placeholders.
7. Subject/body рендерятся до enqueue и сохраняются snapshot.
8. Locale resolution использует существующий `TemplateLocaleResolver`.
9. Message и Outbox event создаются в одной transaction.
10. Start защищён DB lock и второй start получает 409.
11. Существующий Outbox публикует minimal event.
12. Одна main communication queue обслуживает EMAIL и будущие channels.
13. Один thin worker выбирает provider через registry.
14. Provider получает immutable command и stable idempotency key.
15. Provider call не выполняется внутри DB transaction.
16. Concurrent/duplicate claim не вызывает второй provider call.
17. TX2 меняет Message и CampaignRun counters atomically.
18. Retryable result не увеличивает failedCount.
19. Retry scheduler создаёт ровно один новый Outbox event.
20. Stale PROCESSING восстанавливается либо завершается FAILED на лимите.
21. Run завершается только при точном равенстве terminal counters recipientCount.
22. Invalid Rabbit event не создаёт hot redelivery loop.
23. Message API paged, tenant-scoped и не раскрывает destination в list.
24. Все обязательные PostgreSQL integration scenarios зелёные.
25. В production profile нельзя случайно активировать mock provider как default.

Подключение реального EMAIL provider не входит в этот DoD и запрещено до появления
policy gate для consent/suppression/quiet hours.

---

## 18. Финальная архитектурная оценка

После этих поправок исходное направление остаётся правильным и достаточно простым:

```text
CampaignRun READY
  -> locked start + paged eligibility
  -> immutable Message snapshot
  -> existing transactional Outbox
  -> one RabbitMQ delivery queue
  -> one MessageDeliveryWorker
  -> ChannelProviderRegistry
  -> ProviderSendCommand(messageId as idempotency key)
  -> locked Message result transition
  -> atomic CampaignRun counters/completion
```

Главная граница системы — persisted `Message`. Всё до него является бизнес-решением
campaign/eligibility/template, всё после него — технической доставкой. Эта граница
позволяет позднее вынести worker в отдельный process, не меняя Campaign Core и не
создавая сейчас лишнюю микросервисную инфраструктуру.

---

## 19. Binding decision для KumoMTA

Production EMAIL adapter планируется на **KumoMTA**. Официальная точка HTTP
инъекции — `POST /api/inject/v1`; она принимает envelope, готовый content и список
recipients. Успешная инъекция означает принятие сообщения MTA в очередь, а не
подтверждение доставки в mailbox.

Связанные официальные контракты:

- injection API: <https://docs.kumomta.com/reference/http/kumod/api_inject_v1_post/>;
- log webhooks: <https://docs.kumomta.com/userguide/operation/webhooks/>.

Binding для будущего adapter:

```text
Collectra Message.id -> KumoMTA recipient metadata.collectra_message_id
Collectra rendered subject/body -> template_dialect=Static
success_count=1, fail_count=0 -> MessageStatus.SENT
injection error before acceptance -> retryable/permanent ProviderResult
Delivery/Bounce/Expiration/Feedback -> отдельный webhook/projection slice
```

В v1 `SENT` означает «принято configured provider/MTA». Он не означает
«доставлено в ящик» и тем более «прочитано». `providerMessageId` остаётся nullable:
контракт ответа injection API возвращает aggregate counts/errors и не гарантирует
отдельный provider id для каждого принятого recipient. Корреляция строится через
stable `Message.id` в metadata.

KumoMTA имеет собственную SMTP retry queue. Её `TransientFailure` не должен
автоматически создавать новую Collectra provider attempt: иначе два retry loops
могут породить дубли. Collectra retry относится к невозможности передать message в
KumoMTA; SMTP delivery outcomes поступают позднее через log webhook.

KumoMTA client, webhook endpoint и production provider configuration не входят в
PR slice 1. В этом slice имя провайдера присутствует только в архитектурной
документации и не попадает в domain/persistence package.
