# Real Channel Adapters Roadmap

Status: READY AFTER REAL KUMOMTA

## 1. Goal

Подключать реальные каналы по одному через существующий provider-neutral delivery boundary, не меняя Message state machine для каждого provider.

Recommended order:

```text
SMS -> Telegram -> WhatsApp -> In-App/Push
```

Telegram рекомендуется раньше WhatsApp, потому что он проще как второй реальный non-email adapter и быстрее проверяет переносимость channel-neutral архитектуры.

## 2. Common adapter contract

Каждый adapter MUST:

- реализовать существующий `DeliveryGateway`/provider boundary;
- получать только materialized Message snapshot;
- не менять Message state напрямую;
- классифицировать provider result в bounded outcomes;
- иметь connect/read/request timeout;
- нормализовать provider error code без raw body/secrets/PII;
- поддерживать deterministic mock/fake;
- иметь explicit production config validation;
- не принимать provider endpoint/token из user-controlled payload;
- поддерживать stable correlation/idempotency key там, где provider позволяет.

## 3. SMS

Scope:

- выбрать provider через отдельный ADR;
- text encoding/GSM-7/UCS-2 policy;
- message length/segmentation;
- sender id;
- delivery receipt reconciliation;
- invalid number/permanent failure;
- rate limit/retry;
- provider auth failure;
- country/operator restrictions;
- opt-out/compliance hooks where required.

Acceptance:

- real handset receives message;
- delivery receipt maps back to Message;
- duplicate/retry does not create uncontrolled duplicate send;
- Cyrillic/Kazakh text verified.

## 4. Telegram

Scope:

- Bot API adapter;
- chat/user destination mapping;
- bot token only from secret store;
- text length boundaries;
- Markdown/HTML escaping policy;
- document/attachment path where supported;
- blocked bot/chat not found/rate limit classification;
- Telegram `retry_after` mapped to retry policy;
- provider message id persisted for reconciliation/audit if needed.

Acceptance:

- real bot -> controlled chat;
- text and attachment delivery;
- rate-limit scenario;
- invalid/blocked chat permanent classification.

## 5. WhatsApp

Scope:

- provider choice/Meta Cloud API ADR;
- approved template lifecycle;
- template language/locale mapping;
- session vs template message semantics;
- phone normalization;
- media upload/reference flow;
- webhook verification/security;
- delivery/read/failure status reconciliation;
- rate limit and provider-specific policy errors.

WhatsApp-specific template constraints MUST stay inside adapter/configuration layer and must not leak into generic Message state machine.

Acceptance:

- approved template sent to controlled number;
- webhook reconciles delivered/failed status;
- invalid template/recipient/rate-limit scenarios normalized;
- duplicate webhook/event idempotent.

## 6. In-App / Push

After SMS/Telegram/WhatsApp stabilize, add:

- in-app inbox persistence/read API;
- push gateway (e.g. FCM/APNs abstraction);
- device token lifecycle;
- invalid token cleanup;
- notification vs data payload policy;
- deep-link safety;
- delivery/open/read metrics where technically reliable.

## 7. Cross-channel tests

For every real adapter keep one shared matrix:

```text
ACCEPTED
RETRYABLE
RATE_LIMITED
PERMANENT_FAILURE
AUTH_FAILURE
TIMEOUT_BEFORE_ACCEPT
ACCEPT_THEN_TIMEOUT / ambiguous when applicable
MALFORMED_RESPONSE
```

State-machine behavior MUST remain provider-neutral.

## 8. Definition of Done per channel

- real provider environment configured;
- deterministic adapter unit tests;
- HTTP/client contract tests;
- integration test through worker boundary;
- remote acceptance test;
- retry/permanent/rate-limit/auth scenarios;
- secure secrets/config;
- reconciliation where provider supports async delivery status;
- metrics/alerts;
- no regression to existing channels.
