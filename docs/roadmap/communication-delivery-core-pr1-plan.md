# PR slice 1 — Message persistence and invariants

## 0. Цель

Реализовать только persistence foundation для Communication / Delivery Core:

- таблицу `messages`;
- доменную сущность `Message`;
- status/channel enums;
- repository;
- delivery counters в `CampaignRun`;
- перевод lifecycle `CampaignRun` на application `Clock`;
- PostgreSQL integration tests и domain unit tests.

В этом PR нет:

- RabbitMQ topology/listener;
- создания Outbox event для Message;
- `MessageDeliveryWorker`;
- mock email provider;
- KumoMTA HTTP client;
- retry/recovery schedulers;
- Message REST API;
- template rendering и CampaignRecipient -> Message orchestration.

После PR slice 1 приложение не начинает отправлять сообщения. Оно получает устойчивую
модель, на которую опираются следующие vertical slices.

---

## 1. Ветка и граница PR

Рабочая ветка кода:

```text
feat/communication-message-persistence
```

Создавать от актуального `main`. В PR не смешивать рефакторинг Campaign selection,
RabbitMQ и provider integration.

Ожидаемая последовательность commits:

```text
1. feat(communication): add message persistence schema
2. feat(communication): add message domain and repository
3. feat(campaign): add delivery counters and clock-based lifecycle
4. test(communication): cover message persistence invariants
```

---

## 2. Решение с учётом KumoMTA

Будущий EMAIL adapter будет работать с **KumoMTA**.

KumoMTA — Message Transfer Agent. Его HTTP injection API принимает сообщение в
собственную durable queue. Успешный HTTP response означает, что KumoMTA принял
сообщение для дальнейшей SMTP-доставки, но ещё не подтверждает попадание в mailbox.

Поэтому семантика v1 фиксируется так:

```text
MessageStatus.SENT = сообщение принято configured channel provider/MTA
```

Для KumoMTA это будет:

```text
POST /api/inject/v1
success_count = 1
fail_count = 0
-> Message SENT
```

Фактические результаты KumoMTA позже приходят отдельным log webhook:

```text
Delivery          -> delivered metric/status projection
Bounce            -> permanent delivery failure
TransientFailure  -> KumoMTA продолжает собственный SMTP retry
Expiration        -> delivery expired
Feedback          -> complaint/suppression input
```

Не добавлять `DELIVERED`, `BOUNCED` и webhook tables в PR slice 1. Но в коде и API
нельзя описывать `SENT` как «письмо прочитано» или «доставлено в ящик».

KumoMTA integration не должна использовать Kumo template substitution: Collectra
сохраняет уже отрендеренные subject/body snapshot. Будущий request использует
`template_dialect = Static`.

---

## 3. Шаг 1 — Liquibase 026

Создать:

```text
src/main/resources/db/changelog/changes/026-communication-delivery-core.sql
```

Добавить include последним в:

```text
src/main/resources/db/changelog/db.changelog-master.yaml
```

### 3.1 Таблица messages

```sql
--liquibase formatted sql

--changeset collectra:026-communication-delivery-core
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
    CONSTRAINT ck_messages_channel CHECK (
        channel IN ('EMAIL','SMS','WHATSAPP','TELEGRAM','IN_APP')),
    CONSTRAINT ck_messages_status CHECK (
        status IN ('QUEUED','PROCESSING','RETRY_WAIT','SENT','FAILED')),
    CONSTRAINT ck_messages_attempt_count CHECK (attempt_count >= 0)
);
```

### 3.2 Indexes

```sql
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
```

Не добавлять generic JSON provider response, attachment column и per-channel tables.

### 3.3 CampaignRun counters

