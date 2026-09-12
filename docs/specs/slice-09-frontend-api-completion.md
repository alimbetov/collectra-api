# Slice 9 — Frontend API Completion

Status: PLANNED  
Depends on: existing identity/import/template/file/campaign/message APIs and Slice 8  
Suggested implementation branches: `feat/slice-09a-customer-contract-api`, `feat/slice-09b-receivables-payments-api`, `feat/slice-09c-collection-api`, `feat/slice-09d-frontend-support-api`

## 1. Цель

Довести backend Collectra до состояния **Frontend API Ready**: React/admin frontend должен иметь стабильный tenant-scoped REST API для основных пользовательских экранов и не должен:

- обращаться напрямую к PostgreSQL;
- самостоятельно вычислять authoritative business state;
- загружать неограниченные `List<>` и фильтровать их в браузере;
- собирать dashboard из большого количества несвязанных запросов;
- знать JPA entity structure;
- обходить tenant/security constraints;
- реализовывать собственную generic query language поверх API.

Slice 9 является master-spec и реализуется четырьмя независимо reviewable sub-slices:

```text
9A Customer + Contract API
9B Receivables + Payments API
9C Collection API
9D Frontend Support API
```

После merge 9A–9D `/api/v1` считается frontend contract baseline. Дальнейшие breaking changes требуют явной migration/versioning strategy и automated compatibility check.

## 2. Текущий baseline

Не строить повторно уже существующие возможности.

В `main` уже существуют:

- identity/authentication/tenant context;
- `Customer` и customer contact/segment domain types;
- import domain, включая `ImportBatch`;
- templates/versioned configuration;
- FileService и async generated documents;
- campaigns, campaign runs, recipients;
- Message delivery API из Slice 8 с bounded paging/filtering;
- common exception handling, PostgreSQL/Liquibase integration-test runtime.

`collection` module на момент подготовки этого ТЗ не содержит business implementation, поэтому Slice 9C включает минимальный collection domain/persistence/state model, а не только HTTP controllers.

До начала каждого sub-slice разработчик обязан повторно сверить актуальный `main`: если предыдущий PR уже реализовал часть scope, specification адаптируется к фактическому коду.

### 2.1 Domain ownership rule

Slice 9 завершает presentation/query surface и **не должен молча перепроектировать domain**.

Перед добавлением нового aggregate/table/authoritative field разработчик обязан определить существующий source of truth.

Правила:

- Slice 9 MUST NOT создавать второй authoritative source для уже существующего business fact;
- если authoritative aggregate уже существует, frontend API использует его напрямую через application/query service;
- для frontend-specific list/detail/dashboard shape допускаются query projections/read models, но они не становятся новым source of truth;
- новая authoritative table/domain aggregate создаётся только если после проверки актуального `main` действительно отсутствует существующий owner;
- ownership нового domain state должен быть явно описан в соответствующем sub-slice/PR;
- derived fields (`outstandingAmount`, `paidAmount`, `daysOverdue`, eligibility/status counters и т.п.) не должны независимо поддерживаться в нескольких aggregates без формально заданной consistency strategy.

Пример: если финансовое состояние уже определяется `Invoice + Payment + PaymentAllocation`, Slice 9 не должен создавать отдельный independently mutable `CollectionAccount.outstandingAmount` только ради UI.

## 3. Общий API contract для Slice 9

### 3.1 Base path и representation

Tenant-facing endpoints:

```text
/api/v1/...
```

Platform administration использует отдельный namespace:

```text
/admin/api/v1/...
```

Использовать JSON DTO. JPA entities напрямую из controller не возвращать.

Разделять модели минимум на:

```text
<ListItemResponse>
<DetailResponse>
<CreateRequest>
<UpdateRequest>
```

если list и detail требуют различный объём данных.

Contract conventions:

- UUID передавать как UUID;
- timestamps — ISO-8601 UTC (`Instant`);
- business date — `YYYY-MM-DD` (`LocalDate`);
- денежные значения — decimal/`BigDecimal` + ISO-4217 currency;
- `float`/`double` для денежных сумм запрещены;
- semantics `null`, отсутствующего поля и `[]` должны быть стабильными и задокументированными;
- public enum values являются частью API contract и не переименовываются без compatibility/migration strategy.

