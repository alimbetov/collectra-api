# Slice 8 — Delivery API and observability

Status: Planned

Depends on: `slice-02-message-processing.md` through `slice-07-attachments-documents.md`

Suggested branch: `feat/delivery-operations-api`

## Цель

Дать операционный просмотр delivery state и минимальную observability для production support без прямого доступа к PostgreSQL.

## Уже есть

Переиспользуем:

- `MessageRepository`;
- Campaign/CampaignRun security boundary;
- tenant context/authentication;
- Message delivery statuses and error fields;
- Spring Boot metrics/Actuator infrastructure, если уже подключена.

## Scope

### 1. Message list API

Добавить paged endpoint, например:

```text
GET /api/campaigns/{campaignId}/runs/{runId}/messages
```

Минимальные фильтры:

```text
status
channel
customerId
```

Repository query всегда ограничен `tenantId + campaignRunId`.

### 2. Message details API

Добавить:

```text
GET /api/campaigns/{campaignId}/runs/{runId}/messages/{messageId}
```

Lookup должен подтверждать одновременно tenant, run/campaign relation и message id.

### 3. Response model

Минимально полезные поля:

```text
id
campaignRunId
customerId
channel
destination
status
attemptCount
nextRetryAt
providerMessageId
lastErrorCode
lastErrorMessage
sentAt
createdAt
```

Destination возвращать masked или полностью в зависимости от существующих permissions/policy. Не расширять permissions модель без необходимости.

### 4. Metrics

Добавить небольшой стабильный набор:

```text
collectra_message_delivery_total{channel,result}
collectra_message_delivery_latency_seconds{channel}
collectra_message_retry_total{channel,code}
collectra_message_stuck_processing
collectra_message_queue_age_seconds
```

Не добавлять high-cardinality labels вроде tenantId/messageId/email/providerMessageId.

### 5. Structured logs

Основные correlation keys:

```text
tenantId
campaignId
campaignRunId
messageId
providerMessageId
```

Email/phone не использовать как primary correlation key и не логировать без необходимости.

### 6. Operational errors

API должен давать оператору достаточно информации, чтобы различать:

```text
waiting
processing
retry scheduled
sent
permanent failure
stuck processing
```

Не возвращать stack traces/raw provider responses.

## Основной поток

```text
operator/API client
  -> authenticated tenant context
  -> paged tenant-scoped repository query
  -> operational DTO
```

Metrics/logs формируются на реальных Message transitions, а не при чтении API.

## Инварианты и правила

- raw UUID никогда не обходится без tenant scope;
- list endpoint только paged;
- read API не меняет Message state;
- metrics labels имеют bounded cardinality;
- provider secrets/raw unsafe error data не возвращаются;
- timestamps возвращаются в одном согласованном API формате;
- API показывает persisted truth, а не состояние RabbitMQ queue.

## Не входит

- dashboard UI;
- Prometheus/Grafana deployment redesign;
- manual retry endpoint;
- message cancellation/resend commands;
- analytics warehouse;
- SLA alerting platform.

## Тесты

Обязательные:

```text
MessageQueryApiIntegrationTest
- list pagination
- status/channel/customer filters
- details lookup
- tenant isolation
- campaign/run scope isolation
- masking/access rule
- unknown message -> correct 404

MessageMetricsTest
- SENT increments delivery metric
- FAILED increments failure metric
- RETRY_WAIT increments retry metric
- labels do not contain message/customer identifiers
```

## Definition of Done

- оператор может найти Message внутри CampaignRun без прямого SQL;
- list/details tenant-scoped и paged;
- status/error/retry information достаточно для диагностики;
- sensitive destination/error data защищены;
- success/failure/retry/stuck processing имеют минимальные metrics;
- metrics не создают high-cardinality labels;
- read API не изменяет delivery state;
- `mvn verify` зелёный.