```sql
ALTER TABLE campaign_runs
    ADD COLUMN recipient_count INT NOT NULL DEFAULT 0,
    ADD COLUMN sent_count INT NOT NULL DEFAULT 0,
    ADD COLUMN failed_count INT NOT NULL DEFAULT 0,
    ADD COLUMN skipped_count INT NOT NULL DEFAULT 0,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0;

ALTER TABLE campaign_runs
    ADD CONSTRAINT ck_campaign_runs_delivery_counters CHECK (
        recipient_count >= 0
        AND sent_count >= 0
        AND failed_count >= 0
        AND skipped_count >= 0
        AND retry_count >= 0
        AND sent_count + failed_count + skipped_count <= recipient_count
    );
```

Не добавлять `queued_count`, `processing_count`, `retry_wait_count`: они являются
текущим распределением `messages.status` и не должны дублироваться mutable counters.

### 3.4 Migration acceptance

Проверить на PostgreSQL:

1. Liquibase применяет changeSet на пустой DB.
2. Hibernate `ddl-auto=validate` проходит.
3. Duplicate `campaign_recipient_id` отклоняется.
4. Invalid status/channel отклоняются DB constraint.
5. Negative attempt/counter отклоняется.
6. Terminal counters нельзя увеличить выше `recipient_count`.

---

## 4. Шаг 2 — Domain enums

Создать:

```text
src/main/java/io/collectra/api/communication/domain/CommunicationChannel.java
src/main/java/io/collectra/api/communication/domain/MessageStatus.java
```

```java
public enum CommunicationChannel {
    EMAIL,
    SMS,
    WHATSAPP,
    TELEGRAM,
    IN_APP
}
```

```java
public enum MessageStatus {
    QUEUED,
    PROCESSING,
    RETRY_WAIT,
    SENT,
    FAILED
}
```

`PDF` не является delivery channel. `CANCELLED` не добавлять, пока не определены
cancel command, гонка с provider call и `cancelledCount`.

---

## 5. Шаг 3 — Message entity

Создать:

```text
src/main/java/io/collectra/api/communication/domain/Message.java
```

Entity наследует `AuditableEntity` и mapping полностью совпадает с Liquibase.

### 5.1 Constructor/factory

Использовать named factory:

```java
public static Message queued(
        UUID tenantId,
        UUID campaignId,
        UUID campaignRunId,
        UUID campaignRecipientId,
        UUID customerId,
        UUID invoiceId,
        UUID templateVersionId,
        CommunicationChannel channel,
        String destination,
        String resolvedLocale,
        String subject,
        String body) {
    return new Message(...);
}
```

Начальное состояние:

```text
status = QUEUED
attemptCount = 0
processingStartedAt = null
nextRetryAt = null
providerMessageId = null
sentAt = null
```

### 5.2 Constructor invariants

Обязательны:

```text
tenantId
campaignId
campaignRunId
campaignRecipientId
customerId
templateVersionId
channel
destination non-blank
resolvedLocale non-blank
body non-blank
```

`invoiceId` nullable для будущих customer-care campaigns.

`subject`:

- для `EMAIL` обязателен;
- для остальных channel может быть null.

Строки trim, error fields truncate до DB limits.

### 5.3 Domain transitions

Добавить методы сейчас, даже если worker появится в следующем PR:

```java
public void beginAttempt(Instant now)
public void markSent(String providerMessageId, Instant now)
public void scheduleRetry(Instant nextRetryAt, String code, String message)
public void markFailed(String code, String message)
public void requeue()
```

Поведение:

```text
beginAttempt:
  QUEUED -> PROCESSING
  attemptCount++
  processingStartedAt = now

markSent:
  PROCESSING -> SENT
  sentAt = now
  processingStartedAt = null
  nextRetryAt = null
  clear last error

scheduleRetry:
  PROCESSING -> RETRY_WAIT
  processingStartedAt = null
  nextRetryAt required
  save error

markFailed:
  PROCESSING -> FAILED
  processingStartedAt = null
  nextRetryAt = null
  save error

requeue:
  RETRY_WAIT -> QUEUED
  nextRetryAt = null
```

Каждый недопустимый переход бросает `IllegalStateException`.

