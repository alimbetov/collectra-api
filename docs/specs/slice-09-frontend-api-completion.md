# Slice 9 — Frontend API Completion

Status: PLANNED  
Depends on: existing identity/customer/receivable/import/template/file/campaign/message APIs and Slice 8  
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

Slice 9 является master-spec и реализуется четырьмя independently reviewable sub-slices:

```text
9A Customer + Contract API
9B Receivables + Payments API
9C Collection API
9D Frontend Support API
```

Внутри 9A рекомендуется два implementation PR, не меняя общий scope Slice 9:

```text
9A-1 Customer + Segment API normalization
9A-2 Contract domain foundation + API
```

После merge 9A–9D `/api/v1` считается frontend contract baseline. Дальнейшие breaking changes требуют явной migration/versioning strategy и automated compatibility check.

---

## 2. Проверенный baseline `main`

Slice 9 MUST NOT повторно строить уже существующие возможности.

В актуальном `main` уже существуют authoritative/runtime building blocks:

```text
Identity/authentication/tenant context
SystemRole
Tenant membership / human JWT security
ServiceClient / service JWT / scopes
Customer
CustomerEmail / CustomerPhone
CustomerSegment / CustomerSegmentMember
Invoice
Payment
PaymentAllocation
ReceivableService
ImportBatch + BusinessRecordPersistenceService
Templates/versioned configuration
FileService
Generated documents
Campaign / CampaignRun / CampaignRecipient
Message delivery API / Slice 8
ApiExceptionHandler + Spring ProblemDetail
application Clock
PostgreSQL/Liquibase/Testcontainers test runtime
```

На момент аудита `contract` и `collection` modules не содержат authoritative business implementation кроме package skeleton. Поэтому:

```text
Customer/Segment  -> normalize existing domain/API
Receivables       -> normalize existing domain/API
Contract          -> create minimal new bounded domain
Collection        -> create minimal new bounded domain
Dashboard         -> read models/projections only
```

До начала каждого sub-slice разработчик MUST повторно сверить актуальный `main`. Если предыдущий PR уже реализовал часть scope, implementation адаптируется к фактическому коду без создания competing model.

### 2.1 Authoritative ownership matrix

| Capability | Authoritative owner | Slice 9 action |
|---|---|---|
| Identity | existing `identity` | reuse |
| Tenant membership / human roles | existing identity/security | reuse |
| Technical integration identity | `ServiceClient` | reuse |
| Customer | `customer.Customer` | normalize API |
| Email/phone contacts | customer domain | normalize API |
| Segment | `CustomerSegment` | normalize API |
| Contract | no current owner | create bounded domain |
| Invoice | `receivable.Invoice` | normalize API |
| Payment | `receivable.Payment` | normalize API |
| Allocation | `PaymentAllocation` | normalize API / commands |
| Financial balance/state | receivable domain | derive only |
| CollectionCase | no current owner | create collection domain |
| PromiseToPay | no current owner | create collection domain |
| Dispute | no current owner | create collection domain |
| CollectionAction | no current owner | create collection domain |
| Import history | `ImportBatch` | normalize read API |
| Generated documents | existing document domain | normalize read API |
| Files | FileService | reuse |
| Campaign | campaign domain | normalize API |
| Message | message domain / Slice 8 | reuse |
| Dashboard | no authoritative owner | read model only |

### 2.2 Domain ownership rule

Slice 9 завершает presentation/query surface и MUST NOT молча перепроектировать domain.

Правила:

- MUST NOT создавать второй authoritative source для уже существующего business fact;
- если aggregate/source of truth уже существует, frontend API использует его через application/query service;
- frontend-specific list/detail/dashboard shape может использовать projection/read model, но projection не становится mutable source of truth;
- новая authoritative table/domain aggregate создаётся только если owner действительно отсутствует;
- ownership нового domain state должен быть явно зафиксирован в PR;
- derived fields (`outstandingAmount`, `paidAmount`, `daysOverdue`, delivery counters и т.п.) не поддерживаются независимо в нескольких aggregates;
- import, campaign eligibility, collection views и frontend receivable API MUST видеть один и тот же financial truth.

