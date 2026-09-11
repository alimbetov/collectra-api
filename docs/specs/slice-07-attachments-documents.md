# Slice 7 — Attachments and generated documents

Status: PLANNED  
Depends on: Slice 3, Slice 4, Slice 5, existing Document/FileService pipeline
Suggested branch: `feat/message-attachments`

Entry gate: supported-locale PDF rendering must be operational, not merely return
bytes with a `%PDF` header. The full-suite log currently contains
`Couldn't load font (Noto Sans CJK SC)` for bundled CJK `.otf` resources. Before
Slice 7, fix or explicitly de-scope that locale and add a glyph-level smoke test;
otherwise an attachment may be marked `READY` while containing missing Chinese
glyphs.

## 1. Цель

Подключить существующий async document generation + FileService/RustFS pipeline к EMAIL delivery так, чтобы Message отправлялся только после готовности обязательных attachments.

Slice 7 не строит новый PDF engine и не переносит storage logic в communication module.

## 2. Текущий baseline

Уже есть:

- document generation request/worker/state machine;
- PDF/HTML generation pipeline;
- FileService metadata lifecycle для обычных файлов;
- `GeneratedDocument` + `GeneratedOutputService` + `DocumentStorage` для текущих
  generated outputs (этот path пока не использует `StoredFile`);
- RustFS/MinIO-compatible object storage;
- Outbox/RabbitMQ document messaging;
- Message materialization;
- KumoMTA adapter без attachments из Slice 4.

## 3. Scope

Добавить persistence для immutable relation Message -> attachment.

Предлагаемая модель:

```text
communication.domain.MessageAttachment
communication.infrastructure.MessageAttachmentRepository
communication.application.MessageAttachmentService
```

Liquibase migration:

```text
027/next-free-number-message-attachments
```

Номер migration выбрать фактически следующий в `main` на момент реализации.

## 4. Таблица message_attachments

Минимальная schema:

```text
message_attachments
- id UUID PK
- tenant_id UUID NOT NULL
- message_id UUID NOT NULL
- generation_job_id UUID NOT NULL
- generated_document_id UUID NULL
- filename varchar(...) NOT NULL
- content_type varchar(...) NOT NULL
- required boolean NOT NULL DEFAULT true
- status varchar(...) NOT NULL  # PENDING, READY, FAILED
- created_at timestamptz NOT NULL
```

Не хранить одновременно произвольные storage URL и raw binary.

`generation_job_id` является durable correlation для ещё не готового requirement.
При `READY` поле `generated_document_id` обязательно и ссылается на
`generated_documents(id)`. Это устраняет недолговечную JSON-only correlation и
позволяет отличить «attachment не требуется» от «обязательный attachment ещё
генерируется».

Не дублировать `GeneratedDocument` metadata/storage key в attachment row.

## 5. Constraints

Минимально:

```text
FK message_id -> messages.id
unique(message_id, generation_job_id)
unique(message_id, generated_document_id) WHERE generated_document_id IS NOT NULL
CHECK ((status = 'READY') = (generated_document_id IS NOT NULL))
```

Tenant consistency должна быть проверена application layer и, где возможно, FK/model constraints.

Не разрешать attachment другого tenant.

## 6. Attachment state model

Не дублировать полный lifecycle document job. Локальные `PENDING/READY/FAILED`
фиксируют только readiness relation Message -> generated output и меняются через
intent methods.

Для Message достаточно понимать:

```text
required relation PENDING/FAILED -> NOT READY
all required relations READY     -> READY FOR DELIVERY
```

Если нужен failed generation state, source of truth остаётся document generation job.

## 7. Materialization integration

Campaign materialization должен определить, нужен ли recipient/message document attachment.

Flow:

```text
Campaign recipient
  -> Message created
  -> attachment requirement detected
  -> document generation requested
  -> NO MESSAGE_DELIVERY_REQUESTED yet
```

Если attachment не требуется:

```text
Message created
  -> MESSAGE_DELIVERY_REQUESTED immediately
```

Таким образом Message existence и readiness for delivery — разные понятия.

