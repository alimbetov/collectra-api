# Slice 5 — CampaignRun to Message materialization

## Цель

Создавать durable `Message` из подготовленного `CampaignRun` и существующего recipient snapshot.

## Что сделать

- добавить `CampaignMessageMaterializer`;
- читать recipients страницами/батчами;
- выполнить финальную eligibility recheck там, где это требуется;
- определить destination и locale;
- использовать существующий `TemplateRenderer`;
- сохранить rendered `subject/body` в `Message` как snapshot;
- сохранить `Message` и `MESSAGE_DELIVERY_REQUESTED` в одной транзакции батча;
- обеспечить idempotency повторного запуска.

## Поток

```text
CampaignRun
  -> recipients page
  -> eligibility
  -> destination + locale + template
  -> render subject/body
  -> Message.queued(...)
  -> Outbox MESSAGE_DELIVERY_REQUESTED
```

## Idempotency

Для EMAIL-only MVP использовать текущую уникальность `campaign_recipient_id`.

Перед multi-channel fan-out отдельно изменить constraint, например на:

```text
unique(campaign_recipient_id, channel)
```

Сейчас заранее это не менять.

## Не делать

- не рендерить шаблон повторно при отправке;
- не загружать весь CampaignRun в память;
- не вводить новый template engine;
- не выполнять provider call из materializer.

## Тесты

- повторный materialization не создаёт дубликаты;
- rendered subject/body сохраняются;
- tenant isolation;
- eligibility skip работает;
- большие выборки идут bounded pages;
- Message и Outbox не расходятся при rollback.

## Definition of Done

CampaignRun можно превратить в durable EMAIL messages идемпотентно и батчами, используя существующий template subsystem. `mvn verify` зелёный.
