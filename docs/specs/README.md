# Collectra backend specifications

Этот каталог содержит implementation-ready технические задания для backend MVP.

Главный roadmap: [`../roadmap/backend-mvp-roadmap.md`](../roadmap/backend-mvp-roadmap.md).

Следующий frontend implementation contract: FW4E contracts in
[`frontendweb-fw04-customers.md`](frontendweb-fw04-customers.md).

Business frontend implementation plan:
[`frontendweb-fw03-fw12-plan.md`](frontendweb-fw03-fw12-plan.md). Он индексирует
implementation contracts FW3–FW12 и их backend prerequisites.
Detailed current-code review:
[`frontendweb-fw03-fw12-review.md`](frontendweb-fw03-fw12-review.md).

Approved public money wire contract:
[`public-money-decimal-string-contract.md`](public-money-decimal-string-contract.md).

| Frontend slice | Specification | Readiness |
|---|---|---|
| FW3 | [Dashboard](frontendweb-fw03-dashboard.md) | IMPLEMENTED |
| FW4A | [Customer list](frontendweb-fw04a-customer-list.md) | IMPLEMENTED |
| FW4B | [Customer read-only detail](frontendweb-fw04b-customer-detail.md) | IMPLEMENTED |
| FW4C | [Customer editing](frontendweb-fw04c-customer-editing.md) | IMPLEMENTED |
| FW4D | [Customer segments](frontendweb-fw04d-customer-segments.md) | IMPLEMENTED |
| FW4E | [Customer contracts](frontendweb-fw04-customers.md) | NEXT |
| FW5 | [Receivables](frontendweb-fw05-receivables.md) | money/projection/paging gate |
| FW6 | [Collections](frontendweb-fw06-collections.md) | filters/history paging gate |
| FW7 | [Campaigns](frontendweb-fw07-campaigns.md) | detail/idempotency gate |
| FW8 | [Message monitoring](frontendweb-fw08-message-monitoring.md) | API READY after FW7 |
| FW9 | [Templates](frontendweb-fw09-templates.md) | detail/revision/bounds gate |
| FW10 | [Imports](frontendweb-fw10-imports.md) | diagnostics/config bounds gate |
| FW11 | [Files](frontendweb-fw11-files.md) | registry/public DTO gate |
| FW12 | [Administration](frontendweb-fw12-administration.md) | paging/DTO/revision gate |

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
| 9 | [Frontend API Completion](slice-09-frontend-api-completion.md) | READY FOR IMPLEMENTATION | normalize existing frontend APIs, add Contract/Collection bounded domains and freeze `/api/v1` contract |

## Post-Slice 10A hardening plan

Master plan: [Post-Slice 10A Hardening Roadmap](post-slice10a-hardening-roadmap.md).

| Order | Specification | Purpose |
|---|---|---|
| A0 | [Frontend API Baseline Freeze](frontend-api-baseline-freeze.md) | freeze actual `/api/v1` contract so frontend can proceed independently |
| B0 | [Test Infrastructure Cleanup](test-infrastructure-cleanup.md) | disable RabbitMQ schedulers/background activity in generic tests and make runtime deterministic |
| B1 | [Slice 10A Final Closure](slice-10a-final-closure.md) | close mandatory P08/P11/P02/P10 scenarios and enforce 95/90 critical-core gate |
| B4 | [Slice 10B RabbitMQ Production Hardening](slice-10b-rabbitmq-production-hardening.md) | DLQ/restart/redelivery/publisher-confirm/ambiguous-outcome hardening |
| B5 | [Real KumoMTA Environment and Acceptance](real-kumomta-environment-acceptance.md) | prove real inject, remote SMTP outcome and reconciliation |
| B6-B9 | [Real Channel Adapters Roadmap](real-channel-adapters-roadmap.md) | SMS -> Telegram -> WhatsApp -> In-App/Push |

Frontend work and delivery hardening are intentionally parallel tracks. Frontend implementation MUST NOT wait for RabbitMQ production hardening or real providers unless a concrete screen depends on those providers.

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
                       |
                       v
              Slice 9 Frontend API Completion
                9A-1 Customer/Segment normalization
                         |
                         v
                9A-2 Contract foundation
                         |
                         v
                9B Receivables normalization
                         |
                         v
                9C Collection foundation
                         |
                         v
                9D Frontend support/API freeze
```

## Что означает implementation-ready

Каждое ТЗ фиксирует:

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

ТЗ является стартовым техническим контрактом PR. Перед началом конкретного Slice разработчик обязан сверить его с актуальным `main`: если предыдущий PR уже реализовал часть scope или изменил имя класса/contract, ТЗ корректируется по фактическому коду, а не наоборот.

## Правила реализации

1. Один Slice — один узкий PR, если diff не требует обоснованного разделения.
2. Branch создаётся от актуального `main`, а не от documentation branch.
3. Не вводить новый framework/abstraction, если существующая инфраструктура решает задачу.
4. Domain state меняется через domain methods/commands, не прямым `setStatus`.
5. Tenant id остаётся явной частью async/query contracts.
6. External network calls не выполняются под PostgreSQL row lock/long transaction.
7. Async event содержит durable identifiers, а PostgreSQL остаётся source of truth.
8. Время приходит через application `Clock`; business `LocalDate` использует явно заданный ZoneId, не JVM default.
9. Schema меняется Liquibase migration только когда реально требуется.
10. Для persistence/concurrency использовать PostgreSQL integration tests, не только H2/mock tests.
11. Все integration tests соблюдают общий [test-runtime contract](integration-test-runtime.md).
12. `mvn verify` обязан быть green перед PR completion; Slice 10A closure дополнительно требует `-Pslice10a-coverage`.
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

Для frontend API дополнительно:

```text
Frontend
    работает только через /api/v1 и не знает persistence model

PostgreSQL query
    выполняет tenant filter + business filters + paging + sorting

JPA entities
    не являются public REST DTO

High-volume lists
    никогда не возвращаются unpaged

Receivable
    единственный owner paidAmount/outstandingAmount/paymentStatus

Collection
    владеет workflow state, но не финансовым балансом
```

## Definition of Ready перед началом Slice

Перед созданием code branch проверить:

- предыдущие required slices merged в `main`;
- migration numbering актуален;
- package/class names в ТЗ всё ещё соответствуют проекту;
- нет уже существующей реализации того же scope;
- CI `main` не красный по независимой причине;
- полный integration suite укладывается в connection budget из [test-runtime contract](integration-test-runtime.md);
- внешние contracts, если они есть, подтверждены;
- для frontend work повторно подтверждены current domain ownership и existing API paths.

После этого реализация может идти прямо по разделу `Implementation order` соответствующего ТЗ.