### 3.2 Pagination

Любой endpoint, который потенциально может вернуть более ~100 строк, MUST быть paginated.

Default contract:

```text
page=0
size=50
max size=200
```

Ошибки:

```text
page < 0      -> 400
size <= 0     -> 400
size > 200    -> 400
```

Не silently clamp `size`.

Для экранов, где frontend реально использует totals и `COUNT(*)` остаётся разумным, вернуть Page contract:

```json
{
  "items": [],
  "page": 0,
  "size": 50,
  "totalElements": 1254,
  "totalPages": 26,
  "hasNext": true
}
```

Для high-volume/expensive-count ресурсов использовать Slice contract:

```json
{
  "items": [],
  "page": 0,
  "size": 50,
  "hasNext": true
}
```

`messages` остаётся Slice-style API. Import row errors также предпочтительно делать Slice, если точный total требует дорогого count.

### 3.3 Sorting

Query syntax:

```text
sort=createdAt,desc
sort=displayName,asc
```

Каждый endpoint имеет explicit allowlist sortable fields. Передавать arbitrary entity/property/SQL field запрещено.

Default stable order для большинства списков:

```text
createdAt DESC, id DESC
```

Если пользователь сортирует по non-unique полю, repository MUST добавлять unique deterministic tie-breaker `id`.

Unknown sort field/direction -> `400`.

### 3.4 Filtering и search semantics

Использовать fixed typed business filters и AND semantics.

Пример:

```text
status=ACTIVE&managerId=<uuid>&createdFrom=...&createdTo=...
```

означает conjunction всех переданных условий.

Не вводить generic RSQL/OData/query DSL ради Slice 9.

Для каждого `search`/string filter endpoint обязан определить:

- trim semantics;
- case sensitivity;
- exact/prefix/contains semantics;
- min/max input length;
- конкретные searchable fields;
- индексную/query strategy для ожидаемого dataset.

Default recommendation для human-readable search: trim, case-insensitive, bounded contains/prefix только по явно перечисленным полям. Не применять `%term%` механически к большим таблицам без подходящего PostgreSQL index/query plan.

Wildcard/raw SQL syntax от клиента не интерпретировать.

Invalid UUID/enum/date/decimal/range -> `400`.

### 3.5 Tenant isolation

Tenant id для tenant-facing API берётся только из authenticated principal/security context.

Caller-controlled `tenantId` из query/body/path/header не является источником authorization.

Tenant isolation MUST выполняться в repository/SQL query:

```text
tenant_id = :tenantId
```

Application-side post filtering запрещён.

Нельзя:

```java
repository.findById(id)
```

и затем проверять tenant в Java.

Нужно:

```java
repository.findByIdAndTenantId(id, tenantId)
```

или equivalent tenant-scoped projection/query.

Для nested resources query одновременно проверяет tenant + parent id + child id.

Чужой tenant, неправильный parent path и отсутствующий resource должны быть externally indistinguishable `404`.

Page count query также MUST содержать tenant restriction.

### 3.6 Authentication/authorization — минимальная модель

В Slice 9 не вводить сложный корпоративный RBAC без реального business requirement.

Поддерживаются три типа principal:

```text
PLATFORM_ADMIN
TENANT_USER
TECHNICAL_ACCOUNT
```

Contract:

- `TENANT_USER` всегда связан ровно с одним tenant;
- `TECHNICAL_ACCOUNT` всегда связан ровно с одним tenant;
- `PLATFORM_ADMIN` не tenant-bound и работает через `/admin/api/v1/**`;
- обычный tenant-facing `/api/v1/**` не должен автоматически разрешать cross-tenant access только потому, что caller является platform admin;
- fine-grained business RBAC внутри tenant сознательно out-of-scope для Slice 9 до появления подтверждённого требования.

`TENANT_USER` получает coarse-grained доступ к frontend API своего tenant.

`TECHNICAL_ACCOUNT` использует небольшой набор scopes для machine-to-machine интеграций. Минимальная модель scopes:

```text
customer:read
customer:write
invoice:read
invoice:write
payment:read
payment:write
import:execute
message:read
```