Authoritative financial chain:

```text
Invoice
  + Payment
  + PaymentAllocation
       ↓
paidAmount / outstandingAmount / payment status / eligibility
```

Forbidden example:

```text
CollectionCase.outstandingAmount // independently mutable duplicate
```

---

## 3. Общий API contract

### 3.1 Base paths и representations

Tenant-facing API:

```text
/api/v1/...
```

Platform administration:

```text
/admin/api/v1/...
```

Controllers возвращают JSON DTO, не JPA entities.

При необходимости разделять:

```text
<ListItemResponse>
<DetailResponse>
<CreateRequest>
<UpdateRequest>
```

Conventions:

- UUID -> UUID;
- timestamp -> ISO-8601 UTC / `Instant`;
- business date -> `LocalDate` / `YYYY-MM-DD`;
- money -> `BigDecimal` + ISO-4217 currency;
- `float`/`double` для денег запрещены;
- semantics `null`, omitted field и `[]` должны быть стабильными;
- public enum values являются API contract.

### 3.2 Pagination

Любой потенциально большой endpoint MUST быть paginated.

Default:

```text
page=0
size=50
max size=200
```

Validation:

```text
page < 0   -> 400
size <= 0  -> 400
size > 200 -> 400
```

Silent clamp запрещён.

Use `Page` только если frontend реально требует total и `COUNT(*)` приемлем:

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

Use Slice-style response для high-volume/expensive-count lists:

```json
{
  "items": [],
  "page": 0,
  "size": 50,
  "hasNext": true
}
```

`messages` остаётся Slice-style. Import row errors также предпочтительно Slice, если exact count дорогой.

### 3.3 Sorting

Syntax:

```text
sort=createdAt,desc
sort=displayName,asc
```

Каждый endpoint имеет explicit allowlist sortable fields.

Arbitrary entity/property/SQL sorting запрещён.

Default stable order:

```text
createdAt DESC, id DESC
```

Для non-unique user sort repository MUST добавлять unique tie-breaker `id`.

Unknown field/direction -> `400`.

### 3.4 Filtering/search

Использовать fixed typed business filters с AND semantics.

Generic RSQL/OData/query DSL в Slice 9 запрещён.

Для каждого string/search filter определить:

- trim semantics;
- case sensitivity;
- exact/prefix/contains semantics;
- min/max length;
- searchable fields;
- PostgreSQL index/query strategy.

Default human-readable search: trimmed, case-insensitive, bounded search только по explicit fields. `%term%` на больших таблицах без индекса/query-plan justification запрещён.

Invalid UUID/enum/date/decimal/range -> `400`.

### 3.5 Tenant isolation

Tenant id tenant-facing API берётся только из authenticated security context.

Caller-controlled `tenantId` из path/query/body/header не является authorization source.

Tenant predicate MUST находиться в repository/SQL:

```text
tenant_id = :tenantId
```

Forbidden:

```java
repository.findById(id)
// then tenant check in Java
```

Required pattern:

```java
repository.findByIdAndTenantId(id, tenantId)
```

или equivalent tenant-scoped projection/query.

Nested resources проверяют tenant + parent + child в query/application boundary.

Foreign tenant, wrong parent и missing resource externally возвращают одинаковый `404`.

Count query MUST иметь тот же tenant predicate.

### 3.6 Authentication/authorization — reuse current security model

Slice 9 MUST reuse existing identity/security model. Новые `PrincipalType`, новый technical-account aggregate или parallel role hierarchy не создавать.

Conceptual actor mapping:

```text
Platform administrator
  -> existing platform identity
  -> SystemRole.PLATFORM_SUPER_ADMIN

Tenant human user
  -> existing human JWT
  -> ROLE_HUMAN
  -> existing tenant membership / SystemRole.TENANT_ADMIN or TENANT_USER

Technical integration
  -> existing ServiceClient
  -> service JWT
  -> ROLE_SERVICE + existing SCOPE_* authorities
  -> tenant-bound
```

Fine-grained corporate RBAC для каждого screen/resource out-of-scope до подтверждённого requirement.

Machine-to-machine API использует существующую scope convention. Не вводить provider-specific roles вроде `ROLE_1C`/`ROLE_SAP`.

Expected semantics:

```text
unauthenticated                            -> 401
human user own-tenant resource             -> allowed
human user foreign-tenant resource         -> 404
service client + required scope            -> allowed
service client without required scope      -> 403
human tenant user -> /admin/api/v1/**      -> 403
platform super admin -> admin API           -> allowed
```

Обычный `/api/v1/**` не становится cross-tenant API для platform admin автоматически.

### 3.7 HTTP/error semantics

Use existing `ApiExceptionHandler` and Spring `ProblemDetail`. Не создавать второй `ErrorResponse` contract.

Baseline:

```text
200 successful read/update
201 successful create
204 successful command without body
400 invalid request/query
401 unauthenticated
403 authenticated but unauthorized
404 absent / foreign tenant / wrong hierarchy
409 business conflict / illegal transition / duplicate business key
```

Stable error properties должны продолжать существующую модель:

```text
status
title
detail
instance
code
traceId
correlationId
errors       // validation, where applicable
```

Frontend MUST use machine-readable `code`, а не parse exception text.

Stack trace, SQL/provider exception, secrets, PII и internal class names наружу не возвращать.

### 3.8 Time/concurrency/audit

Проект уже имеет application `Clock`; Slice 9 MUST использовать injected `Clock` для time-dependent business/read logic.

Forbidden in production Slice 9 code:

```java
LocalDate.now()
Instant.now()
OffsetDateTime.now()
```

без injected `Clock`.

Особенно это относится к:

```text
overdue
daysOverdue
due today
promise due/broken
next-action overdue
dashboard aging/asOf
```

Mutable financial/workflow aggregates используют optimistic versioning или explicit row locking согласно invariant.

Significant workflow/financial transitions сохраняют actor/time/action/reason audit, где это необходимо.

Hard delete financial/collection history запрещён; использовать archive/status/reversal semantics.

### 3.9 Query/read-model boundary

List/dashboard API SHOULD использовать query projections, dedicated query repositories или explicit JPQL/native queries, а не materialize full aggregate без необходимости.

Forbidden: lazy association traversal per row при построении list DTO.

Read model может дублировать representation, но не authoritative state.

### 3.10 Module dependency rule

Bounded modules не должны напрямую использовать repositories соседнего bounded context как shared database DAO layer.

Например Collection application layer при необходимости финансового контекста использует `ReceivableService`/dedicated receivable query port, а не произвольные вызовы `InvoiceRepository`/`PaymentRepository` из collection package.

Target direction:

```text
REST
  ↓
Application / Query service
  ↓
Domain owner / dedicated query port
  ↓
Repository
  ↓
PostgreSQL
```

---

## 4. Slice 9A — Customer + Contract API

### 4.1 9A-1 Customer API normalization

`Customer` уже authoritative. Slice 9A не создаёт новый customer aggregate.

```text
GET /api/v1/customers
```

MVP filters при наличии соответствующих fields:

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

`search` минимум `displayName` + `externalId`.

Sortable allowlist минимум:

```text
createdAt
updatedAt
displayName
externalId
```

List response не загружает nested contacts/contracts/invoices collections.

### 4.2 Customer detail/write

```text
GET   /api/v1/customers/{customerId}
POST  /api/v1/customers
PATCH /api/v1/customers/{customerId}
```

