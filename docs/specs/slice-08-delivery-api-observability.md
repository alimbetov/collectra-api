# Slice 8 — Delivery API and observability

Status: PLANNED  
Depends on: Slice 2–7  
Suggested branch: `feat/delivery-api-observability`

## 1. Цель

Дать support/operator/developer безопасный tenant-scoped просмотр состояния delivery pipeline и минимальный production observability layer без прямого доступа к БД.

После Slice 8 по CampaignRun/Message должно быть возможно понять:

- что было создано;
- где message находится сейчас;
- сколько было attempts/retries;
- почему message failed/retrying;
- когда message sent;
- есть ли stuck processing;
- как выглядит агрегированный delivery health.

## 2. Scope

Добавить read-only API:

```text
GET /api/campaigns/{campaignId}/runs/{runId}/messages
GET /api/campaigns/{campaignId}/runs/{runId}/messages/{messageId}
```

Опционально, если уже есть отдельный CampaignRun details endpoint, расширить его delivery counters вместо создания duplicate endpoint.

Добавить:

```text
communication.application.MessageQueryService
communication.api.MessageController
communication.api.MessageResponse
communication.api.MessageListItemResponse
communication.api.MessageFilter
communication.observability.MessageDeliveryMetrics
```

Названия адаптировать к существующей package convention (`web`, `controller`, etc.), если она отличается.

## 3. API authorization

Использовать существующий auth/RBAC/tenant context.

Каждый query должен содержать tenant id из authenticated context.

Нельзя:

```text
repository.findById(messageId)
```

для API lookup без tenant/campaign/run scope.

Raw UUID другого tenant должен вернуть обычный `404`, а не `403 exists`, чтобы не раскрывать existence.

## 4. List endpoint

