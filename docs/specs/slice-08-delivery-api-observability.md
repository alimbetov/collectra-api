# Slice 8 — Delivery API and observability

Status: PLANNED  
Depends on: Slice 2–7  
Suggested implementation branch: `feat/delivery-api-observability`

## 1. Цель

Дать support/operator/developer безопасный tenant-scoped просмотр состояния delivery pipeline и production-grade observability layer без прямого доступа к БД.

После Slice 8 по CampaignRun/Message должно быть возможно понять:

- что было создано;
- где message находится сейчас;
- сколько было attempts/retries;
- почему message failed/retrying;
- когда message sent;
- есть ли stuck processing;
- есть ли просроченный retry backlog;
- есть ли broker dead-letter/dead queue проблема;
- как выглядит агрегированный delivery health;
- какой компонент pipeline перестал продвигать сообщения.

Slice 8 остаётся read/observability slice и не меняет delivery state machine.

## 2. Scope

Добавить read-only API:

```text
GET /api/v1/campaigns/{campaignId}/runs/{runId}/messages
GET /api/v1/campaigns/{campaignId}/runs/{runId}/messages/{messageId}
```

Опционально, если уже есть отдельный CampaignRun details endpoint, расширить его delivery counters вместо создания duplicate endpoint.

Добавить или адаптировать к существующей package convention:

```text
communication.application.MessageQueryService
communication.api.MessageController
communication.api.MessageResponse
communication.api.MessageListItemResponse
communication.api.MessageFilter
communication.observability.MessageDeliveryMetrics
communication.observability.DeliveryHealthSnapshotService
communication.observability.DeliveryErrorClassifier
communication.observability.DestinationMasker
```

Использовать уже существующий Spring Boot Actuator, Micrometer Prometheus registry и tracing stack. Не вводить параллельный custom metrics framework.

## 3. API authorization и tenant isolation

Использовать существующий auth/RBAC/tenant context.

Для обоих endpoints использовать существующее permission `CAMPAIGN_READ` вместе с `ROLE_HUMAN`; новое delivery-specific permission в MVP не вводить.

Каждый query должен содержать tenant id из authenticated context.

Tenant isolation MUST быть enforced в database query. Application-side post filtering запрещён.

Нельзя:

```text
repository.findById(messageId)
```

с последующей проверкой tenant в Java.

Raw UUID другого tenant, неправильный campaign/run parent path и несуществующий Message должны давать одинаковый `404`, а не distinguishable `403`, чтобы не раскрывать existence.

Если используется `Page`, tenant scope должен применяться не только к content query, но и к count query; metadata `totalElements/totalPages` не должна раскрывать данные другого tenant.

## 4. List endpoint

```text
GET /api/v1/campaigns/{campaignId}/runs/{runId}/messages
```

Query params:

```text
page=0
size=50
status=SENT
channel=EMAIL
customerId=<uuid>
```

MVP фильтры:

- `status` optional;
- `channel` optional;
- `customerId` optional.

Different filters используют AND semantics.

Пример:

```text
status=SENT&channel=EMAIL&customerId=X
```

означает:

```text
status = SENT
AND channel = EMAIL
AND customer_id = X
```

Не добавлять универсальный query DSL и multi-value filter syntax в Slice 8.

### Paging rules

- default size: 50;
- max size: 200;
- deterministic sort: `createdAt DESC, id DESC`;
- unpaged endpoint запрещён;
- `page < 0` -> `400`;
- `size <= 0` -> `400`;
- `size > 200` -> `400`, не silently clamp;
- unknown `status` -> `400`;
- unknown `channel` -> `400`;
- invalid `customerId` UUID -> `400`.

Предпочтительно вернуть `Slice`/application slice contract без дорогого `COUNT(*)`, если UI не требует totalElements. `Page` использовать только при реальном UI requirement.

## 5. Detail endpoint

```text
GET /api/v1/campaigns/{campaignId}/runs/{runId}/messages/{messageId}
```

Lookup должен одновременно проверять:

```text
tenantId
campaignId
campaignRunId
messageId
```

Это предотвращает чтение Message через неправильный parent resource path.

Предпочтительный repository contract:

