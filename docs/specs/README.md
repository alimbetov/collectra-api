# Collectra backend specifications

Этот каталог содержит короткие технические задания для этапов backend roadmap.

| Slice | Спецификация | Статус |
|---|---|---|
| 2 | [Message processing core](slice-02-message-processing.md) | NEXT |
| 3 | [Message delivery messaging](slice-03-message-delivery-messaging.md) | Planned |
| 4 | [KumoMTA email adapter](slice-04-kumomta-email-adapter.md) | Planned |
| 5 | [CampaignRun to Message materialization](slice-05-message-materialization.md) | Planned |
| 6 | [Campaign delivery counters and completion](slice-06-campaign-delivery-counters.md) | Planned |
| 7 | [Attachments and generated documents](slice-07-attachments-documents.md) | Planned |
| 8 | [Delivery API and observability](slice-08-delivery-api-observability.md) | Planned |

Общий порядок, зависимости и архитектурные решения находятся в [`../roadmap/backend-mvp-roadmap.md`](../roadmap/backend-mvp-roadmap.md).

## Правило работы

Перед началом slice сверяем ТЗ с текущим `main`. Если код уже реализовал часть требований, не строим её повторно — корректируем ТЗ и реализуем только недостающий scope.

Каждый slice должен оставаться узким PR и завершаться зелёным `mvn verify`.