## 8. Document generation request

Не передавать весь Message body/business object в document broker event, если existing document pipeline работает по durable IDs/context snapshot.

Нужно использовать existing document request contract и связать generated output с `messageId`/attachment intent.

Correlation хранится в `message_attachments.generation_job_id`; менять payload
существующего `DOCUMENT_GENERATION_REQUESTED` не требуется.

## 9. Completion callback/orchestration

После successful document generation:

```text
document output ready
  -> resolve durable file/output reference
  -> create MessageAttachment
  -> check all required attachments ready
  -> append MESSAGE_DELIVERY_REQUESTED
```

Операции:

```text
attachment relation creation
+
Message delivery OutboxEvent
```

должны быть atomic в одной DB transaction после того, как все required attachments готовы.

Текущий `DocumentGenerationWorker` не публикует completion event. В этом Slice
добавить `DOCUMENT_GENERATION_COMPLETED {tenantId, jobId}` через существующий
Outbox после durable `GenerationJob COMPLETED`; отдельный completion listener
разрешает attachment relation и создаёт delivery event. Document module не должен
импортировать communication application classes напрямую.

При затрагивании `GenerationJob`/`GeneratedDocument` убрать внутренний
`Instant.now()` из новых/изменяемых transition paths: timestamp передаётся из
application `Clock`, как и в communication flow. Completion lookup/event всегда
tenant-scoped; существующий `begin(jobId)` без tenant scope нельзя переиспользовать
в новом attachment callback path.

## 10. Idempotency

Повторный document completion event / worker redelivery:

- не создаёт duplicate MessageAttachment;
- не создаёт второй delivery Outbox event после Message уже стал eligible/queued for delivery;
- не генерирует второй PDF без business reason.

Нужен unique constraint + application check.

Для exactly-one delivery signal добавить DB idempotency key для Outbox creation
(предпочтительно unique logical key `MESSAGE_DELIVERY_REQUESTED + messageId`) либо
durable `delivery_requested_at`/equivalent marker у Message. Обычного
`exists()` перед insert недостаточно при concurrent completion events.

Если one Message has multiple attachments, delivery event создаётся только после последнего required attachment.

## 11. KumoMTA command extension

Расширить provider-neutral `DeliveryCommand` только на immutable attachment descriptors, например:

```java
public record DeliveryAttachment(
        String filename,
        String contentType,
        byte[] content) {}
```

или лучше, чтобы не держать большие файлы дольше нужного:

```java
public record DeliveryAttachment(
        String filename,
        String contentType,
        AttachmentContent content) {}
```

Но не вводить generic streaming framework без необходимости.

Для MVP допустимо прочитать bounded attachment bytes непосредственно перед Kumo call, если установлены size limits.

## 12. Attachment size limits

Обязательна configuration:

```yaml
collectra:
  communication:
    attachments:
      max-file-size: 10MB
      max-total-size: 20MB
      max-count: 10
```

Точные defaults можно скорректировать под deployment/provider policy.

До provider call проверить:

- count;
- per-file size;
- total size;
- content type present;
- object exists.

Oversized attachment -> permanent delivery/materialization failure с понятным error code, не бесконечный retry.

## 13. File access

Для generated attachment communication layer получает bytes через существующий
`GeneratedOutputService`/`DocumentStorage` abstraction. Для обычного uploaded
file — через `FileService`; эти два durable source type нельзя угадывать по URL.

Не обращаться напрямую к RustFS SDK из `KumoMtaEmailDeliveryGateway`, если FileService уже является storage boundary.

Правильный dependency direction:

```text
Kumo adapter
  <- receives already resolved attachment content/descriptor

Message attachment application service
  -> GeneratedOutputService or FileService
  -> existing storage implementation
```

## 14. KumoMTA attachment mapping

Использовать attachment contract `POST /api/inject/v1` structured content.

Для каждого attachment передать:

```text
filename
content type
content/base64 according to actual Kumo HTTP schema
```

Перед реализацией сверить текущую Kumo version schema `Attachment`, потому что wire representation зависит от API contract.

