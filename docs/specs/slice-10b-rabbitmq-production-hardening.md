# Slice 10B — RabbitMQ Production Hardening

Status: READY AFTER SLICE 10A

## 1. Goal

Довести messaging contour `PostgreSQL Outbox -> RabbitMQ -> MessageDeliveryWorker` до production-grade at-least-once delivery semantics с контролируемой redelivery, DLQ, restart recovery и operational visibility.

RabbitMQ остаётся transport layer. PostgreSQL остаётся authoritative business state.

## 2. Required broker topology

Зафиксировать durable topology для delivery events:

```text
primary exchange
primary queue
retry exchange/queue(s)
dead-letter exchange
DLQ / parking queue
```

Topology MUST быть декларативной, idempotent на startup и одинаковой между environments кроме explicit sizing/tuning properties.

## 3. Consumer semantics

- manual acknowledgement;
- ack только после безопасного завершения local processing contract;
- nack/requeue не должен создавать unbounded hot loop;
- prefetch configurable;
- graceful shutdown прекращает intake и завершает bounded in-flight work;
- duplicate broker delivery безопасен;
- poison event не блокирует очередь.

## 4. Retry and DLQ

Retry должен учитывать существующий Message state/retry policy, а не создавать второй source of truth в RabbitMQ.

Обязательно определить:

- retryable vs permanent failures;
- max attempts;
- delay/backoff contract;
- broker redelivery count vs business attemptCount;
- dead-letter reason;
- parking/manual recovery flow;
- no infinite redelivery.

## 5. Publisher reliability

Outbox publisher MUST использовать publisher confirms или эквивалентный подтверждённый mechanism.

DB outbox row не считается safely published только потому, что publish call вернулся без local exception.

Проверить crash windows:

```text
DB commit -> process crash before publish
publish -> broker accepts -> process crashes before marking outbox published
broker unavailable
network partition
confirm timeout
```

Duplicate publication после crash безопасна за счёт consumer/business idempotency.

## 6. Restart matrix

Automated integration tests должны покрывать:

- broker restart before publish;
- broker restart with queued messages;
- worker restart before claim;
- worker crash after DB claim;
- worker crash before provider;
- worker crash after provider response but before state commit;
- API/application restart while retries are due;
- redelivery after lost consumer connection;
- DLQ persists across restart.

## 7. Ambiguous external delivery

Slice 10B MUST зафиксировать production contract для:

```text
provider accepted request
connection/result became ambiguous
local transaction did not record SENT
```

Blind resend запрещён, если provider не гарантирует idempotency.

Нужен один из production-grade designs:

1. stable physical-delivery idempotency key accepted by provider;
2. durable delivery-attempt ledger + provider reconciliation;
3. explicit `UNKNOWN`/`ACCEPTANCE_UNCERTAIN` state с reconciliation before retry.

Выбранный вариант фиксируется ADR и тестируется fault injection.

## 8. Observability

Metrics минимум:

- queue depth;
- oldest message age;
- publish failures/confirm latency;
- consumer processing latency;
- redelivery count;
- retry queue depth;
- DLQ depth;
- stuck processing count;
- permanent failures;
- reconciliation/unknown outcomes.

Labels MUST иметь bounded cardinality. Message/customer identifiers запрещены как metric labels.

Alerts минимум:

- DLQ > 0;
- oldest queue age over threshold;
- repeated publish failures;
- consumer unavailable;
- stuck processing growth;
- unknown delivery outcome exists too long.

## 9. Security/configuration

- broker credentials only from secret/config source;
- TLS supported for non-local environments;
- no credentials in logs;
- queue/exchange names controlled by application config, not user input;
- management/diagnostic endpoints not exposed through tenant API.

## 10. Definition of Done

- deterministic restart/redelivery test matrix green;
- no infinite redelivery;
- DLQ/parking flow operational;
- publisher confirms covered;
- graceful shutdown covered;
- ambiguous delivery has explicit production design;
- metrics/alerts documented and tested;
- `mvn verify` and Slice 10A gates remain green.