Добавлять scope только под реальный integration use case. Не создавать provider-specific роли `ROLE_1C`, `ROLE_SAP`, `ROLE_ERP` и т.п.

Expected authorization semantics:

```text
unauthenticated                                      -> 401
TENANT_USER -> own tenant resource                   -> allowed
TENANT_USER -> foreign tenant resource               -> 404
TECHNICAL_ACCOUNT + required scope                   -> allowed
TECHNICAL_ACCOUNT without required write/read scope  -> 403
TENANT_USER -> /admin/api/v1/**                      -> 403
PLATFORM_ADMIN -> /admin/api/v1/**                   -> allowed
```

### 3.7 HTTP/error semantics

Baseline:

```text
200 successful read/update
201 successful create
204 successful command without response
400 invalid request/query parameter
401 unauthenticated
403 authenticated but not authorized
404 resource absent / wrong tenant / wrong parent hierarchy
409 business conflict / illegal transition / duplicate business key
```

Public API должен иметь единый machine-readable error contract. Предпочтительно использовать Spring `ProblemDetail` / RFC 9457 semantics либо существующий equivalent, но frontend не должен парсить exception message.

Минимальный error payload должен стабильно предоставлять:

```text
status
title/code
requestId
fieldErrors[] when validation applies
```

`code` является стабильным machine-readable identifier. Stack trace, SQL/provider exception, PII и internal class names наружу не возвращать.

### 3.8 Concurrency, audit и time

Mutable business aggregates должны использовать existing audit fields и application `Clock`.

Для financial/workflow records, где concurrent edits существенны, использовать optimistic locking/version или explicit row locking согласно invariant.

Financial/workflow transition audit должен позволять восстановить минимум actor/time/action/reason там, где transition имеет business significance.

Hard delete business history по умолчанию запрещён. Использовать status/archive/reversal semantics.

### 3.9 Query/read-model boundary

List/dashboard endpoints должны быть оптимизированы под чтение и не обязаны материализовать full JPA aggregate.

Допускаются:

- DTO/interface projections;
- explicit JPQL/native queries;
- dedicated query repositories;
- bounded read models/materialized views при доказанной необходимости.

Запрещено строить list DTO через цикл по entities с lazy navigation, создающий N+1.

Read projection может дублировать representation для чтения, но не становится independently mutable source of truth.

## 4. Slice 9A — Customer + Contract API

### 4.1 Customer list

```text
GET /api/v1/customers
```

MVP filters, если соответствующее поле существует в domain:

```text
search
status
customerType
managerId
segment
externalId
email
phone
createdFrom
createdTo
```

`search` минимум по `displayName` и `externalId`; дополнительные поля включать только при наличии корректных indexes/use case.

Sortable allowlist минимум:

```text
createdAt
updatedAt
displayName
externalId
```

List response не должен загружать unbounded contacts/contracts/invoices nested collections.

### 4.2 Customer detail/write

```text
GET   /api/v1/customers/{customerId}
POST  /api/v1/customers
PATCH /api/v1/customers/{customerId}
```

Использовать существующие invariants `Customer`: tenant-scoped unique `externalId`, customer type, display name, status, manager, preferred locale/timezone, custom fields.

Изменение status должно проходить через domain method, не direct setter.

Не добавлять physical DELETE customer в MVP.

### 4.3 Customer contacts

Existing email/phone domain types должны быть доступны frontend через понятный contract.

Предпочтительно:

```text
GET    /api/v1/customers/{customerId}/contacts
POST   /api/v1/customers/{customerId}/contacts
PATCH  /api/v1/customers/{customerId}/contacts/{contactId}
DELETE /api/v1/customers/{customerId}/contacts/{contactId}
```

`DELETE` допустим только если текущая domain semantics действительно допускает удаление контакта; иначе заменить на deactivate/status command.

DTO должен скрывать internal persistence details. Email/phone считаются PII: не писать raw values в application logs/errors.

Если contact list bounded существующими domain limits (например <=5 email и <=2 phone), pagination для nested contacts не нужна.

### 4.4 Segments

Сначала классифицировать текущую модель segment:

- если это tenant-configurable resource — дать CRUD/list API с tenant scope;
- если это bounded enum/value — включить в reference data 9D и не создавать искусственный CRUD.