```java
Optional<Message> findByIdAndTenantIdAndCampaignIdAndCampaignRunId(...)
```

или equivalent JPQL/query method.

## 6. Response fields

List item минимум:

```json
{
  "id": "uuid",
  "campaignRunId": "uuid",
  "customerId": "uuid",
  "channel": "EMAIL",
  "maskedDestination": "r***@example.com",
  "status": "SENT",
  "attemptCount": 1,
  "nextRetryAt": null,
  "sentAt": "2026-09-11T10:00:00Z",
  "createdAt": "2026-09-11T09:59:00Z"
}
```

Detail дополнительно:

```text
campaignId
invoiceId
templateVersionId
resolvedLocale
providerMessageId
lastErrorCode
lastErrorSummary
processingStartedAt
attachments metadata (если Slice 7 реализован)
```

Не возвращать body/subject в list endpoint.

В detail endpoint body/subject по умолчанию также не возвращать. Если позже потребуется content preview — отдельное permission/endpoint.

## 7. Destination masking

Raw destination не должен попадать в API response DTO.

EMAIL examples:

```text
ruslan@example.com -> r***@example.com
ab@example.com     -> a***@example.com
a@example.com      -> a***@example.com
null               -> null
blank/malformed    -> ***
```

Phone/channel rules добавить при multi-channel expansion.

Unmasked destination допустим только если существующая RBAC policy явно вводит соответствующее permission. Не давать unmasked destination implicit любому authenticated user.

Masking выполняется в mapping layer через единый `DestinationMasker`, не мутирует `Message` entity и не реализуется post-serialization filter-ом.

## 8. Error exposure и normalization

`lastErrorCode` должен быть bounded internal/normalized code, например:

```text
CONNECTION_TIMEOUT
PROVIDER_4XX
PROVIDER_5XX
RATE_LIMITED
INVALID_DESTINATION
PERMANENT_PROVIDER_REJECTION
ATTACHMENT_NOT_READY
UNKNOWN
```

Raw provider error code/message не должен становиться API contract без normalization.

В API использовать `lastErrorSummary`, а не raw `lastErrorMessage`.

Требования к `lastErrorSummary`:

- формируется централизованно;
- ограничен по длине;
- не содержит email/phone/body/subject;
- не содержит stack trace;
- не содержит credentials/tokens/authorization headers;
- не содержит raw provider response;
- не зависит от `Throwable.getMessage()` как единственного источника текста.

Provider adapter sanitization полезен, но не является единственной privacy/security boundary.

## 9. Repository/query requirements

Добавить explicit tenant-scoped query methods/specification только для фиксированных filters.

Предпочтительная форма:

```text
findAll by tenantId + campaignRunId
optional status/channel/customerId
paged
```

Можно использовать Spring Data `Specification` только если она уже принята в проекте. Не вводить generic query framework ради трёх filters.

Repository/API layer не должен делать post-filtering tenant/customer after loading broad result set.

## 10. Database indexes

Перед implementation проверить query plan для list endpoint через PostgreSQL `EXPLAIN ANALYZE` на representative data.

Базовый expected index для основного paging path:

```text
(tenant_id, campaign_run_id, created_at DESC, id DESC)
```

Дополнительные composite indexes добавлять только если реальный query plan/use case требует, например:

```text
(tenant_id, campaign_run_id, customer_id, created_at DESC, id DESC)
(tenant_id, campaign_run_id, status, created_at DESC, id DESC)
```

Индекс по low-cardinality `channel` добавлять только после измерений. Не создавать index на каждую optional filter combination заранее.

## 11. CampaignRun delivery summary

Если существующий CampaignRun response не содержит counters, расширить его:

```json
{
  "recipientCount": 1000,
  "sentCount": 920,
  "failedCount": 20,
  "skippedCount": 40,
  "retryCount": 73,
  "pendingCount": 20
}
```

`pendingCount` не хранить отдельной колонкой. Рассчитывать:

```text
recipientCount - sentCount - failedCount - skippedCount
```

с защитой invariant `>= 0`.

PostgreSQL counters остаются business source of truth.

## 12. Metrics stack

Slice 8 MUST использовать существующие Micrometer/Actuator/Prometheus/tracing dependencies.

