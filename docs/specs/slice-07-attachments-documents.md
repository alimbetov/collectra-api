# Slice 7 — Attachments and generated documents integration

## Цель

Связать существующий document/PDF/FileService pipeline с email delivery.

## Что сделать

- определить, какие Message требуют attachment;
- использовать существующий document generation flow;
- после генерации сохранить immutable attachment reference;
- enqueue `MESSAGE_DELIVERY_REQUESTED` только когда обязательные attachments готовы;
- передавать KumoMTA adapter только готовые attachment descriptors;
- QR оставлять частью document/template preparation.

## Минимальная модель

Если текущей модели недостаточно, добавить таблицу наподобие:

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

Точный FK выбрать по существующей FileService/document модели, не дублируя метаданные файла.

## Поток

```text
materialization
  -> document generation request
  -> document worker
  -> RustFS/FileService
  -> attachment reference
  -> MESSAGE_DELIVERY_REQUESTED
```

## Не делать

- не генерировать PDF в KumoMTA adapter;
- не хранить бинарные файлы в messages;
- не создавать второй file storage abstraction без необходимости.

## Тесты

- сообщение не отправляется раньше обязательного attachment;
- attachment принадлежит тому же tenant;
- повторная генерация/consumer redelivery не создаёт лишние attachment rows;
- удалённый/недоступный файл даёт управляемую ошибку.

## Definition of Done

EMAIL с attachment использует существующий document/FileService pipeline и не смешивает document generation с provider delivery. `mvn verify` зелёный.
