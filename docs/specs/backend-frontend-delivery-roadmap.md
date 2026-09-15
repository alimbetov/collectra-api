# Backend and Frontend Delivery Roadmap

Status: **AUDITED / READY FOR TECHNICAL SPECIFICATION**

Audit baseline: `main@2b6b112` (`2026-09-15`)

Purpose: единый порядок доработки backend под пользовательские сценарии и последовательной реализации `frontendweb`.

## 1. Решение по итогам аудита

Backend уже реализует основной MVP-контур: authentication, dashboard projections,
customers/segments, contracts, receivables, payments/allocations, collections,
templates, campaigns/runs/recipients/messages, imports, generated documents, files,
administration и delivery pipeline до provider adapters.

Создавать отдельный generic BFF, GraphQL gateway или новый read-model framework не
требуется. Frontend должен использовать существующий `/api/v1`, а backend изменяется
только для подтвержденных пробелов конкретных экранов.

Главная проблема текущего состояния — не отсутствие доменной реализации, а разрыв
между уже готовым backend и минимальным frontend:

- `frontendweb` содержит FW0/FW1: shell, router, HTTP client, login/session/refresh и
  permission guard;
- все business routes пока являются placeholder pages;
- единственный frontend test file проверяет три сценария HTTP client;
- обязательные FW1 auth/session tests отсутствуют;
- часть documentation status не соответствует фактически merged Slice 4–10A и FW1;
- OpenAPI baseline объявлен архитектурной границей, но automated compatibility gate
  отсутствует;
- critical-core JaCoCo profile существует, но не запускается CI;
- для Files нет tenant-scoped paged list API;
- import errors сейчас представляют только одну batch-level ошибку, а не durable
  record/field-level diagnostics.

## 2. Зафиксированные архитектурные решения

### 2.1 Backend

1. PostgreSQL остается authoritative source of truth.
2. RabbitMQ — at-least-once transport, provider adapters — infrastructure details.
3. Frontend работает только с public DTO `/api/v1`; JPA entities наружу не выдаются.
4. Tenant берется из authenticated context. Обычный workspace UI не передает
   `tenantId` в query/body.
5. High-volume endpoints обязаны быть tenant-scoped, bounded и paged.
6. Backend определяет деньги, eligibility, workflow state, counters и permissions;
   frontend не вычисляет authoritative business state.
7. Новые backend endpoint добавляются только под подтвержденный UI use case и проходят
   OpenAPI compatibility gate.
8. Provider hardening, реальный KumoMTA и дополнительные каналы развиваются параллельно
   и не блокируют provider-neutral frontend.

### 2.2 Frontend

1. Сохраняется React + TypeScript + Vite monorepo module `frontendweb`; microfrontend
   architecture не вводится.
2. Dependency direction: `app -> pages -> widgets -> features -> entities -> shared`.
3. TanStack Query хранит server state. Query keys включают tenant-neutral business
   filters, paging и sorting; mutations инвалидируют только затронутые keys.
4. Operational filters и pagination синхронизируются с URL, где это помогает support
   и восстановлению состояния.
5. Access token хранится in-memory, refresh token — в `sessionStorage`, как реализовано
   FW1. Переход на HttpOnly cookie требует отдельного security decision и backend
   contract; он не входит в MVP без отдельного ТЗ.
6. Каждый route имеет loading, empty, error, forbidden и success state; mutation также
   имеет pending/disabled state.
7. Permission guard управляет UX, но не заменяет backend authorization.
8. Денежные значения отображаются через общий formatter и не пересчитываются из
   плавающих `number` как источник финансовой истины.
9. Provider-specific DTO и термины KumoMTA не попадают в UI contracts.

## 3. Аудит baseline

| Область | Фактическое состояние | Вывод |
|---|---|---|
| Backend API | 201 controller mappings, screen-oriented DTO и bounded queries | Основа UI готова |
| Backend tests | 119 test classes, PostgreSQL/Testcontainers, Slice 10A security/concurrency/parser/observability tests | Сильная база, нужен обязательный coverage gate |
| Delivery | Message state machine, outbox/Rabbit, KumoMTA adapter, simulation, materialization, counters, attachments, observability | Не блокирует frontend |
| Frontend foundation | FW0 и FW1 merged | Продолжать с test closure и FW2 |
| Frontend tests | 1 test file, 3 HTTP-client cases | Недостаточно для FW1 acceptance |
| Frontend business UI | Все domain routes placeholders | Главный объем работ |
| API compatibility | OpenAPI dependency есть, snapshot/diff gate отсутствует | Блокирует безопасную параллельную разработку |
| Files | upload/get/content/download-url/delete есть, list отсутствует | Нужна узкая backend доработка перед FW11 |
| Imports | batch status есть, error endpoint синтезирует одну общую ошибку | Нужна durable error model перед полноценным FW10 |
| Messages | tenant-scoped paged list/detail есть, ручного retry endpoint нет | MVP экран оставить read-only |
| Frontend deployment | Vite dev/build есть; production image, SPA fallback и runtime config не зафиксированы | Закрыть перед release candidate |
| Documentation | roadmap/status частично описывают уже завершенные работы как READY/IN PROGRESS/BLOCKED | Нужна reconciliation |