`providerMessageId` остаётся nullable даже после `SENT`: KumoMTA HTTP injection
response сообщает counts, но не гарантирует возврат отдельного ID каждого принятого
message. Корреляция KumoMTA будет строиться через metadata `collectra_message_id`.

---

## 6. Шаг 4 — MessageRepository

Создать:

```text
src/main/java/io/collectra/api/communication/infrastructure/MessageRepository.java
```

В PR slice 1 достаточно:

```java
public interface MessageRepository extends JpaRepository<Message, UUID> {

    Optional<Message> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Message> findByCampaignRecipientId(UUID campaignRecipientId);

    Slice<Message> findAllByTenantIdAndCampaignRunId(
            UUID tenantId,
            UUID campaignRunId,
            Pageable pageable);

    long countByTenantIdAndCampaignRunIdAndStatus(
            UUID tenantId,
            UUID campaignRunId,
            MessageStatus status);
}
```

Правила:

- user/application read queries всегда содержат `tenantId`;
- list только paged;
- generic `Specification`/query DSL не добавлять;
- atomic claim и `SKIP LOCKED` queries добавить в worker/retry PR, где появятся их
  application callers и concurrency tests.

---

## 7. Шаг 5 — CampaignRun counters и Clock

Изменить:

```text
campaign/domain/CampaignRun.java
campaign/infrastructure/CampaignRunRepository.java
campaign/application/CampaignService.java
```

### 7.1 Fields/getters

Добавить:

```java
private int recipientCount;
private int sentCount;
private int failedCount;
private int skippedCount;
private int retryCount;
```

### 7.2 Lifecycle signatures

Убрать `Instant.now()` из entity:

```java
public void ready(int recipientCount, Instant now)
public void start(Instant now)
public void complete(Instant now)
public void fail(Instant now)
public void cancel(Instant now)
```

Все callers получают `now` через:

```java
Instant.now(clock)
```

`ready(...)` проверяет `recipientCount >= 0`.

### 7.3 CampaignService.prepare

Заменить:

```java
run.ready();
```

на:

```java
run.ready(created, Instant.now(clock));
```

`created` уже вычисляется после page loop и является точным числом persisted
CampaignRecipient snapshots.

### 7.4 Граница counter logic

В PR slice 1 добавить mapping, getters и установку `recipientCount` при `ready(...)`.
Остальные counters остаются равными нулю: пока нет worker result transaction, их
некому корректно изменять.

Не добавлять заранее repository bulk updates `incrementSent`, `incrementFailed`,
`incrementRetry` и `completeIfFinished`. Они входят в PR slice 3 вместе с:

- row lock победившего Message transition;
- tenant/status guards;
- явным обновлением `updatedAt` и `version`;
- проверкой точного равенства
  `sentCount + failedCount + skippedCount == recipientCount`;
- concurrency и duplicate-delivery tests.

Так в PR slice 1 не появляется неиспользуемый persistence API без доказанных
transaction boundaries.

---

## 8. Шаг 6 — Unit tests

Создать:

```text
src/test/java/io/collectra/api/communication/domain/MessageTest.java
src/test/java/io/collectra/api/campaign/domain/CampaignRunTest.java
```

### 8.1 MessageTest

Проверить:

1. `queued(...)` создаёт начальное состояние.
2. EMAIL без subject отклоняется.
3. Blank destination/body/locale отклоняются.
4. `beginAttempt` увеличивает attempt ровно один раз.
5. `QUEUED -> PROCESSING -> SENT`.
6. `QUEUED -> PROCESSING -> RETRY_WAIT -> QUEUED`.
7. `PROCESSING -> FAILED`.
8. `SENT -> PROCESSING` запрещён.
9. `FAILED -> QUEUED` запрещён.
10. Retry/error fields очищаются при success.
11. Error message обрезается до 1000 символов.
12. `processingStartedAt` устанавливается и очищается.

### 8.2 CampaignRunTest

Проверить:

