# Slice 7 — Attachments and generated documents

Status: Planned

Depends on: `slice-04-kumomta-email-adapter.md`, `slice-05-message-materialization.md`

Suggested branch: `feat/message-attachments`

## Цель

Связать существующий document/PDF/FileService pipeline с EMAIL delivery так, чтобы сообщение отправлялось только после готовности обязательных attachments.

## Уже есть

Переиспользуем:

- document generation worker;
- PDF generation pipeline;
- FileService metadata;
- RustFS storage;
- Outbox/RabbitMQ infrastructure;
- KumoMTA adapter;
- Message materialization.

## Scope

### 1. Attachment requirement

Во время materialization определить, нужен ли `Message` обязательный attachment.

Если attachment не нужен — текущий delivery flow не меняется.

Если нужен — delivery request нельзя публиковать до готовности файла.

### 2. Attachment reference

Если существующей модели недостаточно, добавить минимальную persistence модель, например:

```text
message_attachments
- id
- tenant_id
- message_id
- file_id / generated_output_id
- filename
- content_type
- created_at
```

Не дублировать binary content и существующие FileService metadata.

### 3. Generation flow

Для Message с generated attachment:

```text
materialization
  -> document generation request
  -> existing document worker
  -> FileService/RustFS
  -> persist Message attachment reference
  -> append MESSAGE_DELIVERY_REQUESTED
```

Attachment reference и переход к delivery должны быть restart-safe/idempotent.

### 4. Delivery mapping

Расширить `DeliveryCommand` только минимально необходимыми immutable attachment descriptors.

KumoMTA adapter получает готовый файл/reference/content stream через существующий file abstraction и не запускает document generation.

### 5. Failure handling

Нужно различать:

```text
document generation failed
file reference invalid/missing
delivery provider rejected attachment
```

Document generation failure не должен маскироваться как KumoMTA network failure.

## Инварианты и правила

- tenant Message и attachment/file должны совпадать;
- обязательный attachment должен быть READY до enqueue delivery;
- binary content не хранится в `messages`;
- repeated document event не создаёт duplicate attachment rows;
- provider adapter не генерирует документы;
- QR/PDF остаются ответственностью document/template subsystem;
- удалённый/expired file должен давать контролируемую ошибку, а не NPE/404 leakage.

## Не входит

- новый object storage;
- redesign FileService;
- inline images/CID, если они не требуются первым реальным шаблоном;
- arbitrary user attachment upload redesign;
- attachment support для SMS/Telegram/WhatsApp.

## Тесты

Обязательные:

```text
MessageAttachmentIntegrationTest
- Message без attachment сразу готов к delivery
- Message с required attachment не enqueue-ится раньше READY
- generated file reference сохраняется
- tenant mismatch rejected
- duplicate generation/redelivery idempotent
- missing/deleted file -> controlled failure

KumoMtaEmailDeliveryGatewayTest
- ready attachment mapped to provider request
```

## Definition of Done

- обязательный attachment блокирует delivery до готовности;
- используется существующий document/FileService/RustFS pipeline;
- attachment metadata не дублируют file storage model;
- повторная обработка не создаёт duplicates;
- KumoMTA получает только готовые attachments;
- document generation и provider delivery остаются разделёнными;
- `mvn verify` зелёный.