Использовать existing Customer invariants: tenant-scoped externalId, type, displayName, status, manager, locale/timezone, custom fields.

Status changes проходят через domain/application rule, не arbitrary setter.

Physical DELETE customer out-of-scope.

### 4.3 Customer contacts

Expose existing email/phone domain через frontend contract:

```text
GET    /api/v1/customers/{customerId}/contacts
POST   /api/v1/customers/{customerId}/contacts
PATCH  /api/v1/customers/{customerId}/contacts/{contactId}
DELETE /api/v1/customers/{customerId}/contacts/{contactId}
```

`DELETE` разрешён только если existing domain semantics допускает physical removal; иначе endpoint заменяется deactivate/status command.

Email/phone — PII: raw values не писать в logs/errors.

Если nested contact count bounded business limits, pagination не требуется.

### 4.4 Segments — existing tenant-configurable resource

`CustomerSegment` и `CustomerSegmentMember` уже существуют. Segment MUST NOT заменяться enum/reference-data.

Normalize/create missing resource API:

```text
GET   /api/v1/segments
GET   /api/v1/segments/{segmentId}
POST  /api/v1/segments
PATCH /api/v1/segments/{segmentId}
```

List paginated; минимум `search/status`; stable deterministic sort; membership access tenant-scoped.

### 4.5 9A-2 Contract domain foundation

На момент аудита authoritative Contract отсутствует. Slice 9A-2 создаёт минимальный bounded aggregate, а не frontend-only table.

Minimum model:

```text
Contract
├── id
├── tenantId
├── customerId
├── externalId
├── contractNumber
├── status
├── validFrom
├── validTo
├── renewalDate        // only if business requirement retained
├── customFields
├── version
├── createdAt
└── updatedAt
```

Required invariants:

- `tenantId` mandatory;
- customer MUST belong to same tenant;
- `externalId` tenant-scoped unique;
- contract-number uniqueness semantics MUST be explicitly chosen and tested;
- lifecycle/status bounded by domain methods;
- hard delete forbidden;
- optimistic versioning for conflicting edits;
- document content stored/retrieved through FileService references, not byte arrays in Contract;
- Contract MUST NOT own `paidAmount`, `outstandingAmount`, collection status or delivery counters.

Public API:

```text
GET   /api/v1/contracts
GET   /api/v1/contracts/{contractId}
POST  /api/v1/contracts
PATCH /api/v1/contracts/{contractId}
```

Filters:

```text
customerId
status
contractNumber
externalId
validFrom
validTo
renewalFrom
renewalTo
search
```

Contract DTO returns safe file/document references only.

### 4.6 9A acceptance

Frontend должен реализовать без large client-side filtering/N+1:

```text
Customers table/detail
Customer contacts editor
Segments management/selector
Contracts table/detail/editor
```

9A implementation SHOULD be two reviewable PRs: existing Customer/Segment normalization first, Contract foundation second.

---

## 5. Slice 9B — Receivables + Payments API

### 5.1 Authoritative financial model is already present

Slice 9B MUST reuse:

```text
Invoice
Payment
PaymentAllocation
ReceivableService
```

Imports already persist into this domain and campaign eligibility already consumes it. Slice 9B MUST NOT create replacement Invoice/Receivable/Balance models.

The same financial state MUST drive:

```text
receivable frontend API
campaign eligibility
collection financial projections
dashboard receivable metrics
```

### 5.2 Invoices

```text
GET   /api/v1/invoices
GET   /api/v1/invoices/{invoiceId}
POST  /api/v1/invoices
PATCH /api/v1/invoices/{invoiceId}
```

