# Frontend API Baseline Freeze

Status: READY FOR IMPLEMENTATION

## 1. Goal

Зафиксировать фактический frontend-facing REST contract текущего `main`, чтобы frontend-разработка могла идти параллельно с reliability hardening и реальными provider integrations.

Backend не должен заставлять frontend вычислять authoritative business state, обращаться к persistence model или повторять domain rules.

## 2. Scope

Провести inventory и стабилизацию всех пользовательских `/api/v1/**` API:

- authentication/session/self-service;
- customers, contacts, segments;
- contracts;
- invoices, payments, allocations;
- collection cases, promises, disputes, actions;
- campaigns and campaign runs;
- messages/delivery monitoring;
- imports;
- templates/configuration;
- files/generated documents;
- dashboard/read models.

## 3. Required artifact

Создать `docs/frontend/frontend-api-baseline.md` с таблицей:

```text
Screen/Use case
Endpoint
Method
Request DTO
Response DTO
Required authority
Pagination
Sorting/filtering
Stable error codes
Frontend readiness
```

## 4. Contract rules

- `/api/v1` считается frontend baseline после аудита;
- tenant id берётся только из security context;
- JPA entities не являются public DTO;
- high-volume lists paginated;
- stable sort с deterministic tie-breaker;
- frontend использует `ProblemDetail.code`, а не exception text;
- деньги только `BigDecimal` + currency;
- time-dependent fields используют backend `Clock`/business ZoneId;
- lifecycle transitions идут через command endpoints, не arbitrary `PATCH status`;
- financial truth остаётся в Receivable domain;
- Collection хранит workflow state, но не копирует mutable financial balance.

## 5. Compatibility gate

Добавить automated OpenAPI snapshot/compatibility check для `/api/v1`.

Breaking changes после freeze требуют одного из:

1. backward-compatible additive change;
2. explicit migration plan;
3. versioned endpoint.

## 6. Acceptance

Frontend может реализовать основные пользовательские экраны без:

- direct DB access;
- unbounded client-side filtering;
- N+1 orchestration с десятками REST calls;
- знания internal entities;
- вычисления authoritative state;
- hardcoded parsing exception messages.

## 7. Out of scope

- production RabbitMQ hardening;
- real KumoMTA deployment;
- SMS/WhatsApp/Telegram adapters;
- redesign existing bounded contexts.