## 4. Gap register

| ID | Priority | Gap | Решение | Блокирует |
|---|---:|---|---|---|
| G-01 | P0 | frontend lockfile/Node CI repair остается отдельной зеленой веткой | rebase/merge узкого repair PR | любое расширение frontend dependencies/tests |
| G-02 | P0 | FW1 без auth/session component/integration tests | отдельный FW1 closure PR | FW2 и уверенный refactoring auth |
| G-03 | P0 | нет OpenAPI compatibility gate | baseline artifact + automated breaking-change check | безопасную параллельную работу frontend/backend |
| G-04 | P0 | `slice10a-coverage` не исполняется CI | отдельный обязательный CI job | declared backend quality gate |
| G-05 | P1 | documentation drift | reconciliation только по доказательствам из code/CI | корректную Definition of Ready следующих ТЗ |
| G-06 | P1 | нет общей UI/RBAC/i18n/error foundation | FW2 | все business screens |
| G-07 | P1 | нет paged Files list | tenant-scoped filtered API | FW11 file registry |
| G-08 | P1 | нет record/field-level import errors | durable error storage/read API | качественный FW10 error UX |
| G-09 | P2 | нет manual message retry contract | MVP read-only; отдельное ТЗ после policy decision | не блокирует FW8 monitoring |
| G-10 | P2 | production serving/runtime config не определены | container/static hosting contract, SPA fallback, headers | release candidate |
| G-11 | P2 | нет browser E2E smoke | тонкий Playwright suite для critical journeys | release candidate |

## 5. Обязательный порядок PR

Каждый пункт ниже — отдельный узкий PR от актуального `main`. Техническое ТЗ для PR
создается после повторной проверки Definition of Ready.

### Stage 0 — Stabilization and contract freeze

#### PR 0.1 — Frontend test infrastructure repair

Suggested branch: `fix/frontendweb-ci-lock-node`.

Scope:

- согласовать Node runtime и lockfile с фактическими engines Vite/Vitest/jsdom;
- сохранить deterministic `npm ci`;
- не добавлять business/frontend behavior.

Done:

- `npm ci`, `typecheck`, `test:ci`, `build` green в CI;
- branch rebased на актуальный `main` и diff остается инфраструктурным.

#### PR 0.2 — FW1 authentication closure

Suggested branch: `test/frontendweb-fw1-auth-closure`.

Scope:

- login success/failure;
- bootstrap with/without refresh token;
- single-flight refresh при конкурентных `401`;
- original request replay ровно один раз;
- refresh failure очищает tokens и переводит session в anonymous;
- logout and cross-tab/session invalidation behavior;
- `RequireAuth` redirect и return URL;
- `PermissionGuard` allowed/denied paths;
- stale `frontendweb/README.md` привести к фактическому FW1 state.

Не менять backend auth contract в этом PR.

#### PR 0.3 — OpenAPI baseline and compatibility gate

Suggested branch: `ci/backend-openapi-compatibility`.

Scope:

- детерминированно генерировать public `/api/v1` OpenAPI artifact;
- сохранить reviewed baseline в repository;
- CI сравнивает current schema с baseline и запрещает удаление endpoint/method,
  required/response fields, несовместимое изменение type/enum/status;
- additive changes разрешены;
- documented baseline update procedure требует явного review;
- representative breaking-change test доказывает работу gate.

Не писать собственный diff framework, если зрелый совместимый инструмент закрывает
требования.

#### PR 0.4 — Critical-core coverage gate

Suggested branch: `ci/slice10a-coverage-gate`.

Scope:

- CI запускает `mvn --batch-mode --no-transfer-progress clean verify -Pslice10a-coverage`;
- thresholds остаются `LINE >= 95%`, `BRANCH >= 90%` для уже определенного critical core;
- job является required check;
- не ослаблять exclusions/thresholds ради зеленого результата.

#### PR 0.5 — Documentation reconciliation

Suggested branch: `docs/reconcile-delivery-status`.

Scope:

- `docs/specs/README.md`, frontend start gate, Slice 4–10A closure docs и
  `frontendweb/README.md` отражают merged code и подтвержденный CI;
- различать `IMPLEMENTED`, `CI VERIFIED`, `REAL ENV ACCEPTED`;
- незапущенный real KumoMTA не объявлять завершенным acceptance.

