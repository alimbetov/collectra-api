# Slice 3 — Message delivery messaging

Status: DONE
Depends on: Slice 2 — Message processing core  
Suggested branch: `feat/message-delivery-messaging`

Entry gate: shared [integration-test runtime contract](integration-test-runtime.md)
implemented and full `main` verification green. Не начинать Slice 3 поверх
красного baseline: Rabbit/outbox integration tests добавят ещё один context и
усилят существующее исчерпание PostgreSQL connections.

## 1. Цель

Подключить существующие Transactional Outbox + RabbitMQ к `MessageDeliveryWorker`, не создавая второй механизм очередей и не перенося business retry state в RabbitMQ.

После Slice 3 путь должен быть таким:

```text
DB transaction
  -> OutboxEvent(MESSAGE_DELIVERY_REQUESTED)
  -> existing outbox publisher
  -> RabbitMQ
  -> MessageDeliveryListener
  -> MessageDeliveryWorker
```

## 2. Текущий baseline

Уже есть:

- `shared.outbox.OutboxService`;
- `OutboxEvent`, repository/publisher/retry lifecycle;
- `OutboxEventRouter`;
- RabbitMQ infrastructure для document generation;
- `MessageDeliveryWorker` из Slice 2;
- `MessageRetryDispatcher` из Slice 2;
- durable retry fields в `Message`.

Не создавать второй outbox, second publisher или отдельную retry DB.

## 3. Scope

Добавить:

```text
io.collectra.api.communication.infrastructure.messaging
├── CommunicationMessagingConfig.java
└── MessageDeliveryListener.java
```

Application contract:

```text
communication.application.MessageDeliveryRequested.java
communication.application.MessageDeliveryEventPublisher.java
```

Event record находится в application, чтобы application services не зависели от
`communication.infrastructure.messaging`. `MessageDeliveryEventPublisher`
инкапсулирует JSON serialization и `OutboxService.append(...)`; его переиспользуют
Slice 5 и Slice 7 вместо копирования event type/aggregate/payload assembly.

Изменить:

```text
shared.outbox.OutboxEventRouter
communication.application.MessageRetryDispatcher
shared.outbox.OutboxService
```

`OutboxService` должен получать `Clock` и вызывать explicit-time constructor
`OutboxEvent(..., clock.instant())`. Конструктор entity с внутренним
`Instant.now()` удалить: общее правило времени распространяется и на новый
communication event path.

## 4. Event contract

Event type:

```text
MESSAGE_DELIVERY_REQUESTED
```

Aggregate:

```text
aggregateType = MESSAGE
aggregateId   = messageId
```

Payload минимальный:

```json
{
  "tenantId": "uuid",
  "messageId": "uuid"
}
```

Payload intentionally не содержит:

- destination;
- subject;
- body;
- customer DTO;
- template payload;
- retry count.

Причина: PostgreSQL `Message` — authoritative state. Broker carries only a work reference.

## 5. MessageDeliveryRequested

Record:

```java
public record MessageDeliveryRequested(
        UUID tenantId,
        UUID messageId) {}
```

Оба UUID обязательны; compact constructor делает fail-fast null validation. JSON
contract должен быть стабильным и покрыт serialization/deserialization test.

## 6. RabbitMQ topology

Использовать отдельную communication topology, не смешивать с document queue.

Минимально:

```text
exchange:     collectra.communication
routing key:  message.delivery.requested
queue:        collectra.communication.message-delivery
dead exchange: collectra.communication.dead
dead key:      message.delivery.dead
dead queue:    collectra.communication.message-delivery.dead
```

`CommunicationMessagingConfig` должен быть единственным местом с именами exchange/queue/routing key.

Существующий `DocumentMessagingConfig` уже предоставляет общий
`Jackson2JsonMessageConverter`. Не объявлять второй converter bean с тем же
назначением; communication topology должна переиспользовать существующий Jackson
setup.

Не добавлять business retry TTL chain в RabbitMQ для `Message`. Retry scheduling контролируется `Message.nextRetryAt`.

Основная queue обязана иметь DLX arguments. Fatal conversion/malformed payload
reject отправляется в dead queue; это infrastructure quarantine, не business
retry. Queue/exchange/binding beans должны использовать explicit qualifiers, так
как document topology уже создаёт несколько beans тех же Rabbit типов.

## 7. Outbox routing

Расширить `OutboxEventRouter`:

```text
DOCUMENT_GENERATION_REQUESTED -> existing document route
MESSAGE_DELIVERY_REQUESTED     -> communication route
unknown                        -> existing UnknownOutboxEventTypeException
```

Не превращать Slice 3 в общий refactoring event bus. Если два `if` всё ещё читаемы — этого достаточно.

## 8. Создание OutboxEvent

Любое место, которое делает message eligible for asynchronous delivery, должно создавать event **в той же DB transaction**, где durable state становится готовым к отправке.

В Slice 3 обязательно подключить retry dispatcher:

```text
RETRY_WAIT where nextRetryAt <= now
   -> lock Message
   -> message.requeue()
   -> OutboxService.append(MESSAGE_DELIVERY_REQUESTED)
   -> commit
```

Atomicity:

```text
QUEUED + outbox row
```

commit together или rollback together.

Первичная event creation при materialization будет полностью подключена Slice 5.

## 9. Listener

Рекомендуемая форма:

```java
@Component
public class MessageDeliveryListener {
    private final MessageDeliveryWorker worker;

    @RabbitListener(queues = CommunicationMessagingConfig.MESSAGE_DELIVERY_QUEUE)
    public void consume(MessageDeliveryRequested event) {
        worker.deliver(event.tenantId(), event.messageId());
    }
}
```

До включения KumoMTA listener и worker регистрируются только при
`collectra.communication.delivery.enabled=true`. Default — `false`. Это сохраняет
успешный startup после Slice 3 и не потребляет delivery events, когда provider
явно отключён. При включённом switch отсутствие `DeliveryGateway` должно ломать
startup как configuration error, а не создавать no-op delivery.

Listener должен оставаться thin.

Listener не должен:

- читать/менять `Message` самостоятельно;
- считать retries;
- классифицировать provider errors;
- рендерить templates;
- обновлять counters;
- содержать KumoMTA code.

## 10. Ack/error semantics

Business processing outcome `SENT/RETRY_WAIT/FAILED` считается успешно обработанным message event и не должен бесконечно redeliver-иться RabbitMQ.

Unexpected technical exception допускается обрабатывать согласно существующей project Rabbit listener policy.

Не перехватывать `Exception` в thin listener. Стандартный Spring AMQP fatal
conversion handler reject-ит malformed payload без requeue, после чего DLX
карантинирует его. Нефатальная application/DB exception может быть redelivered;
Slice 2 claim и stale recovery обеспечивают безопасность. Не настраивать
бесконечный application-level retry loop внутри listener.

Важно разделить:

```text
Provider temporary rejection
    -> Message becomes RETRY_WAIT
    -> listener call considered handled

Application/DB infrastructure crash
    -> listener infrastructure may redeliver event
    -> Message claim/state machine makes redelivery safe
```

## 11. Duplicate delivery

RabbitMQ обеспечивает at-least-once delivery. Система обязана быть idempotent при:

```text
same tenantId + messageId event delivered N times
```

Гарантия находится не в RabbitMQ, а в Slice 2:

- `SENT/FAILED` -> no-op;
- `PROCESSING` -> duplicate begin no-op;
- `QUEUED` -> only one locked claim.

## 12. Serialization

Использовать тот же Jackson/ObjectMapper setup, который уже используется Outbox publisher/listeners.

Не создавать отдельный ObjectMapper для communication без необходимости.

Malformed event должен попадать в существующий infrastructure error/DLQ path и не приводить к созданию нового Message.

## 13. Configuration

Если project config уже хранит Rabbit settings централизованно, добавить только communication-specific names.

Пример:

```yaml
collectra:
  communication:
    messaging:
      exchange: collectra.communication
      delivery-queue: collectra.communication.message-delivery
      delivery-routing-key: message.delivery.requested
```

Если имена являются constants и не configurably required — не усложнять properties class.

## 14. Database changes

Schema migration не нужна.

Outbox table используется существующая.

## 15. Tests

### Unit

`OutboxEventRouterTest`

- `MESSAGE_DELIVERY_REQUESTED` -> correct exchange/routing key;
- document route не сломан;
- unknown event -> exception.

`MessageDeliveryListenerTest`

- valid event вызывает worker с tenantId/messageId;
- listener не выполняет дополнительную domain logic.

### Integration

`MessageDeliveryMessagingIntegrationTest`

Минимальный сценарий:

```text
OutboxEvent MESSAGE_DELIVERY_REQUESTED
  -> publisher
  -> RabbitMQ
  -> listener
  -> test DeliveryGateway
  -> Message SENT
```

Если full Rabbit Testcontainers path уже слишком тяжёлый для текущего suite, покрыть router/listener отдельно + существующий outbox publisher integration. Но до merge должен существовать хотя бы один integration path, доказывающий compatibility event JSON и listener contract.

`MessageRetryDispatcherIntegrationTest`

- due RETRY_WAIT -> QUEUED + exactly one outbox event;
- future retry -> untouched;
- repeated dispatcher run не создаёт duplicate event после state changed.

### Idempotency

- same event twice -> one actual provider attempt for already terminal Message;
- redelivery during PROCESSING -> second worker no-op.

## 16. Порядок реализации

1. `MessageDeliveryRequested`;
2. messaging constants/config;
3. Outbox router route;
4. listener;
5. retry dispatcher Outbox append;
6. remove entity-internal Outbox `Instant.now()`;
7. serialization/router/listener/topology tests;
8. PostgreSQL Outbox atomicity/idempotency test;
9. `mvn verify`.

## 17. Out of scope

- KumoMTA adapter;
- Campaign -> Message materialization;
- attachment generation;
- public delivery API;
- Rabbit-based business retry counter;
- redesign всего Outbox subsystem.

## 18. Definition of Done

Slice 3 готов, если:

- `MESSAGE_DELIVERY_REQUESTED` имеет зафиксированный JSON contract;
- existing Outbox routes event в communication Rabbit topology;
- listener вызывает `MessageDeliveryWorker`;
- Rabbit payload содержит только durable IDs;
- retry dispatcher делает `RETRY_WAIT -> QUEUED + OutboxEvent` атомарно;
- duplicate delivery безопасна;
- business retry state остаётся в PostgreSQL;
- existing document messaging не сломан;
- integration path соблюдает общий
  [test-runtime contract](integration-test-runtime.md) и не создаёт отдельный
  PostgreSQL container/context без необходимости;
- `mvn verify` green.