Не создавать custom metrics endpoint/registry поверх Micrometer.

### Event counters

```text
collectra_message_delivery_total{channel,result}
collectra_message_retry_total{channel,error_code}
collectra_message_dead_letter_total{queue,reason}
```

`result` имеет bounded set:

```text
sent
failed
retry_scheduled
```

### Latency/timers

```text
collectra_message_delivery_latency_seconds{channel,result}
```

Semantics для MVP:

```text
sentAt - Message.createdAt
```

Provider HTTP call latency не смешивать с end-to-end delivery latency; использовать standard HTTP client metrics или отдельную provider-call metric, если она уже соответствует project convention.

### Current-state gauges

```text
collectra_message_processing_stuck
collectra_message_processing_oldest_stuck_age_seconds
collectra_message_retry_wait_due
collectra_message_retry_wait_oldest_age_seconds
collectra_message_failed_current
collectra_message_dead_letter_depth{queue}
collectra_message_queue_age_seconds
```

## 13. FAILED vs dead-letter semantics

`Message.status = FAILED` и broker dead-letter/DLQ — разные operational concepts.

Domain terminal failure:

```text
PROCESSING -> FAILED
```

означает, что delivery state machine штатно завершила message permanent failure/retries exhausted.

Broker dead-letter означает, что transport envelope/consumer processing не смог продолжить normal flow и сообщение оказалось в dead-letter queue/exchange.

Не объединять их в один metric.

Минимум:

```text
collectra_message_delivery_total{channel,result="failed"}
collectra_message_failed_current

collectra_message_dead_letter_total{queue,reason}
collectra_message_dead_letter_depth{queue}
```

`queue` и `reason` должны иметь bounded predefined values; raw routing key/provider text запрещены как labels.

## 14. Metric cardinality rules

В metric labels запрещены high-cardinality identifiers:

```text
tenantId
campaignId
campaignRunId
messageId
customerId
providerMessageId
email
phone
```

Допустимые low-cardinality labels:

```text
channel
result
provider
error_code
queue
reason
```

`error_code` и `reason` только normalized/bounded enums/classifiers.

Raw exception class не использовать как `result`; exception class как label использовать только если заранее доказан bounded set, но для MVP лучше не использовать вовсе.

## 15. Metrics update points: AFTER_COMMIT only

Business outcome metrics должны публиковаться только после успешного commit соответствующей PostgreSQL transaction.

```text
markSent committed      -> delivery_total{sent}
scheduleRetry committed -> delivery_total{retry_scheduled} + retry_total
markFailed committed    -> delivery_total{failed}
```

Increment до commit запрещён, чтобы rollback не создавал telemetry drift.

Допустимые implementation patterns:

```text
@TransactionalEventListener(phase = AFTER_COMMIT)
```

или transaction synchronization `afterCommit`.

Outbox для metrics не требуется.

Metrics остаются operational telemetry, PostgreSQL state/counters — source of truth.

## 16. Stuck PROCESSING observability

Stuck semantics:

```text
status = PROCESSING
AND processingStartedAt < now - processingTimeout
```

Переиспользовать processing timeout config Slice 2.

Expose минимум:

```text
collectra_message_processing_stuck
collectra_message_processing_oldest_stuck_age_seconds
```

Один count недостаточен: `3 stuck for 70 sec` и `3 stuck for 8 hours` должны различаться.

Не делать full table scan на каждый Prometheus scrape.

## 17. Due RETRY_WAIT observability

Due retry semantics:

```text
status = RETRY_WAIT
AND nextRetryAt <= now
```

Expose минимум:

```text
collectra_message_retry_wait_due
collectra_message_retry_wait_oldest_age_seconds
```

Query должен использовать index Slice 2/appropriate composite index.

Эти gauges показывают backlog; `collectra_message_retry_total` показывает historical event rate. Не смешивать event counter и current backlog.

## 18. Queue age

`collectra_message_queue_age_seconds` измеряет age oldest actionable queued/retry message, а не per-message metric.

Semantics должна быть единой и документированной. Для MVP допустимо:

```text
now - oldest actionableAt among QUEUED/eligible RETRY_WAIT
```

Если efficient query отсутствует, metric можно отложить отдельным PR note, но запрещено реализовывать expensive full scan на каждый scrape.