1. New run = `PREPARING`, counters zero.
2. `ready(10, fixedInstant)` сохраняет count/time.
3. Negative recipientCount отклоняется.
4. `READY -> RUNNING` использует переданный Instant.
5. `RUNNING -> COMPLETED` использует переданный Instant.
6. Невалидные lifecycle transitions отклоняются.

Ни один тест не использует системное время.

---

## 9. Шаг 7 — PostgreSQL integration tests

Создать:

```text
src/test/java/io/collectra/api/CommunicationMessagePersistenceIntegrationTest.java
```

Использовать существующий `AbstractIntegrationTest` и Testcontainers PostgreSQL.

Scenarios:

1. Создать tenant/template/customer/invoice/campaign/run/recipient fixture.
2. Persist `Message.queued(...)` и reload по `id + tenantId`.
3. Проверить все FK/snapshot fields.
4. Проверить auditing/version.
5. Второй Message для того же recipient получает unique constraint violation.
6. Tenant B не находит Message tenant A.
7. Paged run query возвращает только сообщения нужного run/tenant.
8. Liquibase/Hibernate schema validation проходит.
9. Campaign prepare сохраняет `recipientCount`.
10. Существующий paid-allocation stabilization test остаётся зелёным.

Для DB constraint tests обязательно делать `saveAndFlush`, иначе exception может
возникнуть уже после assertion boundary.

---

## 10. Шаг 8 — Architecture test

Обновить существующий ArchUnit test либо добавить правило:

```text
communication.domain не зависит от campaign.application,
RabbitTemplate, HTTP client или provider package
```

Разрешённые зависимости Message domain:

```text
java.*
jakarta.persistence.*
shared.persistence.AuditableEntity
communication.domain.*
```

В PR slice 1 package `communication` не должен зависеть от KumoMTA.

---

## 11. Локальная и CI-проверка

Перед push:

```bash
mvn -B -ntp spotless:check
mvn -B -ntp test
mvn -B -ntp verify
```

Проверить отдельно:

```text
CampaignStabilizationIntegrationTest
CommunicationMessagePersistenceIntegrationTest
MessageTest
CampaignRunTest
```

Не маскировать отсутствие Docker. Если Testcontainers не стартует, фиксировать
реальную infrastructure cause, а не объявлять код зелёным.

---

## 12. Definition of Done PR slice 1

PR можно открывать в `main`, когда:

1. `026-communication-delivery-core.sql` применяется PostgreSQL.
2. Hibernate schema validation проходит.
3. Message mapping полностью совпадает с migration.
4. Один recipient имеет максимум один Message.
5. Все Message transitions защищены domain invariants.
6. `processingStartedAt` присутствует для будущего recovery.
7. Message read repository tenant-scoped и paged.
8. CampaignRun хранит пять согласованных counters.
9. `CampaignService.prepare` сохраняет `recipientCount`.
10. `CampaignRun` больше не вызывает `Instant.now()` внутри entity.
11. Fixed Clock tests проходят.
12. Unit tests transitions зелёные.
13. PostgreSQL persistence/constraint tests зелёные.
14. Existing campaign stabilization test зелёный.
15. Full `mvn verify` зелёный.
16. В PR отсутствуют RabbitMQ, Outbox message event, worker и KumoMTA client.
17. PR открыт в `main`, но не merged автоматически.

---

## 13. Что будет после PR slice 1

Следующий slice:

```text
PR slice 2 — content snapshot and campaign delivery start
```

Только после него:

```text
PR slice 3 — RabbitMQ worker + MockEmailProvider
```

KumoMTA adapter — отдельный production-provider slice после зелёного mock pipeline.
Его будущий контракт:

```text
Collectra rendered snapshot
-> POST KumoMTA /api/inject/v1 with template_dialect=Static
-> metadata.collectra_message_id = Message.id
-> success_count=1 means SENT (accepted by MTA)
-> KumoMTA log webhook reports Delivery/Bounce/Expiration/Feedback
```

Так KumoMTA остаётся инфраструктурным EMAIL adapter и не меняет Campaign Core,
Message persistence либо общий worker для остальных каналов.
