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
- обходить tenant/RBAC constraints;
- реализовывать собственную generic query language поверх API.

Slice 9 является master-spec и реализуется четырьмя независимо reviewable sub-slices:

```text
9A Customer + Contract API
9B Receivables + Payments API
9C Collection API
9D Frontend Support API
```

После merge 9A–9D `/api/v1` считается frontend contract baseline. Дальнейшие breaking changes требуют явной migration/versioning strategy.

## 2. Текущий baseline

Не строить повторно уже существующие возможности.

В `main` уже существуют:

- identity/authentication/RBAC/tenant context;
- `Customer` и customer contact/segment domain types;
- import domain, включая `ImportBatch`;
- templates/versioned configuration;
- FileService и async generated documents;
- campaigns, campaign runs, recipients;
- Message delivery API из Slice 8 с bounded paging/filtering;
- common exception handling, PostgreSQL/Liquibase integration-test runtime.

`collection` module на момент подготовки этого ТЗ не содержит business implementation, поэтому Slice 9C включает минимальный collection domain/persistence/state model, а не только HTTP controllers.

До начала каждого sub-slice разработчик обязан повторно сверить актуальный `main`: если предыдущий PR уже реализовал часть scope, specification адаптируется к фактическому коду.

## 3. Общий API contract для Slice 9

### 3.1 Base path и representation

Все новые endpoints:

```text
/api/v1/...
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

UUID передавать как UUID, timestamps — ISO-8601 UTC (`Instant`), business date — `YYYY-MM-DD` (`LocalDate`). Денежные значения — decimal/`BigDecimal` + ISO-4217 currency. `float`/`double` для денежных сумм запрещены.

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

Если пользователь сортирует по non-unique полю, repository MUST добавлять deterministic tie-breaker `id`.

Unknown sort field/direction -> `400`.

### 3.4 Filtering

Использовать fixed typed business filters и AND semantics.

Пример:

```text
status=ACTIVE&managerId=<uuid>&createdFrom=...&createdTo=...
```

означает conjunction всех переданных условий.

Не вводить generic RSQL/OData/query DSL ради Slice 9.

`search` допускается только как bounded endpoint-specific search по заранее определённым полям. Search string trim/length limits обязательны. Wildcard/raw SQL syntax от клиента не интерпретировать.

Invalid UUID/enum/date/decimal/range -> `400`.

### 3.5 Tenant isolation

Tenant id берётся только из authenticated context.

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

Для nested resources query одновременно проверяет tenant + parent id + child id.

Чужой tenant, неправильный parent path и отсутствующий resource должны быть externally indistinguishable `404`.

Page count query также MUST содержать tenant restriction.

### 3.6 RBAC

Использовать существующий `ROLE_HUMAN` и существующую permission convention проекта.

До реализации 9A создать/подтвердить permission matrix для business resources. Если в `main` нет подходящих permissions, добавить bounded permissions по модели:

```text
CUSTOMER_READ / CUSTOMER_MANAGE
CONTRACT_READ / CONTRACT_MANAGE
RECEIVABLE_READ / RECEIVABLE_MANAGE
PAYMENT_READ / PAYMENT_MANAGE
COLLECTION_READ / COLLECTION_MANAGE
```

Не вводить permission на каждый HTTP method без необходимости.

Dashboard/reference endpoints используют read permissions соответствующих данных; один dashboard endpoint не должен становиться privilege-escalation path.

### 3.7 HTTP semantics

Baseline:

```text
200 successful read/update
201 successful create
204 successful command without response
400 invalid request/query parameter
401 unauthenticated
403 authenticated but missing permission
404 resource absent / wrong tenant / wrong parent hierarchy
409 business conflict / illegal transition / duplicate business key
```

Validation errors должны иметь существующий единый API error format.

### 3.8 Concurrency, audit и time

Mutable business aggregates должны использовать existing audit fields и application `Clock`.

Для financial/workflow records, где concurrent edits существенны, использовать optimistic locking/version или explicit row locking согласно invariant.

Financial/workflow transition audit должен позволять восстановить минимум actor/time/action/reason там, где transition имеет business significance.

Hard delete business history по умолчанию запрещён. Использовать status/archive/reversal semantics.

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

Если contact list потенциально bounded существующими domain limits (например <=5 email и <=2 phone), pagination для nested contacts не нужна.

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

До code implementation проверить, существует ли contract aggregate/persistence в актуальном `main`. Если отсутствует, 9A включает минимальную domain/persistence foundation.

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

без client-side filtering больших наборов.

## 5. Slice 9B — Receivables + Payments API

### 5.1 Domain readiness gate

До implementation сверить актуальные Invoice/Receivable/Payment entities/tables. Если какая-либо сущность существует только как import projection или отсутствует как authoritative business aggregate, сначала создать минимальный domain/persistence contract в рамках 9B.

Не создавать второй competing Invoice/Payment aggregate рядом с уже существующим.

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

с authoritative balances от backend.

## 6. Slice 9C — Collection API

### 6.1 Domain foundation

Так как текущий `collection` module не содержит implementation, Slice 9C включает domain + persistence + API минимум для:

```text
CollectionCase
PromiseToPay
Dispute
CollectionAction (Next Action)
```

Не вводить generic BPM/workflow engine.

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
- collection domain не зависит от HTTP/provider adapters.

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

После 9D:

- response field rename/removal;
- List -> Page/Slice change;
- enum semantic change;
- path restructuring;

считаются breaking API changes и требуют migration strategy.

## 8. Database/query/index requirements

Новые list endpoints не реализовывать через `findAll()` + Java filtering.

Repository queries должны push down tenant/filter/sort/paging в PostgreSQL.

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

## 9. Security/privacy requirements

- tenant isolation enforced in DB queries;
- PII (email/phone) не писать в logs/error messages;
- auth tokens/password hashes/raw provider responses не возвращать;
- custom fields не должны обходить authorization/privacy policy;
- financial amounts and collection history доступны только соответствующим read permissions;
- dashboard не должен раскрывать данные, на которые caller не имеет underlying permission;
- `404` не раскрывает cross-tenant existence.

## 10. Tests

Каждый paginated list endpoint должен иметь минимум:

- default page/size;
- max size 200;
- invalid page/size -> 400;
- stable deterministic order;
- each important single filter;
- representative combined filters с AND semantics;
- allowed sorting;
- invalid sort -> 400;
- empty result;
- tenant A не видит tenant B;
- wrong nested parent -> 404;
- RBAC read/manage separation;
- no duplicate rows from joins;
- no N+1 regression для representative list path, где это practically testable.

9A дополнительно:

- duplicate tenant/externalId conflict;
- contact validation/ownership;
- customer status/update rules.

9B дополнительно:

- exact decimal amounts;
- outstanding/status calculations;
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
- campaigns/runs/recipients/templates pagination migration.

Persistence/concurrency tests — PostgreSQL/Testcontainers согласно existing test-runtime contract.

## 11. Performance acceptance

Frontend list endpoints должны быть usable на realistic tenant dataset, а не только на десятках fixtures.

Минимальные требования:

- DB-side paging/filtering/sorting;
- no unbounded collection load;
- no obvious N+1;
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
- Page/Slice schema единообразна;
- nullable fields explicit;
- examples не содержат real PII/secrets.

Frontend TypeScript client должен иметь возможность генерироваться/типизироваться из stable API contract без знания persistence model.

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
9D Frontend Support + API normalization
```