Если configurable:

```text
GET   /api/v1/segments
GET   /api/v1/segments/{segmentId}
POST  /api/v1/segments
PATCH /api/v1/segments/{segmentId}
```

List paginated, filter `search/status`, stable sort.

### 4.5 Contracts

До code implementation проверить, существует ли contract aggregate/persistence в актуальном `main`.

Если authoritative Contract уже существует — переиспользовать его и добавить только недостающий application/query/API layer.

Если authoritative Contract отсутствует — 9A может создать минимальную domain/persistence foundation, но PR обязан явно зафиксировать ownership и доказать отсутствие competing source of truth.

Public contract:

```text
GET   /api/v1/contracts
GET   /api/v1/contracts/{contractId}
POST  /api/v1/contracts
PATCH /api/v1/contracts/{contractId}
```

Business filters:

```text
customerId
status
contractNumber/externalId
validFrom
validTo
renewalFrom
renewalTo
search
```

Не возвращать document bytes внутри contract DTO. Если contract связан с FileService, возвращать safe file metadata/id/link contract.

### 4.6 9A acceptance criteria

Frontend способен реализовать:

```text
Customers table
Customer detail
Customer contacts editor
Segments selector/management when configurable
Contracts table
Contract detail/editor
```

без client-side filtering больших наборов и без N+1 list materialization.

## 5. Slice 9B — Receivables + Payments API

### 5.1 Domain readiness gate

До implementation сверить актуальные Invoice/Receivable/Payment entities/tables и определить authoritative owner каждого финансового факта.

Если какая-либо сущность существует только как import projection или отсутствует как authoritative business aggregate, сначала определить, нужен ли новый aggregate вообще. Query/read-model потребность frontend сама по себе не является основанием создавать новый mutable domain aggregate.

Не создавать второй competing Invoice/Payment/Balance aggregate рядом с уже существующим.

### 5.2 Invoices / receivables

```text
GET   /api/v1/invoices
GET   /api/v1/invoices/{invoiceId}
POST  /api/v1/invoices
PATCH /api/v1/invoices/{invoiceId}
```

POST/PATCH нужны для manual/API correction flows только в пределах подтверждённых domain rules. Если financial record должен быть immutable после import, использовать explicit correction/status command вместо arbitrary patch.

Filters:

```text
customerId
contractId
status
currency
invoiceNumber
search
issuedFrom
issuedTo
dueFrom
dueTo
overdue=true|false
amountMin
amountMax
outstandingMin
outstandingMax
```

List/detail должны возвращать backend-computed authoritative fields, где применимо:

```text
amount
paidAmount
outstandingAmount
currency
status
dueDate
daysOverdue
```

`paidAmount`, `outstandingAmount`, `status`, `daysOverdue` и collection eligibility MUST происходить из единой authoritative financial model/domain rules. Slice 9 не вводит собственные параллельные финансовые расчёты только для frontend.

Frontend не должен сам вычислять invoice status/days overdue/outstanding from unrelated records.

### 5.3 Payments

```text
GET  /api/v1/payments
GET  /api/v1/payments/{paymentId}
POST /api/v1/payments
```

Предпочитать immutable payment + correction/reversal model вместо unrestricted PATCH финансового факта.

Filters:

```text
customerId
invoiceId (через allocation relation)
status
currency
paymentReference
paymentFrom
paymentTo
amountMin
amountMax
unallocatedOnly
search
```

### 5.4 Allocations

Minimum API:

```text
GET  /api/v1/payments/{paymentId}/allocations
POST /api/v1/payments/{paymentId}/allocations
GET  /api/v1/invoices/{invoiceId}/allocations
```

Для отмены allocation предпочтительно explicit reversal:

```text
POST /api/v1/payments/{paymentId}/allocations/{allocationId}/reverse
```

а не destructive DELETE.

Mandatory invariants:

