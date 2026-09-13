# Real KumoMTA Environment and Acceptance

Status: READY AFTER SLICE 10B

## 1. Goal

Подключить Collectra к реальному KumoMTA environment и доказать end-to-end email delivery/reconciliation вне mock режима.

## 2. Environment

Развернуть отдельный KumoMTA test environment с:

- dedicated hostname/IP;
- explicit HTTP inject endpoint;
- authentication where applicable;
- TLS;
- persistent queue/spool;
- bounded logging;
- health/operational monitoring;
- network access restricted to trusted callers.

Endpoint/IP/credentials задаются application configuration/secrets. User-controlled provider URL запрещён.

## 3. DNS and sender identity

До external acceptance подготовить:

- sending domain/subdomain;
- SPF;
- DKIM;
- DMARC policy appropriate for test stage;
- reverse DNS/PTR where infrastructure permits;
- stable envelope-from/bounce domain strategy.

## 4. Inject API contract

Production adapter должен использовать KumoMTA inject API согласно существующему Slice 4 contract.

Проверить:

- request serialization;
- recipient/envelope fields;
- subject/text/html;
- UTF-8/RU/KZ/CJK;
- attachments;
- timeout limits;
- response parsing;
- provider identifiers;
- bounded error normalization.

## 5. Remote acceptance matrix

Минимум сценарии:

1. successful delivery to controlled mailbox;
2. invalid recipient/permanent rejection;
3. temporary remote SMTP failure;
4. KumoMTA unavailable;
5. HTTP timeout before acceptance;
6. ambiguous timeout after possible acceptance;
7. malformed/5xx response;
8. auth failure;
9. attachment delivery;
10. RU/KZ/CJK rendering;
11. duplicate broker event;
12. application restart during in-flight delivery.

## 6. Reconciliation

HTTP acceptance by KumoMTA не равен final remote delivery.

Нужно зафиксировать lifecycle минимум:

```text
Collectra provider accepted
KumoMTA queued
remote delivered OR bounced/deferred
reconciled outcome
```

Если KumoMTA предоставляет webhook/log/API source для delivery/bounce events, реализовать provider-neutral reconciliation adapter.

События должны быть tenant/message correlated durable identifiers, без PII в metric labels/log keys.

## 7. Acceptance criteria

End-to-end acceptance считается доказанным только если тест проходит путь:

```text
API/Campaign
  -> Message
  -> Outbox
  -> RabbitMQ
  -> worker
  -> KumoMTA inject
  -> Kumo queue
  -> remote SMTP
  -> delivered/bounced outcome
  -> reconciliation/observable final state
```

Простой `HTTP 200` от inject API недостаточен.

## 8. Definition of Done

- KumoMTA deployed and repeatably configured;
- real inject works from Collectra;
- remote mailbox acceptance proven;
- bounce/deferred case proven;
- ambiguous outcome policy aligned with Slice 10B;
- reconciliation path implemented or explicitly documented if provider capability is insufficient;
- mock mode remains available for deterministic local/CI tests but is forbidden by production profile.
