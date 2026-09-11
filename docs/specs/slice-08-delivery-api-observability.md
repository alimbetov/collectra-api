# Slice 8 — Delivery API and observability

## Цель

Дать операционный просмотр delivery state и минимальные метрики для поддержки production.

## API

Добавить paged endpoints в существующей campaign/communication модели, например:

```text
GET /api/campaigns/{campaignId}/runs/{runId}/messages
GET /api/campaigns/{campaignId}/runs/{runId}/messages/{messageId}
```

Поддержать фильтры:

```text
status
channel
customerId
```

Полезные поля response:

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

Destination маскировать там, где это требуется политикой доступа.

## Безопасность

- каждый lookup tenant-scoped;
- raw UUID никогда не позволяет получить message другого tenant;
- paging обязателен для списка сообщений.

## Метрики

Минимум:

```text
collectra_message_delivery_total{channel,result}
collectra_message_delivery_latency_seconds{channel}
collectra_message_retry_total{channel,code}
collectra_message_stuck_processing
collectra_message_queue_age_seconds
```

## Логи

Структурированные correlation keys:

```text
tenantId
campaignId
campaignRunId
messageId
providerMessageId
```

Не использовать email/phone как основной correlation id.

## Тесты

- tenant isolation;
- pagination/filtering;
- masking/access rules;
- metrics увеличиваются на нужных transition;
- operational endpoint не меняет Message state.

## Definition of Done

Оператор может понять, что происходит с конкретным CampaignRun/Message, не обращаясь напрямую к БД. Метрики покрывают success/failure/retry/stuck processing. `mvn verify` зелёный.