- allocation amount > 0;
- payment/invoice/allocation одного tenant;
- no over-allocation payment;
- no over-payment invoice, если business rule не разрешает credit;
- currency compatibility либо explicit FX rule; implicit conversion запрещена;
- customer consistency, если payment привязан к customer;
- allocation + derived payment/invoice state меняются атомарно в одной PostgreSQL transaction;
- concurrent allocation не может нарушить totals;
- retry/duplicate request не должен silently double-allocate; mutation command должен иметь idempotency strategy либо deterministic conflict semantics;
- reversal audit сохраняется.

### 5.5 9B acceptance criteria

Frontend способен реализовать:

```text
Receivables table/detail
Overdue filter
Customer receivables view
Payments table/detail
Payment allocation/reconciliation UI
```

с authoritative balances от backend и без duplicated financial source of truth.

## 6. Slice 9C — Collection API

### 6.1 Domain foundation

Так как текущий `collection` module не содержит implementation, Slice 9C включает только минимальный новый authoritative collection domain, если повторная сверка `main` подтверждает отсутствие существующего owner:

```text
CollectionCase
PromiseToPay
Dispute
CollectionAction (Next Action)
```

Не вводить generic BPM/workflow engine.

Collection domain владеет collection workflow state, но не должен становиться альтернативным owner invoice/payment balances. Финансовые значения для collection read models берутся из authoritative receivables/payment model.

### 6.2 CollectionCase

Minimum fields должны покрывать:

```text
id
tenantId
customerId
invoiceId (nullable только если case действительно customer-level)
status
priority
assignedTo
openedAt
closedAt
nextActionAt
version/audit fields
```

Public API:

```text
GET   /api/v1/collection-cases
GET   /api/v1/collection-cases/{caseId}
POST  /api/v1/collection-cases
PATCH /api/v1/collection-cases/{caseId}
```

Filters:

```text
customerId
invoiceId
status
assignedTo
priority
overdueDaysMin
overdueDaysMax
nextActionFrom
nextActionTo
search
```

Status transition должен быть bounded state machine. Arbitrary `status=X` без transition validation запрещён.

### 6.3 Promise-to-Pay

```text
GET   /api/v1/promises-to-pay
GET   /api/v1/promises-to-pay/{promiseId}
POST  /api/v1/promises-to-pay
PATCH /api/v1/promises-to-pay/{promiseId}
```

Filters:

```text
customerId
caseId
status
promiseFrom
promiseTo
```

Model должен поддерживать минимум promised amount/date, currency, status and fulfillment/breach outcome.

Promise lifecycle должен различать active/fulfilled/broken/cancelled semantics. Не удалять history.

### 6.4 Disputes

```text
GET   /api/v1/disputes
GET   /api/v1/disputes/{disputeId}
POST  /api/v1/disputes
PATCH /api/v1/disputes/{disputeId}
```

Filters минимум:

```text
customerId
caseId
status
category
createdFrom
createdTo
```

Resolution хранит status, resolution summary/code, resolvedAt/actor; raw unbounded internal notes не должны автоматически становиться публичным API/log payload.

### 6.5 Next actions / work queue

Resource name API: `collection-actions`.

```text
GET   /api/v1/collection-actions
GET   /api/v1/collection-actions/{actionId}
POST  /api/v1/collection-actions
PATCH /api/v1/collection-actions/{actionId}
```

Filters:

```text
caseId
customerId
assignedTo
status
type
dueFrom
dueTo
overdue=true|false
```

Это основной work-queue endpoint collection manager, поэтому default sorting рекомендуется:

```text
dueAt ASC, priority DESC, id ASC
```

### 6.6 Collection timeline

Добавить paginated unified timeline:

```text
GET /api/v1/collection-cases/{caseId}/timeline
```

Timeline может агрегировать promises/disputes/actions/state transitions через backend projection. Frontend не должен скачивать четыре списка и пытаться самостоятельно восстановить business chronology.

### 6.7 9C invariants

- no hard delete collection history;
- all parent-child lookups tenant-scoped;
- state transitions validated domain-side;
- financial promises use decimal + currency;
- optimistic versioning/locking для conflicting operator updates;
- actor/time/reason audit для significant transitions;
- notification delivery failure не должен автоматически терять collection state;
- collection domain не зависит от HTTP/provider adapters;
- collection state не дублирует authoritative invoice/payment balances.

### 6.8 9C acceptance criteria

Frontend способен реализовать:

