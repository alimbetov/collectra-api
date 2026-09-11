# Slice 4 — KumoMTA email adapter

## Цель

Подключить реальную email-доставку через KumoMTA за существующим `DeliveryGateway`.

## Что сделать

- пакет `communication.infrastructure.kumomta`;
- `KumoMtaEmailDeliveryGateway`;
- клиент и configuration properties;
- request/response DTO;
- connection/read timeout;
- классификация ошибок в `RETRYABLE` и `PERMANENT`;
- сохранение `providerMessageId` при успешной отправке;
- безопасное логирование без credentials и полного provider response.

## Начальная классификация ошибок

```text
accepted / 2xx          -> Accepted
timeout / connection    -> RETRYABLE
429                     -> RETRYABLE
5xx                     -> RETRYABLE
invalid recipient / 4xx -> PERMANENT
bad auth/config         -> PERMANENT + operational alert
```

Финальную HTTP mapping проверить по реальному KumoMTA endpoint/protocol.

## Конфигурация

```yaml
collectra:
  communication:
    kumomta:
      base-url: ${KUMOMTA_BASE_URL}
      connect-timeout: 2s
      read-timeout: 10s
```

Credentials — только из environment/secret storage.

## Не делать

- не добавлять KumoMTA-типы в domain/application API;
- не генерировать PDF/QR в provider adapter;
- не держать DB transaction во время сетевого вызова.

## Тесты

- корректный request mapping;
- accepted response;
- timeout/429/5xx -> retryable;
- permanent 4xx -> permanent;
- ошибки санитизируются;
- architecture test на отсутствие KumoMTA dependency вне infrastructure.

## Definition of Done

`MessageDeliveryWorker` отправляет EMAIL через `DeliveryGateway`, а application/domain код не знает о KumoMTA. `mvn verify` зелёный.
