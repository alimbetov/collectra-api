# Slice 3 — Message delivery messaging

## Цель

Подключить существующий Outbox и RabbitMQ к обработке `Message`, не создавая второй механизм очередей.

## Что сделать

- добавить событие `MESSAGE_DELIVERY_REQUESTED`;
- payload: только `tenantId` и `messageId`;
- добавить route в существующий `OutboxEventRouter`;
- добавить `CommunicationMessagingConfig`;
- добавить тонкий `MessageDeliveryListener`, который вызывает `MessageDeliveryWorker`;
- persisted `Message.attemptCount` и `nextRetryAt` оставить источником истины для retry.

## Не делать

- не передавать `subject/body/customer` через RabbitMQ;
- не дублировать Outbox;
- не размещать retry-бизнес-логику в listener;
- не подключать KumoMTA в этом slice.

## Минимальный контракт

```json
{
  "tenantId": "uuid",
  "messageId": "uuid"
}
```

## Тесты

- Outbox event маршрутизируется в communication queue;
- listener вызывает worker один раз;
- повторная доставка сообщения безопасна за счёт Message state machine;
- неизвестный event type обрабатывается существующим механизмом ошибок.

## Definition of Done

- `MESSAGE_DELIVERY_REQUESTED` проходит `Outbox -> RabbitMQ -> listener -> MessageDeliveryWorker`;
- бизнес-состояние retry хранится в PostgreSQL;
- `mvn verify` зелёный.