```text
Collection work queue
Case detail/timeline
Promise-to-Pay management
Dispute management
Next action scheduling/completion
```

## 7. Slice 9D — Frontend Support API

### 7.1 Dashboard read models

Не заставлять frontend строить dashboard через десятки list endpoints.

Добавить bounded aggregate API:

```text
GET /api/v1/dashboard/summary
GET /api/v1/dashboard/receivables
GET /api/v1/dashboard/delivery
GET /api/v1/dashboard/collections
```

`summary` минимум должен позволять показать high-level cards без N+1/network fan-out.

Receivables aggregation:

- outstanding totals;
- overdue totals;
- due today/soon;
- aging buckets;
- affected customers.

**Запрещено суммировать разные currencies в одно число без существующей authoritative FX/base-currency model.** Пока FX отсутствует, totals группировать по currency.

Delivery dashboard использует persisted/cached delivery counters/metrics semantics, а не пересчитывает Message table без необходимости.

Collection dashboard: open cases, overdue actions, promises due/broken, disputes open.

Dashboard queries tenant-scoped и должны иметь indexes/materialized/read-model strategy при необходимости; тяжелая full-table aggregation на каждый refresh недопустима.

Dashboard read model является projection и не становится новым owner business state.

### 7.2 Reference data

Добавить один согласованный read API, например:

```text
GET /api/v1/reference-data
```

или bounded sub-resources, если payload становится слишком большим:

```text
GET /api/v1/reference-data/channels
GET /api/v1/reference-data/currencies
GET /api/v1/reference-data/locales
GET /api/v1/reference-data/statuses
```

В reference data отдавать compile-time/static code lists, необходимые UI select/filter controls.

Tenant-configurable business entities (например configurable segments) не маскировать под static reference-data; для них остаётся полноценный resource API.

### 7.3 Import history

Существующий `ImportBatch` использовать как source of truth.

Добавить/нормализовать:

```text
GET /api/v1/imports
GET /api/v1/imports/{importId}
GET /api/v1/imports/{importId}/errors
```

Filters:

```text
type/definition/schema where applicable
status
filename/search
createdFrom
createdTo
```

Import list paginated. Error rows paginated/Slice и не возвращают unbounded input/raw payload, если он может содержать PII. Detail должен показывать безопасные counters/status/timestamps/file metadata.

### 7.4 Generated documents

Добавить/нормализовать read API:

```text
GET /api/v1/generated-documents
GET /api/v1/generated-documents/{documentId}
```

Filters:

```text
status
type
customerId
invoiceId
createdFrom
createdTo
```

Ответ возвращает metadata/status/safe error code-summary/file reference. Binary content идёт через существующий FileService/download authorization contract, не inline base64.

### 7.5 Existing campaigns/runs/recipients

Текущие unbounded list endpoints привести к общему contract.

`GET /api/v1/campaigns`:

```text
page/size
search
status
channel
scheduledFrom
scheduledTo
createdFrom
createdTo
sort
```

`GET /api/v1/campaigns/{campaignId}/runs`:

```text
page/size
status
createdFrom
createdTo
sort
```

`GET /api/v1/campaigns/runs/{runId}/recipients` также MUST стать paginated, потому что recipients потенциально high-volume.

Предпочтительно нормализовать hierarchy route к:

```text
GET /api/v1/campaigns/{campaignId}/runs/{runId}/recipients
```

с tenant + campaign + run verification. Старый route либо сохранить временно совместимым, либо удалить до frontend contract freeze; не держать два расходящихся contracts.

Message list из Slice 8 остаётся Slice и не переводится на expensive total count без UI requirement.

### 7.6 Templates/versions

Existing template list/version list endpoints проверить на unbounded `List`.

Минимум template list:

```text
page/size
search
channel
status
locale where applicable
createdFrom
createdTo
sort
```

Template versions также paginated, если history unbounded.

### 7.7 Frontend compatibility boundary

До окончания 9D допустимы deliberate breaking corrections существующих `/api/v1` list response contracts, поскольку frontend baseline ещё не заморожен.

После 9D следующие изменения считаются breaking:

- удаление endpoint;
- rename/removal response field;
- изменение field type;
- optional -> required;
- удаление/переименование enum value;
- `List -> Page/Slice` change;
- path restructuring;
- несовместимое изменение HTTP status/request semantics.

До завершения 9D добавить CI API compatibility/breaking-change check для generated OpenAPI baseline. После freeze CI MUST fail на незаявленное breaking change.

## 8. Database/query/index requirements

Новые list endpoints не реализовывать через `findAll()` + Java filtering.

Repository queries должны push down tenant/filter/sort/paging в PostgreSQL.

List endpoints SHOULD использовать DTO/query projections и не materialize full aggregate без необходимости.

Индексы проектировать от фактических query patterns. Типовые кандидаты:

```text
(tenant_id, created_at DESC, id DESC)
(tenant_id, status, created_at DESC, id DESC)
(tenant_id, customer_id, created_at DESC, id DESC)
(tenant_id, due_date, id)
(tenant_id, assigned_to, status, due_at, id)
```

Не создавать индекс на каждый возможный параметр mechanically.

Для каждого sub-slice предоставить representative `EXPLAIN ANALYZE` минимум для:

- default list;
- наиболее частого combined filter;
- one high-volume path.

Избегать N+1 при list DTO enrichment. Нужные names/counts получать projection/join/batch query, а не lazy loop queries.

Acceptance rule: representative list endpoint не должен выполнять lazy association traversal per row для построения DTO.

## 9. Security/privacy/observability requirements

- tenant isolation enforced in DB queries;
- tenant identity берётся из authenticated principal, не из caller-controlled parameter/header;
- PII (email/phone) не писать в logs/error messages;
- auth tokens/password hashes/raw provider responses не возвращать и не логировать;
- custom fields не должны обходить authorization/privacy policy;
- financial amounts and collection history доступны только caller, которому разрешён соответствующий tenant/API contract;
- dashboard не должен становиться privilege-escalation path;
- `404` не раскрывает cross-tenant existence;
- application logs содержат `requestId`, endpoint/operation, HTTP status, latency и безопасный tenant identifier/context;
- raw request/response body tenant business API по умолчанию не логируется;
- email/phone/payment payload/custom fields должны проходить redaction/masking policy;
- operational metrics не должны использовать unbounded-cardinality labels (`customerId`, `invoiceId`, raw tenant/customer/email и т.п.).

## 10. Tests

Каждый paginated list endpoint должен иметь минимум:

- default page/size;
- max size 200;
- invalid page/size -> 400;
- stable deterministic order с unique tie-breaker;
- each important single filter;
- representative combined filters с AND semantics;
- defined search trim/case/matching semantics;
- allowed sorting;
- invalid sort -> 400;
- empty result;
- tenant A не видит tenant B;
- wrong nested parent -> 404;
- unauthenticated -> 401;
- `TENANT_USER` own tenant access;
- `TECHNICAL_ACCOUNT` required-scope positive/negative cases для exposed M2M endpoint;
- tenant user access to `/admin/api/v1/**` -> 403;
- no duplicate rows from joins;
- no N+1 regression для representative list path, где это practically testable;
- canonical error payload/code/requestId для representative 400/403/404/409.

9A дополнительно:

- duplicate tenant/externalId conflict;
- contact validation/ownership;
- customer status/update rules.

9B дополнительно:

- exact decimal amounts;
- outstanding/status calculations from authoritative model;
- partial allocations;
- over-allocation rejection;
- currency/customer mismatch;
- concurrent allocations;
- reversal;
- rollback atomicity.

9C дополнительно:

- valid/invalid state transitions;
- optimistic concurrent update;
- promise fulfillment/breach;
- dispute resolution;
- overdue action filter;
- timeline stable chronology.

9D дополнительно:

- dashboard currency grouping;
- aggregate tenant isolation;
- import errors paging/redaction;
- generated document access;
- campaigns/runs/recipients/templates pagination migration;
- OpenAPI compatibility check detects representative breaking change.

Persistence/concurrency tests — PostgreSQL/Testcontainers согласно existing test-runtime contract.

## 11. Performance acceptance

Frontend list endpoints должны быть usable на realistic tenant dataset, а не только на десятках fixtures.

Минимальные требования:

- DB-side paging/filtering/sorting;
- DTO/query projection для representative list paths;
- no unbounded collection load;
- no lazy-per-row N+1;
- indexes justified query plans;
- dashboard query count bounded;
- expensive `COUNT(*)` не использовать там, где UI не требует totals;
- max page size enforced server-side.

Не вводить premature Elasticsearch/OpenSearch для этого Slice. PostgreSQL search/indexing использовать до появления доказанной необходимости отдельного search engine.

## 12. OpenAPI/frontend contract

Все public endpoints Slice 9 должны присутствовать в generated OpenAPI.

Перед `Frontend API Ready` проверить:

- query params documented;
- enum values visible;
- response DTO documented;
- 400/401/403/404/409 semantics consistent;
- canonical error schema documented;
- Page/Slice schema единообразна;
- nullable fields explicit;
- money/date/time conventions consistent;
- examples не содержат real PII/secrets.

Frontend TypeScript client должен иметь возможность генерироваться/типизироваться из stable API contract без знания persistence model.

После 9D generated OpenAPI baseline является compatibility boundary и проверяется в CI.

## 13. Порядок реализации

Рекомендуемый порядок:

```text
9A Customer + Contract
        |
        v
9B Receivables + Payments
        |
        v
9C Collection
        |
        v
9D Frontend Support + API normalization + compatibility gate
```

9A–9D реализуются отдельными PR от актуального `main`. Documentation/spec branch не использовать как base code branch.

9D зависит от business read models 9A–9C для полноценного dashboard.

## 14. Explicit out-of-scope

Не входит в Slice 9:

- React/frontend implementation;
- сложный fine-grained business RBAC внутри tenant до появления подтверждённого requirement;
- отдельный IAM/ABAC framework;
- SMS/WhatsApp/Telegram/Push provider adapters;
- generic workflow/BPM engine;
- generic reporting/query DSL;
- Elasticsearch/OpenSearch без отдельного performance requirement;
- billing/plans/usage metering;
- 1C/ERP-specific connectors;
- provider-specific technical-account roles;
- arbitrary cross-tenant admin search;
- FX conversion engine, если его нет в текущем domain;
- hard delete financial/collection history.

## 15. Definition of Done — sub-slice

Каждый 9A/9B/9C/9D считается готовым только если:

1. API/domain requirements соответствующего раздела реализованы.
2. Domain ownership/source of truth проверен и не создан competing authoritative model.
3. Tenant isolation находится в repository/SQL.
4. Paginated endpoints имеют bounded size, filters, stable sorting с unique tie-breaker.
5. DTO не экспонируют JPA entities/internal secrets.
6. Security tests для principal/tenant/scopes соответствующего scope green.
7. PostgreSQL integration tests green.
8. Liquibase migrations включены в master changelog, если schema менялась.
9. Representative query plans/indexes проверены, list query не содержит lazy-per-row N+1.
10. OpenAPI отражает contract и canonical errors.
11. `mvn verify` green.
12. PR не auto-merge без явного решения.

## 16. Definition of Done — Frontend API Ready

Collectra backend получает статус **Frontend API Ready** только после merge 9A–9D и проверки, что frontend может реализовать следующие экраны без backend workarounds:

```text
Login / session
Customers
Customer detail + contacts
Segments
Contracts
Receivables / invoices
Payments
Payment allocation / reconciliation
Collection work queue
Collection case + timeline
Promise-to-Pay
Disputes
Next actions
Imports/history/errors
Templates/versions
Campaigns/runs/recipients
Delivery messages/detail
Generated documents/files
Dashboard
Reference/select data
```

Для каждого data-grid экрана должны существовать server-side pagination, необходимые business filters и deterministic sorting.

Дополнительно должны быть выполнены общие readiness gates:

```text
tenant isolation verified
PLATFORM_ADMIN / TENANT_USER / TECHNICAL_ACCOUNT model verified
technical-account scopes verified where exposed
canonical API error contract stable
representative list endpoints free of N+1
OpenAPI breaking-change CI gate enabled
```

После этого новые frontend requirements могут добавлять endpoint-specific capabilities, но отсутствие базового CRUD/read/list/filter/paging contract не должно блокировать разработку основного UI.
