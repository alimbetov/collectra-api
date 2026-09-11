# Slice 3 — Message delivery messaging

Status: Planned

Depends on: `slice-02-message-processing.md`

Suggested branch: `feat/message-delivery-messaging`

## Цель

Подключить существующие Outbox и RabbitMQ к `MessageDeliveryWorker`, не создавая второй механизм очередей и не перенося business state в broker.

## Уже есть

Переиспользуем:

- `OutboxService`;
- `OutboxEvent` и publisher/retry lifecycle;
- `OutboxEventRouter`;
- RabbitMQ infrastructure;
- document messaging pattern;
- `MessageDeliveryWorker` из Slice 2.

## Scope

### 1. Event

Добавить стабильный тип:

```text
MESSAGE_DELIVERY_REQUESTED
```

Payload:

```json
{
  "tenantId": "uuid",
  "messageId": "uuid"
}
```

### 2. Outbox route

Расширить существующий `OutboxEventRouter` route для communication delivery.

Не создавать новый outbox publisher.

### 3. RabbitMQ config

Добавить `CommunicationMessagingConfig` с одним набором constants для exchange/queue/routing key/dead route.

Имена могут быть, например:

```text
collectra.communication
collectra.communication.queue
collectra.communication.dead
```

Retry timing остаётся business state в PostgreSQL; не нужно строить сложную TTL topology в этом slice.

### 4. Listener

Добавить тонкий listener:

```text
MessageDeliveryListener
  -> deserialize tenantId/messageId
  -> MessageDeliveryWorker.deliver(...)
```

Listener не должен сам менять `Message` status или классифицировать provider errors.

### 5. Retry re-enqueue

Retry dispatcher из Slice 2 после `requeue()` должен append `MESSAGE_DELIVERY_REQUESTED` через существующий Outbox в той же транзакции, где это возможно.

## Основной поток

```text
Message QUEUED
  -> Outbox MESSAGE_DELIVERY_REQUESTED
  -> Outbox publisher
  -> RabbitMQ
  -> MessageDeliveryListener
  -> MessageDeliveryWorker
```

Для retry:

```text
RETRY_WAIT due
  -> requeue()
  -> Outbox MESSAGE_DELIVERY_REQUESTED
  -> RabbitMQ
```

## Инварианты и правила

- broker payload содержит identifiers, а не `subject/body`;
- PostgreSQL `Message` остаётся source of truth;
- `tenantId` обязателен в async event;
- Rabbit redelivery должна быть безопасна за счёт state machine/claim;
- listener должен быть thin;
- retry attempt number не определяется Rabbit header.

## Не входит

- KumoMTA adapter;
- provider credentials/config;
- template rendering;
- campaign materialization;
- новая generic event bus abstraction.

## Тесты

Обязательные:

```text
OutboxEventRouterTest
- MESSAGE_DELIVERY_REQUESTED -> communication route

MessageDeliveryListenerTest
- корректный event вызывает worker

MessageRetryDispatcherIntegrationTest
- due retry -> QUEUED + outbox event
- not-due retry не публикуется

Integration test
- outbox payload содержит tenantId/messageId
- duplicate broker delivery не создаёт второй attempt
```

## Definition of Done

- `MESSAGE_DELIVERY_REQUESTED` проходит через существующий Outbox;
- communication queue/listener подключены;
- listener вызывает `MessageDeliveryWorker`;
- retry dispatcher публикует durable outbox event после requeue;
- broker payload не дублирует Message body/business state;
- Rabbit headers не являются источником retry state;
- `mvn verify` зелёный.
