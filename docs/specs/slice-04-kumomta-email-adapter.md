# Slice 4 — KumoMTA email adapter

Status: Planned

Depends on: `slice-02-message-processing.md`, `slice-03-message-delivery-messaging.md`

Suggested branch: `feat/kumomta-email-provider`

## Цель

Подключить реальную EMAIL-доставку через KumoMTA как infrastructure adapter за существующим `DeliveryGateway`.

## Уже есть

Переиспользуем:

- `DeliveryGateway`, `DeliveryCommand`, `DeliveryResult`;
- `MessageDeliveryWorker`;
- retry/failure semantics из Slice 2;
- RabbitMQ/Outbox flow из Slice 3;
- persisted `providerMessageId`, `lastErrorCode`, `lastErrorMessage`.

## Scope

### 1. Adapter package

Добавить пакет:

```text
io.collectra.api.communication.infrastructure.kumomta
```

Минимально:

```text
KumoMtaEmailDeliveryGateway
KumoMtaClient
KumoMtaProperties
KumoMtaRequest
KumoMtaResponse
KumoMtaErrorClassifier
```

### 2. Configuration

Конфигурация только через properties/env:

```yaml
collectra:
  communication:
    kumomta:
      base-url: ${KUMOMTA_BASE_URL}
      connect-timeout: 2s
      read-timeout: 10s
```

Credentials не коммитить в repository config.

### 3. Request mapping

Из `DeliveryCommand` сформировать provider request для EMAIL:

```text
destination
subject
body
messageId / correlation id where supported
```

Provider-specific DTO не должны выходить за infrastructure package.

### 4. Response/error mapping

Начальная классификация:

```text
accepted / 2xx          -> Accepted(providerMessageId)
timeout / connection    -> RETRYABLE
429                     -> RETRYABLE
5xx                     -> RETRYABLE
invalid recipient / 4xx -> PERMANENT
bad auth/config         -> PERMANENT + operational log/alert signal
```

Финальные codes/fields сверить с фактическим KumoMTA endpoint до merge.

### 5. Safe errors

В `lastErrorMessage` не должны попадать:

- credentials;
- Authorization header;
- полный raw provider response без необходимости;
- лишние персональные данные.

## Основной поток

```text
MessageDeliveryWorker
  -> DeliveryGateway
  -> KumoMtaEmailDeliveryGateway
  -> KumoMTA
  -> Accepted / Rejected
  -> MessageDeliveryWorker
  -> MessageStateService
```

## Инварианты и правила

- application/domain не импортируют KumoMTA classes;
- provider call выполняется вне DB transaction;
- timeout обязателен;
- provider failure всегда переводится в общий `DeliveryResult`;
- неизвестная ошибка не должна молча считаться success;
- `providerMessageId` сохраняется только при accepted response.

## Не входит

- attachments;
- PDF/QR generation;
- multi-provider routing;
- SMS/WhatsApp/Telegram;
- generic provider SDK abstraction сверх `DeliveryGateway`.

## Тесты

Обязательные:

```text
KumoMtaEmailDeliveryGatewayTest
- request mapping
- accepted -> providerMessageId
- timeout -> RETRYABLE
- 429 -> RETRYABLE
- 5xx -> RETRYABLE
- permanent 4xx -> PERMANENT
- auth/config failure -> PERMANENT
- error text sanitized

ArchitectureTest
- kumomta package не протекает в domain/application API
```

При наличии простого mock HTTP server добавить integration test реального serialization/deserialization contract.

## Definition of Done

- EMAIL отправляется через KumoMTA за `DeliveryGateway`;
- success возвращает `providerMessageId`;
- temporary/permanent failures классифицируются детерминированно;
- credentials и unsafe response data не логируются/не persist-ятся;
- provider call ограничен timeout;
- domain/application не знают о KumoMTA;
- `mvn verify` зелёный.