POST/PATCH are not generic entity editing. Permitted manual corrections MUST be explicitly enumerated. Immutable financial facts use explicit correction/reversal/status commands instead of arbitrary PATCH.

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
overdue
amountMin
amountMax
outstandingMin
outstandingMax
```

Backend-computed authoritative fields:

```text
amount
paidAmount
outstandingAmount
currency
status
dueDate
daysOverdue
```

`daysOverdue` and `overdue` MUST use injected application `Clock`.

Frontend MUST NOT reconstruct balances/status from unrelated records.

### 5.3 Payments

```text
GET  /api/v1/payments
GET  /api/v1/payments/{paymentId}
POST /api/v1/payments
```

Prefer immutable payment + correction/reversal model instead of unrestricted PATCH.

Filters:

```text
customerId
invoiceId       // through allocation relation
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

```text
GET  /api/v1/payments/{paymentId}/allocations
POST /api/v1/payments/{paymentId}/allocations
GET  /api/v1/invoices/{invoiceId}/allocations
POST /api/v1/payments/{paymentId}/allocations/{allocationId}/reverse
```

Mandatory invariants:

- amount > 0;
- payment/invoice/allocation same tenant;
- no payment over-allocation;
- no invoice over-payment unless explicit credit rule exists;
- no implicit FX conversion;
- customer consistency where payment has customer ownership;
- allocation and derived states update in one PostgreSQL transaction;
- concurrent allocations cannot violate totals;
- retry/duplicate request cannot silently double-allocate;
- reversal audit retained.

### 5.5 9B acceptance

Frontend supports:

```text
Receivables table/detail
Overdue filtering
Customer receivables
Payments table/detail
Allocation/reconciliation UI
```

with one authoritative financial model.

---

## 6. Slice 9C — Collection bounded domain + API

### 6.1 Domain ownership

Current `collection` module has no authoritative implementation. Slice 9C creates only the minimum domain required for operator UI:

```text
CollectionCase
PromiseToPay
Dispute
CollectionAction
Collection timeline projection
```

Explicitly out-of-scope here:

```text
generic BPM/workflow engine
automatic case-opening scheduler
automatic escalation engine
rules engine
automatic assignment
automatic PromiseToPay breach scheduler
```

Those can be later slices after operator workflow is validated.

Collection owns workflow state. Receivable owns financial truth.

```text
CollectionCase ──references──> Invoice / Customer
CollectionCase does NOT own invoice balance
```

### 6.2 CollectionCase

MVP case is invoice-level. `invoiceId` MUST NOT be null in Slice 9C unless a separate customer-level collection requirement is explicitly approved.

Minimum fields:

```text
id
tenantId
customerId
invoiceId
status
priority
assignedTo
openedAt
closedAt
nextActionAt
version
audit fields
```

Invariant:

```text
At most one active CollectionCase per tenant + invoice.
```

Historical closed cases may remain.

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

Status is a bounded state machine; arbitrary `status=X` is forbidden.

Financial values in case detail/list are query-time projections from Receivable, not mutable Collection fields.

### 6.3 Promise-to-Pay

```text
GET   /api/v1/promises-to-pay
GET   /api/v1/promises-to-pay/{promiseId}
POST  /api/v1/promises-to-pay
PATCH /api/v1/promises-to-pay/{promiseId}
```

Minimum model:

```text
caseId
promisedAmount
currency
promisedDate
status
fulfilledAt / brokenAt / cancelledAt as applicable
audit fields
```

Lifecycle distinguishes active/fulfilled/broken/cancelled. History is never physically deleted.

Filters:

```text
customerId
caseId
status
promiseFrom
promiseTo
```

Time-dependent due/breach representation uses injected `Clock`.

### 6.4 Disputes

```text
GET   /api/v1/disputes
GET   /api/v1/disputes/{disputeId}
POST  /api/v1/disputes
PATCH /api/v1/disputes/{disputeId}
```

Filters:

```text
customerId
caseId
status
category
createdFrom
createdTo
```

Resolution retains status, reason/code/summary, actor and resolved timestamp. Raw unbounded internal notes MUST NOT automatically become public/log payload.

### 6.5 Collection actions / work queue