## 19. Cached delivery health snapshot

Prometheus scrape не должен непосредственно запускать тяжёлые SQL queries.

Предпочтительная схема:

```text
PostgreSQL
   |
   | scheduled refresh every configurable interval (например 15–30 sec)
   v
DeliveryHealthSnapshotService
   |- stuckCount
   |- oldestStuckAge
   |- dueRetryCount
   |- oldestDueRetryAge
   |- failedCurrent
   |- queueAge
   v
Micrometer gauges / AtomicLong state
   v
/actuator/prometheus
```

Refresh interval должен быть configurable.

Query failure не должен сбрасывать previous good snapshot в fake zero; логировать/метрировать refresh failure согласно существующей convention.

## 20. Worker heartbeat

Добавить low-cost heartbeat observability для компонентов, которые продвигают delivery pipeline:

```text
collectra_delivery_worker_last_success_timestamp_seconds
collectra_retry_dispatcher_last_success_timestamp_seconds
collectra_recovery_last_success_timestamp_seconds
```

Если компонент отсутствует в текущем Slice 2–7 implementation, соответствующую metric не создавать искусственно.

Heartbeat обновляется после successful cycle/work unit, а не просто при старте scheduler thread.

## 21. Structured logs

На delivery path использовать correlation keys:

```text
traceId
spanId
tenantId
campaignId
campaignRunId
messageId
channel
attemptCount
providerMessageId   # only if exists and safe
errorCode
```

PII destination не использовать как correlation key.

Не логировать:

```text
email
phone
message body
subject
attachment bytes/base64
authorization headers
tokens/credentials
raw provider response
```

Raw `Throwable` logging запрещён, если `Throwable.getMessage()` потенциально содержит PII/payload/secrets.

Вместо этого логировать normalized `errorCode`, safe summary, trace/correlation ids и при необходимости безопасный exception type/class без raw message.

## 22. Log events

Минимальные semantic events:

```text
message_delivery_started
message_delivery_sent
message_delivery_retry_scheduled
message_delivery_failed
message_processing_recovered
message_dead_lettered
```

Event names должны быть стабильными.

Не требуется отдельная audit table в этом slice.

## 23. Operational alerts contract

Slice 8 должен определить deployable alert rules/specification для production observability. Grafana dashboard provisioning остаётся out of scope.

Каждый alert должен иметь:

```text
name
metric/expression
threshold
window
for
severity
runbook/operational action
```

Минимальные alert classes:

### A. Stuck processing

Alert, если oldest stuck age существенно превышает configured processing timeout, например:

```text
collectra_message_processing_oldest_stuck_age_seconds
> processingTimeout * configurableMultiplier
```

Использовать `for`, чтобы не алертить на transient single scrape.

### B. Due retry backlog

Alert по `collectra_message_retry_wait_due` и/или oldest due retry age.

Threshold/window должны быть configurable deployment values, не hardcode Java constants.

### C. Failed rate

Не алертить просто на `failed > 0`.

Использовать failure ratio/rate с minimum traffic floor, например conceptually:

```text
rate(failed[window]) / rate(delivery outcomes[window]) > threshold
AND traffic >= minimum
```

Конкретные threshold/window задаются deployment observability config.

### D. Dead-letter depth

Alert, если DLQ depth > 0 дольше configurable `for`, либо превышает configurable threshold.

### E. Worker heartbeat stale

Alert, если current time - last successful heartbeat > configured threshold.

## 24. Actuator/security

`/actuator/prometheus` не делать public через Slice 8.

Сохранить существующую security/network policy.

Не добавлять sensitive/high-cardinality application data в metrics.

Не создавать tenant-specific Prometheus labels для диагностики; tenant-specific investigation выполнять через API и structured logs.

## 25. Attachments in API

Detail может показывать только metadata:

```text
attachment id
filename
contentType
size
status
```

Не возвращать file bytes/base64 в Message detail.

Download должен использовать existing FileService access path/permissions.

Если attachment filename считается user-provided/untrusted, API возвращает значение как data; logs не должны автоматически включать filename без необходимости.

## 26. Tests

### Controller/API