### Stage 1 — Shared frontend platform

#### PR 1 — FW2A Workspace shell and route states

- responsive authenticated shell and navigation;
- 403/404 and route error boundary;
- loading/empty/error/forbidden primitives;
- global notification/toast and confirmation dialog;
- accessibility baseline: keyboard navigation, labels, focus and contrast.

#### PR 2 — FW2B RBAC, i18n and formatting

- central route/nav/action permission map;
- navigation hides unavailable domains, direct URL still relies on backend enforcement;
- Russian and English translation foundation, no scattered user-facing literals;
- date/time, amount, count and status formatters;
- ProblemDetail mapping to safe user messages and correlation/support identifiers.

Exit for FW2:

- no business feature creates its own competing table/modal/permission/error primitives;
- shared primitives have component tests;
- architecture test/lint prevents reverse dependency imports.

### Stage 2 — First usable workspace

#### PR 3 — FW3 Dashboard

- summary cards from backend projections;
- overdue/action queues;
- loading/empty/error/forbidden states;
- links preserve filters into relevant registers;
- no frontend recomputation of financial totals.

#### PR 4 — FW4 Customers

Split when necessary:

- FW4A paged/searchable customer list with URL filters;
- FW4B customer detail and safe mutations;
- FW4C segments/membership management with permission guards and invalidation tests.

#### PR 5 — FW5 Receivables

- invoices/receivables list and detail;
- contracts;
- payments and allocations;
- optimistic UI only where rollback and backend idempotency are defined;
- backend remains owner of paid/outstanding/payment status.

#### PR 6 — FW6 Collections

- collection cases, assignments, notes/tasks/actions supported by current API;
- separate collection workflow status from receivable financial status;
- mutation concurrency/conflict behavior shown explicitly to user.

### Stage 3 — Communication operations

#### PR 7 — FW7 Campaigns

- campaigns list/detail and lifecycle actions;
- run creation/detail, recipients and delivery counters;
- action availability comes from permissions and backend state;
- live polling is bounded and stops for terminal states.

#### PR 8 — FW8 Message monitoring

- tenant-scoped paged list/detail under campaign run;
- filters: status, channel, customer, paging;
- delivery attempts/errors presented with PII masking rules;
- no KumoMTA-specific DTO;
- MVP is read-only. Manual retry is excluded until backend policy defines eligible
  states, idempotency, audit trail, rate limits and counter interaction.

### Stage 4 — Content and ingestion

After FW2, FW9 may proceed in parallel with Stages 2–3 if the same shared primitives
are reused.

#### PR 9 — FW9 Templates

- templates/version list and detail;
- builder draft/validate/publish flow supported by current API;
- assets and preview;
- immutable published versions are never edited in place.

#### PR 10 — Backend import diagnostics

Suggested branch: `feat/import-record-errors-api`.

Required technical specification must define:

- durable table/model for batch error items with tenant/batch/record/field/code/detail;
- bounded error text and safe source-value masking;
- atomic persistence semantics relative to batch failure/progress;
- tenant-scoped paged endpoint with stable ordering;
- indexes for `(tenant_id, batch_id, id)` or selected cursor;
- retention and deletion relationship with import batch/source file;
- PostgreSQL, security, paging and PII tests.

#### PR 11 — FW10 Imports

- source schemas and mapping profiles;
- upload/JSON/XML submission with idempotency key;
- progress/status and generated document links;
- field/record-level errors from PR 10;
- no unbounded client-side parsing of production files.

#### PR 12 — Backend files registry API

Suggested branch: `feat/files-list-api`.

Contract:

- `GET /api/v1/files` with bounded page/size;
- fixed filters: category, projectId, createdFrom/createdTo and optional filename query;
- deterministic newest-first ordering plus `id` tie-breaker;
- tenant filter in repository query, authorization identical to file read;
- response uses `FileMetadata`-compatible public DTO without storage key/internal path;
- indexes derived from actual query plan;
- PostgreSQL tenant isolation, filters, paging and security tests.

#### PR 13 — FW11 Files and generated documents

- upload, paged registry, metadata, safe download and permitted delete;
- generated documents and attachment readiness;
- large content is streamed/downloaded, never buffered into application state;
- unavailable/expired content has explicit UX.

### Stage 5 — Administration and release closure

#### PR 14 — FW12 Administration

- tenant users/roles/permissions and reference data exposed by current admin API;
- destructive actions require confirmation and backend authorization;
- permission matrix tests cover navigation, route and actions.

#### PR 15 — Frontend production delivery

- production build artifact/image;
- runtime API base URL strategy without rebuilding per environment where practical;
- SPA fallback, compression/cache rules and immutable asset caching;
- CSP and security headers compatible with actual auth and API use;
- health/readiness contract and deployment documentation;
- source maps and logging follow the project PII/security policy.