9A–9D реализуются отдельными PR от актуального `main`. Documentation/spec branch не использовать как base code branch.

9D зависит от business read models 9A–9C для полноценного dashboard.

## 14. Explicit out-of-scope

Не входит в Slice 9:

- React/frontend implementation;
- SMS/WhatsApp/Telegram/Push provider adapters;
- generic workflow/BPM engine;
- generic reporting/query DSL;
- Elasticsearch/OpenSearch без отдельного performance requirement;
- billing/plans/usage metering;
- 1C/ERP-specific connectors;
- arbitrary cross-tenant admin search;
- FX conversion engine, если его нет в текущем domain;
- hard delete financial/collection history.

## 15. Definition of Done — sub-slice

Каждый 9A/9B/9C/9D считается готовым только если:

1. API/domain requirements соответствующего раздела реализованы.
2. Tenant isolation находится в repository/SQL.
3. Paginated endpoints имеют bounded size, filters, stable sorting.
4. DTO не экспонируют JPA entities/internal secrets.
5. RBAC tests green.
6. PostgreSQL integration tests green.
7. Liquibase migrations включены в master changelog, если schema менялась.
8. Representative query plans/indexes проверены.
9. OpenAPI отражает contract.
10. `mvn verify` green.
11. PR не auto-merge без явного решения.

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

После этого новые frontend requirements могут добавлять endpoint-specific capabilities, но отсутствие базового CRUD/read/list/filter/paging contract не должно блокировать разработку основного UI.
