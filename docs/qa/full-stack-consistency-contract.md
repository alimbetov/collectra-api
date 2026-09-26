# Full-Stack Consistency Contract

Status: NORMATIVE
Scope: pre-VC9 functional hardening

## Single execution model
codex-pre-vc9-completion-contract.md Phase A through G is the only execution sequence. Other documents provide requirements/evidence, not alternative schedules.

## Traceability graph
Every shipped capability traces:
P1-P15 business process -> J persona/system actor -> frontend route/action or API_ONLY -> backend endpoint -> server authority/scope -> tenant invariant -> domain transition -> positive test -> negative authorization/isolation test -> exact-SHA evidence.
A missing node is a hardening gap.

## P1-P15 reconciliation
| Process | Actor | Required evidence |
|---|---|---|
| P1 registration/first login | bootstrap admin | registration API + bootstrap; if no product registration UI exists, explicitly classify onboarding API_ONLY rather than inventing UI |
| P2 login/session | human | /login, refresh-once, logout, expired/revoked behavior |
| P3 customer onboarding | Operator | customer + contacts + segments + optional contract |
| P4 contract onboarding | Operator | create/detail/lifecycle/version conflict |
| P5 receivable/import | Operator/Data Manager | invoice plus import execution/config separation |
| P6 monitoring | Operator | dashboard -> filtered receivables -> detail/deep links |
| P7 payment/allocation | Receivable Manager | payment/allocation/reversal/idempotency/concurrency |
| P8 collections | Collection Officer | real /collections workspace |
| P9 templates | Content Manager | create/version/validate/preview/publish/assets |
| P10 campaign | Campaign Manager | campaign -> run -> recipient/message |
| P11 eligibility change | Campaign/System | payment change -> recheck -> SKIPPED/no delivery |
| P12 delivery monitoring | Campaign/Support | masked read-only message diagnostics |
| P13 files/documents | file-capable actor | upload/metadata/download/delete + owning reference |
| P14 tenant admin | Tenant Admin | members/invites/roles/permissions/status/sessions |
| P15 profile/security | human | identity/me, profile/password/sessions/logout-all |

Codex adds P1-P15 individually to the completion ledger. T30 does not replace process coverage.

## Frontend/backend consistency
Frontend permissions are UX only; backend is authoritative. Frontend and backend use the same capability semantics. 401 refreshes at most once; 403 is not retried; 404 does not disclose foreign existence; 409 reloads/reconciles without blind overwrite. Financial, eligibility, retry and workflow truth is backend-derived. Lists use bounded server paging/filtering. Missing screen projection is a backend gap, not a client-side full scan or N+1 workaround. Required deep links come from frontend-user-processes.md.

## API/data compatibility
Preserve OpenAPI compatibility unless an accepted decision deliberately changes it; update API spec/client/tests together. Liquibase is forward-only: do not edit an already-released migration to repair deployed schema. Money transport is lossless decimal; JS does not recompute authoritative totals. Backend businessDate/timezone owns business meaning. Optimistic concurrency is explicit 409/reload behavior.

## Broker boundary
Current delivery tests exercise substantial listener/worker logic in-process; that does not prove RabbitMQ topology, serialization and listener wiring. Pre-VC9 production-readiness therefore requires one real RabbitMQ Testcontainers smoke:
outbox publish -> broker exchange/queue/binding -> listener deserialization -> worker claim -> deterministic adapter -> persisted Message/CampaignRun result.
Add the RabbitMQ Testcontainers dependency/config if absent. Use bounded observable polling, never sleep. No live external provider.

## Test layering
Unit: pure state/policy/mapper/parser.
Integration: Spring + PostgreSQL for repositories/transactions/security/API.
Broker integration: PostgreSQL + real RabbitMQ Testcontainer.
Frontend: Vitest/MSW for page/query/error/permission behavior.
Composite: public/admin/service APIs + real persistence/broker where practical + deterministic adapter.
Use the lowest sufficient layer plus cross-layer wiring smoke; do not duplicate every scenario at every layer.

## Production-ready wording
This gate proves a production-ready candidate, not a live production deployment. Provider credentials, infrastructure HA/capacity, deployment rollback and environment-specific observability are deployment concerns unless this repository already owns their gate.