Resource API:

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
overdue
```

Default queue sort:

```text
dueAt ASC, priority DESC, id ASC
```

Overdue semantics use injected `Clock`.

### 6.6 Unified timeline

```text
GET /api/v1/collection-cases/{caseId}/timeline
```

Paginated projection aggregates state transitions/promises/disputes/actions into stable chronology. Frontend MUST NOT reconstruct chronology by downloading multiple independent collections.

### 6.7 Collection invariants

- no hard delete of workflow history;
- tenant-scoped parent/child access;
- validated domain transitions;
- decimal + currency for financial promises;
- optimistic versioning/locking for conflicting edits;
- actor/time/reason audit for significant transitions;
- notification delivery failure cannot erase Collection state;
- no direct dependency on HTTP/provider adapters;
- no duplicated invoice/payment balances;
- Collection module does not reach directly into receivable repositories; use application/query port.

### 6.8 9C acceptance

Frontend supports:

```text
Collection work queue
Case detail/timeline
Promise-to-Pay management
Dispute management
Next action scheduling/completion
```

---

## 7. Slice 9D — Frontend Support API

### 7.1 Dashboard read models

Avoid frontend fan-out over many list endpoints.

```text
GET /api/v1/dashboard/summary
GET /api/v1/dashboard/receivables
GET /api/v1/dashboard/delivery
GET /api/v1/dashboard/collections
```

Every time-dependent dashboard response SHOULD expose:

```text
asOf: Instant
```

Receivables projection:

```text
outstanding totals
overdue totals
due today/soon
aging buckets
affected customers
```

Different currencies MUST NOT be summed into one number without authoritative FX/base-currency model. Until such model exists, group totals by currency.

Delivery dashboard uses existing persisted/cached delivery counters/metric semantics, not expensive full Message-table recalculation per refresh.

Collection dashboard includes open cases, overdue actions, promises due/broken, disputes open.

Dashboard is a read model only.

### 7.2 Reference data

Expose static/compile-time code lists through a consistent API, for example:

```text
GET /api/v1/reference-data
```

or bounded resources:

```text
GET /api/v1/reference-data/channels
GET /api/v1/reference-data/currencies
GET /api/v1/reference-data/locales
GET /api/v1/reference-data/statuses
```

Tenant-configurable `CustomerSegment` MUST remain a resource API and MUST NOT be represented as static reference data.

### 7.3 Import history

Reuse `ImportBatch` as source of truth.

```text
GET /api/v1/imports
GET /api/v1/imports/{importId}
GET /api/v1/imports/{importId}/errors
```

Filters:

```text
type/definition/schema as applicable
status
filename/search
createdFrom
createdTo
```

Import list paginated. Error rows Page/Slice without unbounded raw payload/PII.

### 7.4 Generated documents

Normalize read API:

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

Return metadata/status/safe error code/file reference. Binary download remains FileService responsibility.

### 7.5 Campaigns/runs/recipients

Normalize existing unbounded list endpoints.

`GET /api/v1/campaigns` supports:

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

Campaign runs are paginated.

Recipients MUST be paginated/high-volume aware.

Preferred hierarchy:

```text
GET /api/v1/campaigns/{campaignId}/runs/{runId}/recipients
```

with tenant + campaign + run verification.

Legacy route may remain temporarily before API freeze but two divergent contracts MUST NOT survive 9D.

Message list from Slice 8 remains Slice-style unless UI proves exact totals are required.

### 7.6 Templates/versions

Existing template/version lists must be checked for unbounded collections.

Minimum template filters:

```text
page/size
search
channel
status
locale
createdFrom
createdTo
sort
```

Version history paginated when unbounded.

### 7.7 API freeze/OpenAPI compatibility boundary

Before 9D completion deliberate breaking cleanup inside `/api/v1` is allowed because frontend baseline is not frozen.

At 9D completion:

```text
generate/store OpenAPI baseline
activate CI compatibility check
```

After freeze CI MUST reject undeclared representative breaking changes including:

```text
removed endpoint
removed/renamed response field
field type change
optional -> required
removed/renamed enum value
List -> Page/Slice contract change
path restructuring
incompatible request schema
incompatible status/request semantics where detectable
```

Use existing OpenAPI tooling/compatible checker; do not build a custom compatibility framework unless required.

---

## 8. Database/query/index requirements

No new list endpoint may use `findAll()` + Java filtering.

Push tenant/filter/sort/page into PostgreSQL.

Representative list paths SHOULD use projection/query DTO.

Typical index candidates, justified by actual query patterns:

```text
(tenant_id, created_at DESC, id DESC)
(tenant_id, status, created_at DESC, id DESC)
(tenant_id, customer_id, created_at DESC, id DESC)
(tenant_id, due_date, id)
(tenant_id, assigned_to, status, due_at, id)
```

Do not mechanically index every filter combination.

Each sub-slice PR must provide representative `EXPLAIN ANALYZE` evidence for:

```text
default list
common combined filter
one high-volume path
```

Acceptance: representative list DTO building performs no lazy-per-row association traversal.

---

## 9. Security/privacy/observability

- tenant restriction in DB query;
- tenant from authenticated principal/security context;
- reuse existing `SystemRole`, human/service authorities and ServiceClient scopes;
- no raw email/phone in logs/error messages;
- no auth tokens/password hashes/raw provider response in responses/logs;
- custom fields cannot bypass privacy rules;
- `404` hides cross-tenant existence;
- reuse existing trace/correlation context in logs and ProblemDetail;
- log endpoint/operation/status/latency and safe tenant context;
- do not log raw request/response business payload by default;
- payment/custom-field/contact payload follows redaction/masking policy;
- operational metric labels must be bounded cardinality; no customerId/invoiceId/email/raw tenant id where cardinality is unsafe.

Do not introduce a parallel `requestId` convention when existing `traceId`/`correlationId` already provide request correlation unless a separate project-wide requirement is approved.

---

## 10. Tests

Every paginated list endpoint includes at least:

- default page/size;
- max size 200;
- invalid page/size -> 400;
- deterministic order with unique tie-breaker;
- important individual filters;
- representative AND combined filters;
- documented search semantics;
- allowed/invalid sorting;
- empty result;
- tenant A cannot see tenant B;
- wrong nested parent -> 404;
- unauthenticated -> 401;
- human own-tenant access;
- service-scope positive/negative where endpoint supports M2M;
- tenant user -> admin API = 403;
- no duplicate rows from joins;
- representative no-N+1 check;
- existing ProblemDetail shape/code/traceId/correlationId for representative 400/403/404/409.

9A additionally:

```text
Customer externalId uniqueness
contact ownership/validation
segment tenant isolation
Contract tenant/customer ownership
Contract externalId uniqueness
Contract number uniqueness semantics
Contract lifecycle/version conflict
```

9B additionally:

```text
exact decimal amounts
paid/outstanding/status from authoritative model
Clock-based overdue calculations
partial allocation
over-allocation rejection
currency/customer mismatch
concurrent allocation
reversal
transaction rollback atomicity
```

9C additionally:

```text
one active case per tenant+invoice
valid/invalid state transitions
optimistic concurrent update
promise fulfillment/breach/cancel
Clock-based due/breach behavior
dispute resolution
overdue action filter
timeline stable chronology
no duplicated financial state
```

9D additionally:

```text
dashboard currency grouping
dashboard asOf
aggregate tenant isolation
import errors paging/redaction
generated-document authorization
campaign/run/recipient/template pagination
OpenAPI compatibility check catches representative breaking change
```

Persistence/concurrency tests use PostgreSQL/Testcontainers according to existing test-runtime contract.

---

## 11. Performance acceptance

Minimum:

```text
DB-side pagination/filter/sort
projection for representative list paths
no unbounded collection load
no lazy-per-row N+1
indexes backed by query plans
bounded dashboard query count
no expensive COUNT when UI does not need totals
server-side max page size
```

Do not introduce Elasticsearch/OpenSearch in Slice 9 without measured need.

---

## 12. OpenAPI/frontend contract

All public Slice 9 endpoints MUST be present in generated OpenAPI.

Before `Frontend API Ready` verify:

```text
query params documented
enum values visible
response/request DTO documented
400/401/403/404/409 consistent
existing ProblemDetail schema documented
Page/Slice schema consistent
nullable fields explicit
money/date/time conventions consistent
examples contain no real PII/secrets
```

Frontend TypeScript client must be generatable/typeable without persistence knowledge.

After 9D the generated OpenAPI baseline is compatibility boundary.

---

## 13. Implementation order

Recommended:

```text
9A-1 Customer + Segment normalization
        ↓