```text
GET /api/campaigns/{campaignId}/runs/{runId}/messages
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

Не добавлять универсальный query DSL.

### Paging rules

- default size: 50;
- max size: 200;
- deterministic sort: `createdAt DESC, id DESC` или project-standard sort;
- unpaged endpoint запрещён.

Если текущий repository возвращает `Slice`, можно вернуть application page/slice contract без дорогого total count. Если UI требует totalElements, использовать `Page` осознанно.

## 5. Detail endpoint

```text
GET /api/campaigns/{campaignId}/runs/{runId}/messages/{messageId}
```

Lookup должен одновременно проверять:

```text
tenantId
campaignId
campaignRunId
messageId
```

Это предотвращает чтение Message через неправильный parent resource path.

## 6. Response fields

List item минимум:

```json
{
  "id": "uuid",
  "campaignRunId": "uuid",
  "customerId": "uuid",
  "channel": "EMAIL",
  "destination": "r***@example.com",
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
lastErrorMessage
processingStartedAt
attachments metadata (если Slice 7 реализован)
```

Не возвращать body/subject в list endpoint.

В detail endpoint body/subject по умолчанию также лучше не возвращать, если support use case не требует. Если потребуется content preview — отдельное permission/endpoint.

## 7. Destination masking

По умолчанию destination должен быть masked.

EMAIL пример:

```text
ruslan@example.com -> r***@example.com
ab@example.com     -> a***@example.com
```

Phone/channel rules можно добавить при multi-channel expansion.

Unmasked destination допустим только если существующая RBAC policy явно даёт такое permission. Не вводить его implicit для любого authenticated user.

Masking выполняется response mapping layer, не мутирует Message entity.

## 8. Error exposure

`lastErrorCode` безопасно отдавать support role.

`lastErrorMessage`:

- уже должен быть sanitized provider adapter-ом;
- дополнительно ограничить output size;
- не возвращать stack trace;
- не возвращать credentials/provider raw response.

## 9. Repository/query requirements

Добавить explicit tenant-scoped query methods/specification только для фиксированных filters.

Предпочтительная форма:

```text
findAll by tenantId + campaignRunId
optional status/channel/customerId
paged
```

Можно использовать Spring Data `Specification` только если она уже принята в проекте. Не вводить generic query framework ради трёх filters.

Для detail:

```java
Optional<Message> findByIdAndTenantIdAndCampaignIdAndCampaignRunId(...)
```

или equivalent JPQL query.

## 10. Database indexes

Перед implementation проверить query plan для list endpoint.

Базовый expected index уже должен покрывать:

```text
(tenant_id, campaign_run_id)
```

Дополнительные composite indexes добавлять только если реальный query требует:

```text
(tenant_id, campaign_run_id, status)
(tenant_id, campaign_run_id, channel)
```

Не создавать комбинацию index на каждый optional filter без EXPLAIN/use case.

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

## 12. Metrics

Использовать Micrometer/Actuator, если уже подключены.

Минимум:

```text
collectra_message_delivery_total{channel,result}
collectra_message_delivery_latency_seconds{channel,result}
collectra_message_retry_total{channel,error_code}
collectra_message_processing_stuck
collectra_message_retry_wait_due
collectra_message_queue_age_seconds
```

### result values

Стабильный bounded set:

```text
sent
failed
retry_scheduled
```

Не использовать raw exception class как result.

## 13. Metric cardinality rules

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
error_code   # только bounded normalized codes
```

Если error code приходит raw от provider и имеет unbounded values, сначала normalize classifier-ом.

## 14. Metrics update points

Metrics должны обновляться рядом с фактическим durable outcome, а не listener ACK.

```text
markSent committed        -> delivery_total{sent}
scheduleRetry committed   -> delivery_total{retry_scheduled} + retry_total
markFailed committed      -> delivery_total{failed}
```

Если metric increment произойдёт перед transaction rollback, возможен небольшой telemetry drift. Для MVP допустимо либо регистрировать после successful service return, либо использовать transaction synchronization. Не усложнять outbox для metrics.

Business source of truth всё равно PostgreSQL counters, metrics — operational telemetry.

## 15. Delivery latency

Определить единую semantics:

```text
sentAt - Message.createdAt
```

или, если нужен provider attempt latency:

```text
provider call duration
```

Для MVP рекомендуются две разные conceptual metrics, но минимально оставить:

```text
collectra_message_delivery_latency_seconds
= createdAt -> sentAt
```

и HTTP client/provider call latency логировать/metric отдельно только если уже есть standard HTTP metrics.

Не смешивать эти два значения под одним именем.

## 16. Stuck PROCESSING gauge

Gauge:

```text
count(status=PROCESSING and processingStartedAt < now - processingTimeout)
```

Не делать full table scan на каждый scrape.

Варианты:

- scheduled cached gauge refresh;
- efficient indexed count query.

Переиспользовать processing timeout config Slice 2.

## 17. Due RETRY_WAIT gauge

```text
count(status=RETRY_WAIT and nextRetryAt <= now)
```

Полезно для обнаружения остановившегося retry dispatcher.

Query должен использовать index Slice 2.

## 18. Queue age

`collectra_message_queue_age_seconds` должен измерять age oldest actionable queued/retry message, а не создавать per-message metric.

Возможная semantics:

```text
now - oldest createdAt among QUEUED
```

Если query слишком дорогой для MVP, metric можно отложить, но это должно быть явно отмечено в PR, а не реализовано full scan.

## 19. Structured logs

На delivery path использовать correlation keys:

```text
tenantId
campaignId
campaignRunId
messageId
channel
attemptCount
providerMessageId   # only if exists and safe
errorCode
```

PII destination не использовать как основной correlation key.

Message body/subject не логировать.

## 20. Log events

Минимальные semantic events:

```text
message_delivery_started
message_delivery_sent
message_delivery_retry_scheduled
message_delivery_failed
message_processing_recovered
```

Не требуется отдельная audit table в этом slice.

## 21. Actuator/security

Если `/actuator/prometheus` используется:

- не делать его public через Slice 8;
- сохранить existing security/network policy;
- не добавлять sensitive application data в metrics.

## 22. Attachments in API

Detail может показывать только metadata:

```text
attachment id
filename
contentType
size if available
```

Не возвращать file bytes/base64 в Message detail.

Download должен использовать existing FileService access path/permissions.

## 23. Tests

### Controller/API

- list default paging;
- max size enforced;
- status/channel/customer filter;
- detail success;
- mismatched campaign/run/message -> 404;
- tenant A cannot read tenant B;
- destination masked;
- body absent from list response.

### Repository integration

- combined filters return correct messages;
- deterministic paging;
- tenant/run predicates always applied.

### Metrics

- SENT increments sent metric;
- retry increments retry metrics;
- FAILED increments failed metric;
- no IDs/PII appear as labels;
- stuck gauge reflects stale PROCESSING;
- due retry gauge reflects due RETRY_WAIT.

### Logging

Если project has log capture test utilities:

- correlation IDs present;
- email/body not logged;
- sanitized error only.

### Architecture/security

- controller only uses query/application service;
- no repository exposed directly to web layer if project architecture forbids it;
- no state-changing endpoint added.

## 24. Порядок реализации

1. query DTO/response contract;
2. tenant-scoped repository queries;
3. `MessageQueryService`;
4. controller + paging/filter validation;
5. destination masking;
6. CampaignRun summary extension;
7. metrics component/instrumentation;
8. stuck/due gauges with efficient queries;
9. structured logging cleanup;
10. API/security/metrics tests;
11. `mvn verify`.

## 25. Out of scope

- resend/retry-now administrative commands;
- editing Message;
- provider webhook/bounce/open/click UI;
- analytics warehouse;
- Grafana dashboard provisioning;
- searching across all tenants;
- generic query DSL;
- exposing email body to normal users.

## 26. Definition of Done

Slice 8 готов, если:

- operator can page/filter CampaignRun messages;
- detail lookup is tenant/campaign/run scoped;
- destination is masked by default;
- responses do not leak message body/secrets;
- CampaignRun counters are visible as summary;
- success/failure/retry metrics exist with bounded labels;
- stale PROCESSING and due RETRY_WAIT observable;
- logs have durable correlation IDs without PII content;
- API is read-only;
- query paths are indexed/verified against representative PostgreSQL data;
- `mvn verify` green.
