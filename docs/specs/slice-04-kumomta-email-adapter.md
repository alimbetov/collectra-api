# Slice 4 — KumoMTA email adapter

Status: IN PROGRESS
Depends on: Slice 2, Slice 3  
Suggested branch: `feat/kumomta-email-provider`

## 1. Цель

Реализовать production `DeliveryGateway` для EMAIL через KumoMTA HTTP Injection API.

Выбранный transport для MVP:

```text
POST /api/inject/v1
Content-Type: application/json
```

Не реализовывать параллельно SMTP transport. Если deployment позже потребует SMTP, это отдельный adapter.

Официальный reference:

```text
https://docs.kumomta.com/reference/http/kumod/api_inject_v1_post/
```

Минимальная поддерживаемая версия deployment для этого contract:

```text
2026.05.12-a6845223
```

`template_dialect = Static` появился в официальном API начиная с
`2025.12.02-67ee9e96`, а используемый этим Slice per-recipient `metadata`
поддерживается начиная с `2026.05.12-a6845223`. Поэтому фактический minimum
для полного request contract — `2026.05.12-a6845223`.

Deployment version проверяется в Definition of Ready; молча fallback-ить на
Jinja запрещено, иначе уже rendered `{{...}}` content может измениться повторно.

## 2. Архитектурная граница

```text
MessageDeliveryWorker
        |
        v
DeliveryGateway                 application port
        ^
        |
KumoMtaEmailDeliveryGateway     infrastructure adapter
        |
        v
KumoMtaClient
        |
        v
POST /api/inject/v1
```

Application/domain packages не импортируют KumoMTA request/response classes.

## 3. Scope

Добавить:

```text
io.collectra.api.communication.infrastructure.kumomta
├── KumoMtaEmailDeliveryGateway.java
├── KumoMtaClient.java
├── KumoMtaProperties.java
├── KumoMtaInjectRequest.java
├── KumoMtaInjectResponse.java
├── KumoMtaRecipient.java
└── KumoMtaErrorClassifier.java
```

До готовности provider infrastructure также добавить:

```text
io.collectra.api.communication.infrastructure.simulation
└── SimulatedDeliveryGateway.java
```

HTTP client использовать стандартный для проекта (`RestClient`/`WebClient`), не добавлять новую HTTP library без необходимости.

Для синхронного worker path предпочтителен Spring `RestClient`, если проект уже использует blocking execution.

## 4. Configuration

Provider выбирается независимо от state machine:

```yaml
collectra:
  communication:
    delivery:
      enabled: true
      provider: simulated # simulated | kumomta
```

`disabled` является безопасным production default. Local profile использует
`simulated`, но delivery остаётся выключенной до явного включения. При
`provider=kumomta` отсутствие обязательных параметров приводит к startup failure.

Не вводить отдельную property `ip`: `base-url` хранит protocol, IP/DNS, port и
позволяет позже поставить TLS/reverse proxy без изменения application contract.

```yaml
collectra:
  communication:
    kumomta:
      enabled: true
      base-url: ${KUMOMTA_BASE_URL}
      envelope-sender: ${KUMOMTA_ENVELOPE_SENDER}
      connect-timeout: 2s
      read-timeout: 10s
```

Если deployment защищает injection endpoint Basic/API auth:

```yaml
      username: ${KUMOMTA_USERNAME:}
      password: ${KUMOMTA_PASSWORD:}
```

## 4.1 Deterministic simulation mode

До готовности KumoMTA и остальных provider adapters используется один
`SimulatedDeliveryGateway` для всех `CommunicationChannel`:

```yaml
collectra:
  communication:
    delivery:
      provider: simulated
      simulation:
        success-rate-percent: 80
        permanent-failure-rate-percent: 10
```

Оставшиеся 10% являются retryable failure. Outcome вычисляется детерминированно
из `messageId`, поэтому повторная обработка того же сообщения воспроизводима и
тесты не flaky. Simulator является отдельным adapter и не смешивается с KumoMTA
классами. Production profile по умолчанию использует `disabled`, чтобы случайно
не отметить реальные сообщения как `SENT` через fake provider.

Credentials:

- только env/secret management;
- не логировать;
- не хранить в DB;
- не commit в `application*.yml` реальное значение.

## 5. HTTP request contract

