# Collectra backend specifications

Этот каталог содержит рабочие технические задания для этапов backend roadmap.

Главный принцип: **roadmap определяет порядок, spec определяет границы конкретного PR**.

| Slice | Спецификация | Статус | Зависит от |
|---|---|---|---|
| 2 | [Message processing core](slice-02-message-processing.md) | NEXT | Slice 1 — merged |
| 3 | [Message delivery messaging](slice-03-message-delivery-messaging.md) | Planned | Slice 2 |
| 4 | [KumoMTA email adapter](slice-04-kumomta-email-adapter.md) | Planned | Slice 2–3 |
| 5 | [CampaignRun → Message materialization](slice-05-message-materialization.md) | Planned | Slice 2–3 |
| 6 | [Campaign delivery counters and completion](slice-06-campaign-delivery-counters.md) | Planned | Slice 2, 5 |
| 7 | [Attachments and generated documents](slice-07-attachments-documents.md) | Planned | Slice 4–5 |
| 8 | [Delivery API and observability](slice-08-delivery-api-observability.md) | Planned | Slice 2–7 |

Общий порядок и архитектурные решения находятся в [`../roadmap/backend-mvp-roadmap.md`](../roadmap/backend-mvp-roadmap.md).

## Как использовать specs

Перед началом каждого slice:

1. сверить spec с текущим `main`;
2. удалить из scope то, что уже реализовано;
3. не расширять PR соседними задачами без необходимости;
4. после реализации обновить статус spec;
5. следующий slice начинать только после проверки его зависимостей.

## Общие правила для всех slices

- tenant scope должен быть явным в repository/application слоях;
- время брать из `Clock`, а не через прямой `Instant.now()` / `LocalDate.now()` в domain;
- внешние network calls не выполнять под PostgreSQL row lock;
- durable business state хранить в PostgreSQL, а не только в RabbitMQ headers;
- async events должны передавать durable identifiers, а не копию business state;
- domain invariants не дублировать в listener/controller/provider adapter;
- для больших выборок использовать bounded pages/batches;
- каждый slice должен иметь unit/integration tests по своему риску;
- завершение slice — зелёный `mvn verify` и узкий PR без auto-merge.

Для новых спецификаций использовать [`_template.md`](_template.md).