9A-2 Contract foundation + API
        ↓
9B Receivables + Payments normalization
        ↓
9C Collection foundation + API
        ↓
9D Frontend support + normalization + API freeze
```

9A–9D are separate PRs from current `main`. Documentation/spec branch MUST NOT be used as implementation base branch.

9D depends on 9A–9C read models for complete dashboard.

---

## 14. Explicit out-of-scope

- React/frontend implementation;
- new IAM/ABAC framework;
- replacement identity/security model;
- duplicate PrincipalType/technical-account model;
- fine-grained business RBAC without confirmed requirement;
- SMS/WhatsApp/Telegram/Push provider adapters;
- BPM/workflow engine;
- automatic collection escalation/assignment/rules engine;
- automatic collection case scheduler;
- generic reporting/query DSL;
- Elasticsearch/OpenSearch without measured requirement;
- billing/plans/usage metering;
- 1C/ERP-specific connectors;
- provider-specific technical roles;
- arbitrary cross-tenant admin search;
- FX conversion engine if no authoritative FX model exists;
- customer-level collection case in MVP unless explicitly approved;
- hard delete financial/collection history.

---

## 15. Definition of Done — sub-slice

Each 9A/9B/9C/9D is complete only when:

1. Scope is implemented against current `main`, not an obsolete spec assumption.
2. Domain ownership is verified and no competing authoritative model is introduced.
3. Tenant isolation is enforced in repository/SQL.
4. Pagination/filter/sort are bounded and deterministic.
5. DTOs expose no JPA/internal secret model.
6. Existing identity/security model is reused; applicable security tests are green.
7. Existing ProblemDetail/correlation conventions are reused.
8. Existing application `Clock` is used for time-dependent behavior.
9. PostgreSQL/Testcontainers integration tests are green.
10. Liquibase migration is in master changelog when schema changed.
11. Representative query plans/indexes are checked; no lazy-per-row N+1.
12. OpenAPI reflects contract.
13. `mvn verify` is green.
14. PR is not auto-merged without explicit decision.

---

## 16. Definition of Done — Frontend API Ready

Collectra backend becomes **Frontend API Ready** only after 9A–9D are merged and frontend can implement without backend workarounds:

```text
Login/session
Customers
Customer detail + contacts
Segments
Contracts
Receivables/invoices
Payments
Payment allocation/reconciliation
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

Every data-grid screen has server-side pagination, business filters and deterministic sorting.

Final readiness gates:

```text
tenant isolation verified
existing SystemRole/human/service security model verified
ServiceClient scopes verified where M2M exposed
one authoritative financial model verified
Contract ownership established
Collection ownership established
existing ProblemDetail contract stable
existing Clock used for time-dependent state
representative list endpoints free of N+1
OpenAPI breaking-change CI gate enabled
```

After this point new frontend requirements may add endpoint-specific capabilities, but the main UI must not be blocked by missing basic CRUD/read/list/filter/paging contracts.