Для одного `Message` отправлять одного recipient.

Минимальный request:

```json
{
  "envelope_sender": "noreply@example.com",
  "content": {
    "headers": {
      "From": "Collectra <noreply@example.com>",
      "Subject": "Invoice reminder"
    },
    "html_body": "<html>...</html>"
  },
  "recipients": [
    {
      "email": "client@example.com",
      "metadata": {
        "collectra_message_id": "uuid",
        "collectra_tenant_id": "uuid"
      }
    }
  ],
  "template_dialect": "Static",
  "deferred_generation": false,
  "deferred_spool": false
}
```

### Важные правила

- Collectra template уже rendered до delivery; KumoMTA templating не использовать;
- `template_dialect = Static`, если это поддерживается deployment version;
- one Collectra Message -> one Kumo injection recipient;
- business content не рендерить в adapter;
- metadata использовать только для correlation, не для business logic.
- `To` header не передавать: KumoMTA строит его из `recipients`; это избегает
  duplicate `To` на версиях до `2026.03.04-bb93ecb1`;
- `deferred_generation=false`, чтобы `success_count` отражал фактический inject;
- `deferred_spool=false`, чтобы не ослаблять durable accountability.

Если actual Kumo deployment предпочитает RFC822 string `content`, adapter может собирать MIME/RFC822 representation, но выбор должен быть единообразным и покрытым test. Для MVP предпочтителен structured `content`.

## 6. Response contract

KumoMTA `POST /api/inject/v1` возвращает как минимум:

```json
{
  "success_count": 1,
  "fail_count": 0,
  "failed_recipients": [],
  "errors": []
}
```

Success condition для single-recipient request:

```text
HTTP 2xx
AND success_count == 1
AND fail_count == 0
```

Если HTTP 2xx, но `fail_count > 0` или recipient присутствует в `failed_recipients`, это rejection, а не Accepted.

Неконсистентный `2xx` response, например `success_count=0, fail_count=0` или
`success_count > 1` для single-recipient request, считается provider contract
failure и классифицируется как `RETRYABLE / KUMO_INVALID_RESPONSE`. Он не должен
безвозвратно переводить бизнес-сообщение в `FAILED`.

## 7. providerMessageId semantics

Текущий Kumo inject response не гарантирует external message id в базовом response contract.

Поэтому для MVP:

```text
DeliveryResult.Accepted(providerMessageId = null)
```

разрешён и соответствует текущему `Message.markSent`, где provider id optional.

Не генерировать fake providerMessageId.

Для correlation использовать собственный `messageId` в Kumo metadata/header.

Если deployment позже добавит hook/API, возвращающий Kumo spool id, его можно сохранять отдельным follow-up изменением.

## 8. Error classification

`KumoMtaErrorClassifier` должен выдавать только application semantics:

```text
RETRYABLE
PERMANENT
```

Initial mapping:

```text
connect timeout                 -> RETRYABLE / KUMO_CONNECT_TIMEOUT
read timeout                    -> RETRYABLE / KUMO_READ_TIMEOUT
connection reset/unavailable    -> RETRYABLE / KUMO_CONNECTION_ERROR
HTTP 429                        -> RETRYABLE / KUMO_RATE_LIMITED
HTTP 5xx                        -> RETRYABLE / KUMO_SERVER_ERROR
HTTP 408                        -> RETRYABLE / KUMO_TIMEOUT
HTTP 400/422 invalid content    -> PERMANENT / KUMO_INVALID_REQUEST
HTTP 401/403                    -> PERMANENT / KUMO_AUTH_ERROR
2xx with failed recipient       -> PERMANENT unless error text is explicitly transient
invalid/inconsistent 2xx body   -> RETRYABLE / KUMO_INVALID_RESPONSE
invalid local destination       -> PERMANENT / INVALID_DESTINATION
```

Не классифицировать все `4xx` одинаково без причины.

## 9. Local validation

До HTTP call adapter обязан проверить минимум:

```text
channel == EMAIL
destination non-blank
subject non-blank
body non-blank
```

Полную RFC email validation не усложнять. Domain уже хранит destination snapshot; provider остаётся финальным валидатором deliverability.

Если `channel != EMAIL`, adapter должен явно reject/throw unsupported-channel error, а не пытаться отправить.

## 10. Timeouts