Не использовать template substitution для attachment content.

## 15. File retention interaction

Соответствующий cleanup path не должен удалить required attachment, пока Message
ещё ожидает delivery/retry. Сейчас generated outputs не управляются
`FileCleanupService`, поэтому retention guard добавляется в фактический
`GeneratedDocument` cleanup либо generated output сначала осознанно мигрируется в
`StoredFile`; смешанный полу-переход запрещён.

Нужно определить retention guard:

```text
Message terminal (SENT/FAILED) + retention period elapsed
```

или существующая document/file retention policy должна гарантировать достаточный TTL.

Не добавлять отдельный cleanup engine; интегрировать reference/use check в существующий FileService cleanup contract при необходимости.

## 16. Failure scenarios

### Document generation failed

```text
Message не отправляется
Campaign/message processing получает terminal or operational failure according to existing document retry policy
```

Не enqueue delivery до успешного generation.

### File metadata exists, object missing

```text
ATTACHMENT_NOT_FOUND
```

Это не provider retryable error. Сначала классифицировать как storage/data failure и не слать incomplete email.

### Temporary storage read failure

Может быть retryable technical delivery failure, если object expected to exist.

### Optional attachment

Если business model разрешает optional attachments, их failure не блокирует message только при явно зафиксированном `required=false`.

## 17. Security

- attachment lookup tenant-scoped;
- Message tenant == attachment/file tenant;
- filenames sanitised before Content-Disposition/provider mapping;
- no local filesystem path leakage;
- MIME type from metadata/validated source, не доверять blindly user filename extension;
- storage credentials never leave infrastructure.

## 18. Database indexes

Минимально:

```text
index message_attachments(tenant_id, message_id)
unique durable relation key
```

Не добавлять indexes без query use case.

## 19. Tests

### Domain/application

- message with no attachment -> delivery can enqueue immediately;
- required attachment -> delivery blocked until ready;
- optional attachment semantics;
- cross-tenant attachment rejected.

### PostgreSQL integration

- document completion creates one attachment row;
- duplicate completion -> no duplicate row;
- final required attachment -> exactly one delivery outbox event;
- transaction rollback -> no partial relation/outbox divergence.

### Storage integration

- file read success;
- file missing;
- temporary read failure classification;
- size limit;
- total size/count limit.

### Generated document readiness

- every enabled attachment locale renders without font-load warnings;
- CJK smoke test verifies the expected glyphs/text in the generated PDF, not only
  the `%PDF` signature and file size;
- a renderer/font failure leaves the required attachment non-`READY` and prevents
  delivery event creation.

### Kumo adapter

- attachment mapped to inject request;
- filename/content type preserved;
- body/subject unchanged;
- multiple attachments supported within configured limits.

### Retention

- referenced non-terminal attachment not deleted by cleanup path;
- terminal/expired attachment follows existing retention rules.

## 20. Порядок реализации

1. inspect actual FileService/document output model;
2. Liquibase `message_attachments`;
3. entity/repository;
4. attachment readiness service;
5. materialization/document correlation;
6. document completion -> attachment relation + Outbox;
7. size/security validation;
8. extend DeliveryCommand/Kumo mapping;
9. retention integration;
10. integration/idempotency tests;
11. `mvn verify`.

## 21. Out of scope

- rebuilding PDF engine;
- direct RustFS use from provider adapter;
- inline image/CID advanced rendering unless current templates require it;
- public download API redesign;
- antivirus/DLP subsystem;
- arbitrary remote URL attachment fetching.

## 22. Definition of Done

Slice 7 готов, если:

- Message can have durable tenant-scoped attachment references;
- required documents block delivery until ready;
- document completion is idempotent;
- final readiness creates exactly one delivery Outbox event;
- provider receives attachment content within configured limits;
- missing/oversized attachment has deterministic failure semantics;
- FileService/RustFS remains storage boundary;
- retention does not delete active required files;
- enabled locales pass glyph-level PDF readiness tests without silent fallback;
- `mvn verify` green.
