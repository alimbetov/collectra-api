# Collectra backend specifications

Этот каталог содержит implementation-ready технические задания для backend MVP.

Главный roadmap: [`../roadmap/backend-mvp-roadmap.md`](../roadmap/backend-mvp-roadmap.md).

Последнее cross-review документации, актуального `main` и полного build log:
[`review-2026-09-11.md`](review-2026-09-11.md).

## Порядок реализации

| Slice | Спецификация | Статус | Основной результат |
|---|---|---|---|
| 2 | [Message processing core](slice-02-message-processing.md) | DONE | safe claim, retry, recovery, provider-neutral worker |
| 3 | [Message delivery messaging](slice-03-message-delivery-messaging.md) | DONE | Outbox -> RabbitMQ -> MessageDeliveryWorker |
| 4 | [KumoMTA email adapter](slice-04-kumomta-email-adapter.md) | IN PROGRESS | real EMAIL injection through KumoMTA HTTP API |
| 5 | [CampaignRun to Message materialization](slice-05-message-materialization.md) | READY | Campaign recipients -> immutable Message + Outbox |
| 6 | [Campaign delivery counters and completion](slice-06-campaign-delivery-counters.md) | READY AFTER 2/5 | atomic counters and durable CampaignRun completion |
| 7 | [Attachments and generated documents](slice-07-attachments-documents.md) | READY AFTER 3/4/5 + FONT GATE | document/FileService attachments before delivery |
| 8 | [Delivery API and observability](slice-08-delivery-api-observability.md) | READY AFTER 2–7 | support API, metrics, logging and production visibility |

## Dependency chain

```text
Slice 1 Message persistence                 DONE
        |
        v
Slice 2 Message processing core             DONE
        |
        v
Integration-test runtime repair             DONE
        |
        +-------------------+
        |                   |
        v                   v
Slice 3 Messaging DONE  Slice 6 counters*   (*final integration also needs Slice 5)
        |
        +--------+
        |        |
        v        v
Slice 4 Kumo   Slice 5 materialization
                 |
                 +--------+
                 |        |
                 v        v
             Slice 6   Slice 7 attachments (after 4/5)
                 \        /
                  \      /
                   v    v
               Slice 8 API/observability
```

## Что означает implementation-ready

Каждое ТЗ Slice 2–8 фиксирует:

- цель и зависимости;
- текущий baseline, который нельзя строить повторно;
- конкретный scope классов/пакетов;
- domain/state invariants;
- transaction boundaries;
- tenant isolation;
- idempotency/concurrency rules;
- DB migration/index requirements;
- event/API/provider contracts;
- error/retry semantics;
- unit/PostgreSQL/integration/architecture tests;
- рекомендуемый порядок реализации;
- explicit out-of-scope;
- Definition of Done.

ТЗ является стартовым техническим контрактом PR. Перед началом конкретного Slice разработчик всё равно обязан сверить его с актуальным `main`: если предыдущий PR уже реализовал часть scope или изменил имя класса/contract, ТЗ корректируется по фактическому коду, а не наоборот.

## Правила реализации

1. Один Slice — один узкий PR, если diff не требует обоснованного разделения.
2. Branch создаётся от актуального `main`, а не от documentation branch.
3. Не вводить новый framework/abstraction, если существующая инфраструктура решает задачу.
4. Domain state меняется через domain methods, не прямым `setStatus`.
5. Tenant id остаётся явной частью async/query contracts.
6. External network calls не выполняются под PostgreSQL row lock/long transaction.
7. Async event содержит durable identifiers, а PostgreSQL остаётся source of truth.
8. Время приходит через application `Clock`.
9. Schema меняется Liquibase migration только когда реально требуется.
10. Для persistence/concurrency использовать PostgreSQL integration tests, не только H2/mock tests.
11. Все integration tests соблюдают общий [test-runtime contract](integration-test-runtime.md).
12. `mvn verify` обязан быть green перед PR completion.
13. PR не мержится автоматически без явного решения.

## Общие архитектурные ограничения

```text
communication.domain
    не зависит от Spring HTTP/Rabbit/KumoMTA

communication.application
    знает DeliveryGateway, но не KumoMTA DTO

communication.infrastructure
    реализует Rabbit/KumoMTA/persistence adapters

PostgreSQL
    authoritative Message/CampaignRun state

RabbitMQ
    at-least-once work transport, не business source of truth

Transactional Outbox
    единственный DB -> broker publication mechanism
```

## Definition of Ready перед началом Slice

Перед созданием code branch проверить:

- предыдущие required slices merged в `main`;
- migration numbering актуален;
- package/class names в ТЗ всё ещё соответствуют проекту;
- нет уже существующей реализации того же scope;
- CI `main` не красный по независимой причине;
- полный integration suite укладывается в connection budget из
  [test-runtime contract](integration-test-runtime.md);
- внешние contracts, если они есть, подтверждены (например KumoMTA endpoint/configuration).

После этого реализация может идти прямо по разделу `Порядок реализации` соответствующего ТЗ.