HTTP call обязательно имеет bounded:

```text
connect timeout
read/response timeout
```

No infinite defaults.

Timeout превращается в `DeliveryResult.Rejected(RETRYABLE, ...)`, а не в raw provider exception для worker.

## 11. Logging and sanitization

Логировать:

```text
messageId
tenantId
HTTP status
provider error code/classification
latency
```

Не логировать целиком:

```text
body
recipient email
Authorization header
password
full provider response if it can contain recipient/content
```

`lastErrorMessage` должен получить короткий sanitized message. Raw `errors[]` от
KumoMTA разрешено использовать для классификации, но запрещено сохранять в
message state; application layer получает стабильное generic сообщение.

## 12. Spring bean selection

`KumoMtaEmailDeliveryGateway` должен стать runtime implementation `DeliveryGateway` для EMAIL.

Не оставлять ambiguity с fake/test bean в production profile. Ровно один bean
выбирается через `collectra.communication.delivery.provider`:

```text
simulated -> SimulatedDeliveryGateway
kumomta   -> KumoMtaEmailDeliveryGateway
disabled  -> no DeliveryGateway
```

Если позже channels станут multiple, provider selection переносится в registry/router. В Slice 4 generic registry не нужен.

## 13. Attachments

Attachments не входят в Slice 4. Они подключаются Slice 7.

Но client DTO не должен препятствовать расширению `content.attachments` позже.

Не читать RustFS/FileService внутри Kumo adapter в этом slice.

## 14. Health/configuration failure

Startup должен fail fast только для явно включённого Kumo adapter с некорректной mandatory configuration, например пустой `base-url` или `envelope-sender`.

Удалённая недоступность KumoMTA не должна блокировать startup приложения; это runtime retryable delivery failure.

## 15. Database changes

Migration не нужна.

`providerMessageId` может оставаться null при accepted injection.

## 16. Tests

### Unit

`KumoMtaErrorClassifierTest`

- timeout -> retryable;
- 429 -> retryable;
- 5xx -> retryable;
- 401/403 -> permanent;
- 400/422 -> permanent;
- inconsistent single-recipient 2xx response -> retryable invalid response;
- provider error text is not persisted into application message state.

`KumoMtaEmailDeliveryGatewayTest`

- EMAIL request maps correctly;
- static rendered subject/body passed unchanged;
- non-email channel rejected;
- `success_count=1` -> Accepted;
- HTTP 2xx + failed recipient -> Rejected;
- provider id not fabricated.

`SimulatedDeliveryGatewayTest`

- deterministic distribution `80 accepted / 10 permanent / 10 retryable`;
- повторный `messageId` всегда даёт тот же outcome;
- поддерживаются все текущие channels;

### HTTP integration

Использовать local mock HTTP server/WireMock equivalent уже допустимый в project tests.

Проверить:

- exact path `/api/inject/v1`;
- `Content-Type: application/json`;
- envelope sender;
- recipient;
- subject/body;
- metadata messageId;
- timeout behavior;
- 429/5xx;
- 422 invalid request.

### Architecture

- `communication.application` не зависит от `infrastructure.kumomta`;
- domain не зависит от HTTP/Spring client DTO;
- Kumo classes confined to infrastructure.

## 17. Порядок реализации

1. properties + validation;
2. request/response DTO;
3. HTTP client;
4. error classifier;
5. `KumoMtaEmailDeliveryGateway`;
6. bean wiring;
7. unit tests;
8. HTTP integration tests;
9. architecture test;
10. `mvn verify`.

## 18. Out of scope

- SMTP adapter;
- delivery/bounce webhook processing;
- Kumo log ingestion;
- attachments;
- template rendering;
- campaign materialization;
- bulk multi-recipient injection in one request.

## 19. Definition of Done

Slice 4 готов, если:

- EMAIL delivery использует `POST /api/inject/v1`;
- request contract стабилен и протестирован;
- rendered body не изменяется повторным templating;
- success/rejection корректно интерпретируются;
- timeout/429/5xx retryable;
- invalid/auth failures permanent;
- invalid/inconsistent provider success body does not cause permanent business failure;
- provider-specific exceptions не выходят в application layer;
- sensitive/provider response data не сохраняется в message state;
- network call остаётся вне DB transaction;
- `mvn verify` green.