#### PR 16 — Critical browser smoke

- Playwright or equivalent thin E2E suite;
- login/refresh/logout;
- dashboard load;
- customer -> receivable -> collection navigation;
- campaign -> run -> message monitoring;
- import -> status/errors/documents;
- permission-denied and expired-session paths.

Это smoke layer, а не замена component/API/PostgreSQL tests.

## 6. Правила подготовки последующих ТЗ

Для каждого PR из плана отдельное implementation-ready ТЗ обязано зафиксировать:

1. current code baseline and dependencies;
2. exact packages, components/controllers/services/repositories and public DTO;
3. route/state diagram или state table, если есть workflow;
4. tenant isolation and authorization;
5. transaction boundaries, concurrency and idempotency для mutations;
6. DB migration/index/query plan requirements, если меняется persistence;
7. API compatibility and ProblemDetail behavior;
8. loading/empty/error/forbidden/pending UX states;
9. unit/component/MSW/PostgreSQL/security/E2E tests по уровню изменения;
10. explicit out-of-scope;
11. Definition of Ready and Definition of Done;
12. команды локальной и CI verification.

## 7. Definition of Ready

PR можно брать в разработку, когда:

- dependency PR merged в актуальный `main`;
- main CI green или независимая поломка явно отделена;
- OpenAPI baseline подтверждает нужный endpoint/DTO либо backend gap вынесен раньше;
- permission codes и tenant source определены;
- design использует существующие shared primitives;
- product behavior для destructive/concurrent/retry action не оставлен на догадку
  разработчика;
- для high-volume UI известны server paging/filter/sort semantics;
- внешняя инфраструктура либо доступна, либо имеет provider-neutral deterministic
  simulation contract.

## 8. Definition of Done для каждого frontend PR

- typecheck, tests и production build green;
- happy path и все обязательные route/mutation states реализованы;
- API calls tenant-neutral на browser boundary;
- permissions покрывают navigation, route и actions;
- server state не дублируется в ad-hoc global stores;
- queries bounded, filters/paging проверены;
- user-facing text локализуем;
- accessibility checks выполнены для измененных controls;
- logs/errors не раскрывают token, raw PII или provider credentials;
- README/route map обновлены только при фактическом изменении.

## 9. Definition of Done для каждого backend-for-frontend PR

- endpoint соответствует реальному screen use case и не дублирует существующий API;
- public DTO отделен от persistence entity;
- tenant scope применен в query, а не только после чтения;
- authorization и ProblemDetail contract протестированы;
- paging bounded, sorting deterministic, indexes подтверждены query shape;
- mutation имеет явные transaction/idempotency/concurrency semantics;
- OpenAPI generated и compatibility check green;
- unit + PostgreSQL integration + security/architecture tests green;
- `mvn verify` и required coverage gate green.

## 10. Параллельный delivery-infrastructure track

Следующие работы важны для production delivery, но не входят в критический путь
business frontend:

- Slice 10B RabbitMQ DLQ/redelivery/publisher-confirm/restart hardening;
- real KumoMTA environment acceptance и reconciliation реального SMTP outcome;
- SMS, Telegram, WhatsApp, In-App/Push adapters;
- security decision по HttpOnly refresh cookie;
- manual message retry API после утверждения operations policy.

Frontend сохраняет provider-neutral channel/status model. До готовности реальных
провайдеров разрешена управляемая deterministic simulation, но production profile не
должен случайно включать ее без явной конфигурации.

## 11. Итоговая последовательность

```text
P0 infrastructure repair
  -> FW1 test closure
  -> OpenAPI + coverage gates
  -> documentation reconciliation
  -> FW2 shared platform
  -> FW3 dashboard
  -> FW4 customers
  -> FW5 receivables
  -> FW6 collections
  -> FW7 campaigns
  -> FW8 messages (read-only)
  -> FW9 templates
  -> backend import diagnostics -> FW10 imports
  -> backend files list -> FW11 files
  -> FW12 administration
  -> production delivery + browser smoke
```

FW9 может выполняться параллельно после FW2. Backend import diagnostics и files list
можно начинать параллельно со Stage 2, но они должны быть merged до соответствующих
frontend slices.

## 12. Первый следующий шаг

1. Довести и merge узкий `fix/frontendweb-ci-lock-node`.
2. Подготовить ТЗ и реализовать `FW1 authentication closure`.
3. Параллельно отдельными backend/CI PR закрыть OpenAPI compatibility и
   `slice10a-coverage` gate.
4. Только после этого начинать FW2 как shared foundation всех business screens.

Этот документ является master execution plan. Детальные ТЗ создаются по нему по одному
PR и сверяются с актуальным `main` перед началом разработки.