- list default paging;
- `size > 200` -> 400;
- negative page -> 400;
- invalid enum/UUID filters -> 400;
- status/channel/customer filters;
- combined filters use AND semantics;
- detail success;
- mismatched campaign/run/message -> 404;
- tenant A cannot read tenant B;
- tenant metadata/count cannot leak tenant B;
- destination masked;
- raw destination absent;
- body/subject absent from list/detail;
- raw provider error absent.

### Repository integration

- combined filters return correct messages;
- deterministic paging with equal `createdAt` uses `id` tie-break;
- tenant/run predicates always applied;
- cross-tenant rows cannot affect Page count if Page is used;
- query plan/index path verified on representative PostgreSQL data.

### Metrics

- SENT committed increments sent metric;
- rollback does not increment sent metric;
- retry committed increments retry metrics;
- rollback does not increment retry metrics;
- FAILED committed increments failed metric;
- domain FAILED and broker dead-letter metrics remain distinct;
- no IDs/PII appear as labels;
- stuck gauge reflects stale PROCESSING;
- oldest stuck age reflects age, not only count;
- due retry gauge reflects due RETRY_WAIT;
- oldest retry age reflects backlog age;
- cached gauge scrape does not execute heavy query directly;
- heartbeat advances only after successful work/cycle.

### Logging/security

Использовать log capture test utility или equivalent:

- trace/correlation IDs present;
- email/phone/body/subject not logged;
- raw provider response not logged;
- token/authorization header not logged;
- raw exception message containing fake PII does not leak;
- normalized error code is logged;
- semantic event names are stable.

### Architecture

- controller uses query/application service;
- web layer не обращается напрямую к repository, если project architecture запрещает это;
- no state-changing endpoint added;
- no custom metrics registry/endpoint introduced.

### Alerts

- alert expressions reference existing metric names;
- alert rules have severity and `for`;
- failed-rate alert includes minimum traffic floor;
- stuck/DLQ/heartbeat alerts do not fire on a single transient scrape when `for` is configured.

## 27. Порядок реализации

1. query DTO/response contract;
2. strict tenant-scoped repository queries;
3. `MessageQueryService`;
4. controller + paging/filter validation;
5. `DestinationMasker`;
6. safe normalized error exposure;
7. CampaignRun summary extension;
8. AFTER_COMMIT event instrumentation;
9. retry/stuck/failed/dead-letter metrics;
10. cached `DeliveryHealthSnapshotService`;
11. oldest-age gauges;
12. worker heartbeat metrics;
13. structured logging cleanup + tracing correlation;
14. operational alert rules/spec;
15. API/security/repository/metrics/logging tests;
16. `mvn verify`.

## 28. Out of scope

- resend/retry-now administrative commands;
- editing Message;
- provider webhook/bounce/open/click UI;
- analytics warehouse;
- Grafana dashboard provisioning;
- searching across all tenants;
- generic query DSL;
- exposing email body to normal users;
- per-tenant Prometheus labels;
- exactly-once metrics delivery/outbox for telemetry.

## 29. Definition of Done

Slice 8 готов, если:

- operator can page/filter CampaignRun messages;
- detail lookup is tenant/campaign/run scoped at DB query level;
- foreign tenant/mismatched parent resource does not leak existence;
- destination is masked by default and raw destination absent from response DTO;
- responses do not leak body/subject/secrets/raw provider errors;
- CampaignRun counters are visible as summary;
- success/failure/retry metrics exist with bounded labels;
- metrics are emitted AFTER_COMMIT for durable business outcomes;
- domain FAILED and broker dead-letter observability are separated;
- stale PROCESSING and due RETRY_WAIT expose both count and oldest age;
- health gauges are served from cached snapshot or efficient indexed queries, not heavy SQL per scrape;
- relevant delivery/retry/recovery workers expose last-success heartbeat where applicable;
- structured logs contain trace/correlation IDs without PII/content/secrets;
- raw Throwable messages cannot leak fake PII in tests;
- operational alerts define expression/threshold/window/for/severity/runbook;
- API is read-only;
- query paths are indexed/verified against representative PostgreSQL data;
- existing Actuator/Micrometer/Prometheus/tracing stack is reused;
- `mvn verify` green.
