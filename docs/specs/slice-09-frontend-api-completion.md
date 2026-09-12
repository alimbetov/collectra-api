# Slice 9 — Frontend API Completion

Status: READY FOR IMPLEMENTATION  
Depends on: existing identity/customer/receivable/import/template/file/campaign/message APIs and Slice 8  
Suggested implementation branches: `feat/slice-09a1-customer-segment-api`, `feat/slice-09a2-contract-domain-api`, `feat/slice-09b-receivables-payments-api`, `feat/slice-09c-collection-api`, `feat/slice-09d-frontend-support-api`

## 1. Цель

Довести backend Collectra до состояния **Frontend API Ready**: React/admin frontend должен иметь стабильный tenant-scoped REST API для основных пользовательских экранов и не должен:

- обращаться напрямую к PostgreSQL;
- самостоятельно вычислять authoritative business state;
- загружать неограниченные `List<>` и фильтровать их в браузере;
- собирать dashboard через неконтролируемый network fan-out;
- знать JPA entity structure;
- обходить tenant/security constraints;
- реализовывать собственный generic query language поверх API.

Slice 9 — master-spec и реализуется отдельными reviewable PR:

```text
9A-1 Customer + Segment API normalization
9A-2 Contract domain foundation + API
9B   Receivables + Payments API normalization
9C   Collection bounded domain + API
9D   Frontend Support API + API freeze
```

После merge 9A-1/9A-2/9B/9C/9D `/api/v1` считается frontend contract baseline. Дальнейшие breaking changes требуют migration/versioning strategy и automated OpenAPI compatibility check.

---

## 2. Проверенный baseline `main`

Slice 9 MUST NOT повторно строить уже существующие возможности.

В `main` уже существуют:

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

На момент финального аудита `contract` и `collection` modules не содержат authoritative business implementation кроме package skeleton.

Поэтому:

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
| Allocation | `PaymentAllocation` | harden + normalize API/commands |
| Financial balance/state | receivable domain | existing persisted authoritative state, maintained only by Receivable |
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
- persisted `Invoice.paidAmount`, `Invoice.outstandingAmount` и `Invoice.paymentStatus` остаются authoritative state Receivable domain и изменяются только через его invariants;
- `daysOverdue`, dashboard counters и другие presentation values могут вычисляться как projections, но не становятся competing state;
- import, campaign eligibility, collection views и frontend receivable API MUST видеть один и тот же financial truth.

Authoritative financial chain:

```text
PaymentAllocation command
        ↓
Receivable transaction
        ↓
Invoice.paidAmount
Invoice.outstandingAmount
Invoice.paymentStatus
        ↓
Campaign / Collection projection / Dashboard / Frontend
```

Forbidden:

```text
CollectionCase.outstandingAmount // independently mutable duplicate
DashboardInvoiceBalance          // independently mutable duplicate without explicit consistency model
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
<CommandRequest>
```

Conventions:

- UUID -> UUID;
- timestamp -> ISO-8601 UTC / `Instant`;
- business date -> `LocalDate` / `YYYY-MM-DD`;
- money -> `BigDecimal` + ISO-4217 currency;
- `float`/`double` для денег запрещены;
- `null`, omitted field и `[]` имеют стабильную documented semantics;
- public enum values являются API contract;
- generic workflow transitions через arbitrary `PATCH status=X` запрещены, если transition имеет business significance.

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

`Page` использовать только если frontend реально требует total и `COUNT(*)` приемлем:

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

Slice-style response использовать для high-volume/expensive-count lists:

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

Каждый endpoint имеет explicit allowlist sortable fields. Arbitrary entity/property/SQL sorting запрещён.

Default stable order:

```text
createdAt DESC, id DESC
```

Для non-unique user sort repository MUST добавлять unique tie-breaker `id`. Tie-breaker direction должен быть детерминирован и согласован с основным порядком; конкретный endpoint фиксирует его в contract.

Unknown field/direction -> `400 INVALID_REQUEST`.

### 3.4 Filtering/search

Использовать fixed typed business filters с AND semantics. Generic RSQL/OData/query DSL запрещён.

Общие query semantics:

```text
?search=                 -> treat as absent after trim
?status=                 -> 400
?customerId=             -> 400
from > to                -> 400 INVALID_RANGE
amountMin > amountMax    -> 400 INVALID_RANGE
```

Для каждого string/search filter определить exact/prefix/contains semantics и searchable fields.

Default endpoint semantics:

```text
Customer.search:
  displayName    -> case-insensitive contains
  externalId     -> case-insensitive prefix

Contract.search:
  contractNumber -> case-insensitive prefix
  externalId     -> case-insensitive exact/prefix

Invoice.search:
  invoiceNumber  -> case-insensitive prefix
  externalId     -> case-insensitive exact/prefix

Payment.search:
  paymentReference -> case-insensitive contains
  externalId       -> case-insensitive exact/prefix
```

Search input MUST be trimmed and length-bounded. `%term%` на больших таблицах без подходящего PostgreSQL index/query-plan justification запрещён.

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

Required:

```java
repository.findByIdAndTenantId(id, tenantId)
```

или equivalent tenant-scoped projection/query.

Nested resources проверяют tenant + parent + child. Foreign tenant, wrong parent и missing resource externally возвращают одинаковый `404`.

Count query MUST иметь тот же tenant predicate.

### 3.6 Authentication/authorization — reuse current security model

Slice 9 MUST reuse existing identity/security model. Новые `PrincipalType`, новый technical-account aggregate или parallel role hierarchy не создавать.

Conceptual mapping:

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

Stable properties:

```text
status
title
detail
instance
code
traceId
correlationId
errors      // validation when applicable
```

Baseline:

```text
200 successful read/update
201 successful create
204 successful idempotent command without body
400 invalid request/query
401 unauthenticated
403 authenticated but unauthorized
404 absent / foreign tenant / wrong hierarchy
409 business conflict / illegal transition / duplicate business key / optimistic conflict
```

Минимальный стабильный catalog business codes для Slice 9:

```text
VALIDATION_FAILED
INVALID_REQUEST
INVALID_RANGE
NOT_FOUND
FORBIDDEN
DUPLICATE_EXTERNAL_ID
VERSION_CONFLICT
INVALID_STATE_TRANSITION
CURRENCY_MISMATCH
CUSTOMER_MISMATCH
ALLOCATION_EXCEEDS_PAYMENT
ALLOCATION_EXCEEDS_INVOICE
ALLOCATION_ALREADY_REVERSED
IDEMPOTENCY_CONFLICT
COLLECTION_CASE_ALREADY_ACTIVE
```

Frontend MUST использовать `code`, а не parse exception text.

Stack trace, SQL/provider exception, secrets, PII и internal class names наружу не возвращать.

### 3.8 Time/business date/concurrency/audit

Проект уже имеет application `Clock`; Slice 9 MUST использовать injected `Clock` для time-dependent logic.

Forbidden in production Slice 9 code:

```java
LocalDate.now()
Instant.now()
OffsetDateTime.now()
```

без injected `Clock`.

`Clock` является authoritative instant source. Для business `LocalDate` нельзя случайно использовать JVM default timezone.

Business date resolution:

```text
1. если tenant timezone уже доступен из authoritative tenant/config model -> использовать его ZoneId;
2. если такого model ещё нет -> использовать один явно configured project-wide business ZoneId;
3. fallback к JVM default timezone запрещён.
```

До появления tenant-specific timezone default для Collectra MVP должен быть явно задан application property, рекомендуемо `Asia/Almaty`, и использован единообразно.

Time-dependent semantics:

```text
overdue invoice:
  paymentStatus not PAID/CANCELLED
  AND outstandingAmount > 0
  AND dueDate < businessDate

daysOverdue:
  overdue ? DAYS.between(dueDate, businessDate) : 0

collection action overdue:
  status == PENDING
  AND dueAt < now(clock)
```

Mutable financial/workflow aggregates используют optimistic versioning или explicit row locking согласно конкретным invariants ниже.

Для API optimistic locking mutable workflow DTO MUST передавать `version`. Stale version -> `409 VERSION_CONFLICT`.

Significant workflow/financial transitions сохраняют actor/time/action/reason audit.

Hard delete financial/collection history запрещён; использовать archive/status/reversal semantics.

### 3.9 Query/read-model boundary

List/dashboard API SHOULD использовать query projections, dedicated query repositories или explicit JPQL/native queries, а не materialize full aggregate без необходимости.

Forbidden: lazy association traversal per row при построении list DTO.

Read model может дублировать representation, но не authoritative state.

### 3.10 Module dependency rule

Bounded modules не используют repositories соседнего bounded context как shared DAO layer.

Например Collection application layer использует `ReceivableService`/dedicated receivable query port, а не `InvoiceRepository`/`PaymentRepository` напрямую.

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

## 4. Slice 9A-1 — Customer + Segment API normalization

### 4.1 Customer list

`Customer` уже authoritative. Новый customer aggregate запрещён.

```text
GET /api/v1/customers
```

MVP filters:

```text
search
status
customerType
managerId
segmentId
externalId
email
phone
createdFrom
createdTo
```

Canonical semantics:

- `externalId` exact match для dedicated filter;
- `email` normalized case-insensitive exact match;
- `phone` normalized exact match по существующей normalized phone semantics;
- `segmentId` означает membership в tenant-owned `CustomerSegment`;
- filters combine with AND.

Sortable allowlist минимум:

```text
createdAt
updatedAt
displayName
externalId
```

List response MUST NOT загружать nested contacts/contracts/invoices collections.

### 4.2 Customer detail/write

Сохраняем существующий command shape вместо нового generic PATCH:

```text
GET   /api/v1/customers/{customerId}
POST  /api/v1/customers
PUT   /api/v1/customers/{customerId}
PATCH /api/v1/customers/{customerId}/status
```

`PUT` обновляет profile fields существующего Customer и не меняет `externalId`/tenant identity.

Status change проходит через domain/application method.

Physical DELETE customer out-of-scope.

### 4.3 Customer contacts

Не создавать generic Contact aggregate. Existing `CustomerEmail` и `CustomerPhone` остаются отдельными resources.

Canonical API:

```text
GET   /api/v1/customers/{customerId}/emails
POST  /api/v1/customers/{customerId}/emails
PATCH /api/v1/customers/{customerId}/emails/{emailId}

GET   /api/v1/customers/{customerId}/phones
POST  /api/v1/customers/{customerId}/phones
PATCH /api/v1/customers/{customerId}/phones/{phoneId}
```

В MVP email/phone value после создания не редактируется. Для смены значения:

```text
deactivate old contact
create new contact
```

PATCH разрешает только bounded metadata/state, если соответствующие domain methods добавлены:

```text
type
primary
status ACTIVE/INACTIVE
```

`verified` не выставляется произвольным frontend PATCH, если нет отдельного verification flow.

Contact invariants:

```text
at most one ACTIVE primary email per customer
at most one ACTIVE primary phone per customer
```

При назначении нового primary предыдущий ACTIVE primary того же типа atomically demoted в одной transaction.

Email/phone — PII: raw values не писать в application logs/errors.

Nested contact lists bounded и не требуют pagination.

### 4.4 Customer segments

`CustomerSegment`/`CustomerSegmentMember` уже tenant-configurable resources.

Canonical path сохраняет существующее и более точное имя:

```text
GET   /api/v1/customer-segments
GET   /api/v1/customer-segments/{segmentId}
POST  /api/v1/customer-segments
PATCH /api/v1/customer-segments/{segmentId}
```

List paginated; filters `search`, `active`; stable deterministic sort.

Membership commands сохраняются:

```text
POST   /api/v1/customers/{customerId}/segments/{segmentId}
DELETE /api/v1/customers/{customerId}/segments/{segmentId}
```

Semantics:

```text
add existing membership    -> idempotent 204
remove absent membership   -> idempotent 204
foreign tenant segment     -> 404
```

Segment MUST NOT дублироваться как static reference data.

### 4.5 9A-1 acceptance

Frontend способен реализовать:

```text
Customers table/detail
Customer profile editor
Emails/phones management
Segments management/selector
Segment membership
```

без unbounded list/N+1/client-side large filtering.

---

## 5. Slice 9A-2 — Contract bounded domain + API

### 5.1 Contract model

На момент аудита authoritative Contract отсутствует.

Minimum aggregate:

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
├── renewalDate      // nullable, only informational in MVP
├── customFields
├── version
├── createdAt
└── updatedAt
```

ContractStatus:

```text
ACTIVE
SUSPENDED
CLOSED
CANCELLED
```

Allowed transitions:

```text
ACTIVE    -> SUSPENDED | CLOSED | CANCELLED
SUSPENDED -> ACTIVE | CLOSED | CANCELLED
CLOSED    -> terminal
CANCELLED -> terminal
```

No `DRAFT` in MVP without explicit draft workflow requirement.

Invariants:

- `tenantId` mandatory;
- customer MUST exist in same tenant;
- `externalId` -> unique `(tenant_id, external_id)`;
- `contractNumber` is NOT globally/tenant unique by platform invariant; index it for search, but external system uniqueness remains integration/business rule;
- `validTo`, if present, MUST be >= `validFrom`;
- `renewalDate`, if present, is informational only in Slice 9 and does not trigger scheduler/workflow;
- status transitions domain-controlled;
- optimistic versioning;
- hard delete forbidden;
- documents use FileService references;
- Contract MUST NOT own financial balances, collection state or delivery counters.

### 5.2 Contract API

```text
GET   /api/v1/contracts
GET   /api/v1/contracts/{contractId}
POST  /api/v1/contracts
PUT   /api/v1/contracts/{contractId}
POST  /api/v1/contracts/{contractId}/suspend
POST  /api/v1/contracts/{contractId}/activate
POST  /api/v1/contracts/{contractId}/close
POST  /api/v1/contracts/{contractId}/cancel
```

State commands MUST validate transition and version where concurrent edits matter.

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

Contract DTO returns file/document references only, not binary content.

### 5.3 Invoice relation integrity after Contract introduction

If `Invoice.contractId != null`:

```text
Contract MUST exist
Contract.tenantId == Invoice.tenantId
Contract.customerId == Invoice.customerId
```

Creating/importing invoice with inconsistent contract -> `409 CUSTOMER_MISMATCH` or stable domain-specific conflict code.

### 5.4 9A-2 acceptance

Frontend supports Contract table/detail/editor and lifecycle commands. Contract ownership/integrity is tenant-safe and does not duplicate receivable state.

---

## 6. Slice 9B — Receivables + Payments API

### 6.1 Existing authoritative model

Reuse:

```text
Invoice
Payment
PaymentAllocation
ReceivableService
```

Imports already persist into this domain and campaign eligibility already consumes it.

The same financial state drives:

```text
receivable frontend API
campaign eligibility
collection financial projections
dashboard receivable metrics
```

### 6.2 Invoice API

```text
GET  /api/v1/invoices
GET  /api/v1/invoices/{invoiceId}
POST /api/v1/invoices
```

Generic `PATCH /invoices/{id}` MUST NOT be added in Slice 9. Existing financial facts are not arbitrary-edit resources.

Any future correction of original amount/customer/currency requires explicit correction/reversal business command in a separate approved requirement.

Filters:

```text
customerId
contractId
paymentStatus
currency
invoiceNumber
externalId
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

`paymentStatus` uses existing enum semantics and MUST NOT be renamed to ambiguous generic `status`.

Backend fields:

```text
originalAmount
paidAmount
outstandingAmount
currency
paymentStatus
dueDate
overdue
daysOverdue
```

`overdue` and `daysOverdue` follow section 3.8 business-date semantics.

### 6.3 Payment API

`Payment` currently has no lifecycle status. Slice 9 MUST NOT invent one only for filtering.

```text
GET  /api/v1/payments
GET  /api/v1/payments/{paymentId}
POST /api/v1/payments
```

Filters:

```text
customerId
invoiceId          // through ACTIVE allocation relation
currency
paymentReference
externalId
paymentFrom
paymentTo
amountMin
amountMax
unallocatedOnly
search
```

`status` filter is explicitly NOT part of Slice 9B.

Payment financial facts are immutable in Slice 9. Correction/reversal of payment itself is out-of-scope unless separately specified.

### 6.4 PaymentAllocation hardened model

Slice 9B extends existing allocation persistence only as required for safe reversal/idempotency.

Required fields/concepts:

```text
id
tenantId
paymentId
invoiceId
amount
commandId
status ACTIVE | REVERSED
reversedAt
reversedBy
reversalReason
createdAt
```

DB constraints:

```text
UNIQUE (tenant_id, command_id)
```

Allocation command:

```text
POST /api/v1/payments/{paymentId}/allocations
```

Request:

```json
{
  "commandId": "uuid",
  "invoiceId": "uuid",
  "amount": 10000.0000
}
```

Idempotency semantics:

```text
same commandId + same canonical payload -> return existing allocation/result, no duplicate state change
same commandId + different payload      -> 409 IDEMPOTENCY_CONFLICT
```

### 6.5 Allocation transaction/concurrency contract

All allocate/reverse code paths MUST use one lock order:

```text
Payment -> Invoice
```

Allocation transaction:

```text
1. lock Payment row FOR UPDATE;
2. lock Invoice row FOR UPDATE;
3. verify same tenant/customer/currency;
4. recompute ACTIVE allocated amount for Payment under lock;
5. validate payment remaining;
6. validate invoice outstanding;
7. apply allocation to Invoice authoritative state;
8. persist ACTIVE PaymentAllocation with commandId;
9. commit.
```

Mandatory invariants:

- amount > 0;
- same tenant;
- same customer;
- same currency;
- no payment over-allocation;
- no invoice over-payment;
- no implicit FX conversion;
- no duplicate command effect;
- Invoice state + allocation persisted atomically.

Conflict codes:

```text
CUSTOMER_MISMATCH
CURRENCY_MISMATCH
ALLOCATION_EXCEEDS_PAYMENT
ALLOCATION_EXCEEDS_INVOICE
```

### 6.6 Allocation reversal

```text
GET  /api/v1/payments/{paymentId}/allocations
GET  /api/v1/invoices/{invoiceId}/allocations
POST /api/v1/payments/{paymentId}/allocations/{allocationId}/reverse
```

Reverse request:

```json
{
  "version": 1,
  "reason": "OPERATOR_CORRECTION"
}
```

Reversal transaction follows same lock order `Payment -> Invoice` and atomically:

```text
ACTIVE allocation -> REVERSED
Invoice.paidAmount        -= allocation.amount
Invoice.outstandingAmount += allocation.amount
Invoice.paymentStatus      = recalculated from authoritative totals
reversedAt/by/reason       = stored
```

Reversing already REVERSED allocation -> `409 ALLOCATION_ALREADY_REVERSED`.

Hard delete allocation forbidden.

### 6.7 9B acceptance

Frontend supports:

```text
Receivables table/detail
Overdue filtering
Customer receivables
Payments table/detail
Allocation/reconciliation
Allocation reversal
```

Concurrency tests MUST prove that parallel allocations/reversals cannot violate payment or invoice totals.

---

## 7. Slice 9C — Collection bounded domain + API

### 7.1 Domain ownership

Slice 9C creates minimum operator workflow domain:

```text
CollectionCase
PromiseToPay
Dispute
CollectionAction
Collection timeline projection
```

Out-of-scope:

```text
generic BPM/workflow engine
automatic case-opening scheduler
automatic escalation engine
rules engine
automatic assignment
automatic PromiseToPay breach scheduler
```

Collection owns workflow state. Receivable owns financial truth.

### 7.2 CollectionCase

MVP case is invoice-level. `invoiceId` MUST NOT be null.

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
closeReason
version
audit fields
```

CollectionCaseStatus:

```text
OPEN
IN_PROGRESS
ON_HOLD
CLOSED
```

Allowed transitions:

```text
OPEN        -> IN_PROGRESS | ON_HOLD | CLOSED
IN_PROGRESS -> ON_HOLD | CLOSED
ON_HOLD     -> IN_PROGRESS | CLOSED
CLOSED      -> terminal in Slice 9
```

One active case invariant:

```text
At most one case with status OPEN/IN_PROGRESS/ON_HOLD per tenant + invoice.
```

Creating a case requires:

```text
Invoice exists in same tenant
Invoice.customerId == request.customerId
Invoice.outstandingAmount > 0
Invoice.paymentStatus not PAID/CANCELLED
no existing active case
```

Existing active case -> `409 COLLECTION_CASE_ALREADY_ACTIVE`.

Public API:

```text
GET  /api/v1/collection-cases
GET  /api/v1/collection-cases/{caseId}
POST /api/v1/collection-cases
PUT  /api/v1/collection-cases/{caseId}
POST /api/v1/collection-cases/{caseId}/start
POST /api/v1/collection-cases/{caseId}/hold
POST /api/v1/collection-cases/{caseId}/close
```

`PUT` may edit non-state metadata such as priority/assignedTo with optimistic `version`; state transitions use commands.

Close request:

```json
{
  "version": 3,
  "reason": "PAID"
}
```

CloseReason values:

```text
PAID
SETTLED
WRITTEN_OFF
DUPLICATE
CANCELLED
OTHER
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
createdFrom
createdTo
```

Financial values in case DTO are query-time projections from Receivable and are never mutable Collection fields.

### 7.3 Promise-to-Pay

Minimum fields:

```text
id
tenantId
caseId
promisedAmount
currency
promisedDate
status
fulfilledAt
brokenAt
cancelledAt
version
audit fields
```

PromiseStatus:

```text
ACTIVE
FULFILLED
BROKEN
CANCELLED
```

Allowed transitions:

```text
ACTIVE -> FULFILLED | BROKEN | CANCELLED
FULFILLED/BROKEN/CANCELLED -> terminal
```

Creation invariants:

- case active and same tenant;
- promisedAmount > 0;
- currency matches invoice currency in MVP;
- promisedAmount SHOULD NOT exceed current invoice outstanding amount; if business later allows negotiated excess, that is a separate requirement.

API:

```text
GET  /api/v1/promises-to-pay
GET  /api/v1/promises-to-pay/{promiseId}
POST /api/v1/promises-to-pay
PUT  /api/v1/promises-to-pay/{promiseId}
POST /api/v1/promises-to-pay/{promiseId}/fulfill
POST /api/v1/promises-to-pay/{promiseId}/break
POST /api/v1/promises-to-pay/{promiseId}/cancel
```

`PUT` edits only allowed non-terminal promise metadata and uses `version`.

Important Slice 9 rule: passing `promisedDate` does NOT automatically persist `BROKEN`, because automatic breach scheduler is out-of-scope.

Read DTO may expose:

```text
overdue = status == ACTIVE && promisedDate < businessDate
```

Persisted `ACTIVE -> BROKEN` happens only via explicit application/operator command in Slice 9.

### 7.4 Dispute

Minimum lifecycle:

```text
OPEN
RESOLVED
CANCELLED
```

Allowed transitions:

```text
OPEN -> RESOLVED | CANCELLED
RESOLVED/CANCELLED -> terminal
```

API:

```text
GET  /api/v1/disputes
GET  /api/v1/disputes/{disputeId}
POST /api/v1/disputes
PUT  /api/v1/disputes/{disputeId}
POST /api/v1/disputes/{disputeId}/resolve
POST /api/v1/disputes/{disputeId}/cancel
```

Resolution stores stable reason/code/summary, actor and resolvedAt.

Raw unbounded notes MUST NOT automatically become public/log payload.

Filters:

```text
customerId
caseId
status
category
createdFrom
createdTo
```

### 7.5 Collection actions / work queue

ActionStatus:

```text
PENDING
COMPLETED
CANCELLED
```

`overdue` is derived, not a persisted status:

```text
status == PENDING && dueAt < now(clock)
```

API:

```text
GET  /api/v1/collection-actions
GET  /api/v1/collection-actions/{actionId}
POST /api/v1/collection-actions
PUT  /api/v1/collection-actions/{actionId}
POST /api/v1/collection-actions/{actionId}/complete
POST /api/v1/collection-actions/{actionId}/cancel
```

`PUT` edits scheduling metadata (`assignedTo`, `dueAt`, `priority`, allowed type-specific metadata) using `version`; completion/cancellation are commands.

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

Default work-queue sort:

```text
dueAt ASC, priority DESC, id ASC
```

### 7.6 Unified timeline

```text
GET /api/v1/collection-cases/{caseId}/timeline
```

Paginated Slice-style projection aggregates:

```text
case state transitions
promises
promise transitions
disputes
dispute transitions
actions
action completion/cancellation
```

Stable chronology:

```text
eventAt DESC, eventId DESC
```

Frontend MUST NOT reconstruct chronology by downloading separate lists.

### 7.7 Collection invariants

- no hard delete workflow history;
- tenant-scoped parent/child access;
- validated transitions;
- `version` for concurrent operator updates;
- actor/time/reason audit for significant transitions;
- notification failure cannot erase Collection state;
- no duplicated invoice/payment balances;
- Collection does not directly use Receivable repositories;
- automatic escalation/assignment/breach remains out-of-scope.

### 7.8 9C acceptance

Frontend supports:

```text
Collection work queue
Case detail/timeline
Promise-to-Pay management
Dispute management
Next action scheduling/completion
```

without inventing business state on frontend.

---

## 8. Slice 9D — Frontend Support API

### 8.1 Dashboard read models

```text
GET /api/v1/dashboard/summary
GET /api/v1/dashboard/receivables
GET /api/v1/dashboard/delivery
GET /api/v1/dashboard/collections
```

Every time-dependent dashboard response MUST expose:

```text
asOf: Instant
businessDate: LocalDate
```

Receivable semantics:

```text
dueToday:
  dueDate == businessDate
  AND outstandingAmount > 0
  AND paymentStatus not PAID/CANCELLED

dueSoon:
  businessDate < dueDate <= businessDate + 7 days
  AND outstandingAmount > 0
  AND paymentStatus not PAID/CANCELLED

aging buckets for outstanding invoices:
  CURRENT       -> daysOverdue == 0
  DAYS_1_30     -> 1..30
  DAYS_31_60    -> 31..60
  DAYS_61_90    -> 61..90
  DAYS_90_PLUS  -> >90
```

Different currencies MUST NOT be summed into one number without authoritative FX/base-currency model. Until then totals grouped by currency.

Delivery dashboard uses existing delivery counters/metrics semantics, not full Message-table recalculation per refresh.

Collection dashboard includes open/active cases, overdue actions, active promises due/overdue, broken promises and open disputes.

Dashboard remains read model only.

### 8.2 Reference data

Expose static compile-time code lists through consistent API:

```text
GET /api/v1/reference-data
```

or bounded resources if payload grows:

```text
GET /api/v1/reference-data/channels
GET /api/v1/reference-data/currencies
GET /api/v1/reference-data/locales
GET /api/v1/reference-data/statuses
```

Tenant-configurable `CustomerSegment` is NOT reference data.

### 8.3 Import history

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

Import list paginated. Error rows Slice/Page without unbounded raw payload/PII.

### 8.4 Generated documents

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

### 8.5 Campaigns/runs/recipients

Normalize existing unbounded lists.

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

Runs paginated.

Recipients MUST be paginated/high-volume aware.

Canonical hierarchy by 9D freeze:

```text
GET /api/v1/campaigns/{campaignId}/runs/{runId}/recipients
```

with tenant + campaign + run validation.

Legacy route may exist temporarily before freeze, but two divergent contracts MUST NOT survive 9D.

Message list remains Slice-style unless exact totals are proven UI requirement.

### 8.6 Templates/versions

Existing template/version lists checked for unbounded collections.

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

### 8.7 API freeze/OpenAPI compatibility

Before 9D completion deliberate breaking cleanup inside `/api/v1` is allowed because frontend baseline is not frozen.

At 9D completion:

```text
generate/store OpenAPI baseline
activate CI compatibility check
```

After freeze CI MUST reject undeclared representative breaking changes:

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

Use existing OpenAPI compatibility tooling; do not build custom framework without need.

---

## 9. Database/query/index requirements

No list endpoint may use `findAll()` + Java filtering.

Push tenant/filter/sort/page into PostgreSQL.

Representative list paths SHOULD use projections/query DTO.

Typical index candidates, justified by real query patterns:

```text
(tenant_id, created_at DESC, id DESC)
(tenant_id, status, created_at DESC, id DESC)
(tenant_id, customer_id, created_at DESC, id DESC)
(tenant_id, due_date, id)
(tenant_id, assigned_to, status, due_at, id)
```

Additional required constraints/indexes introduced by Slice 9:

```text
Contract: UNIQUE (tenant_id, external_id)
PaymentAllocation: UNIQUE (tenant_id, command_id)
CollectionCase: enforce one active case per tenant+invoice through application invariant and PostgreSQL partial unique index where practical
```

Do not mechanically index every filter combination.

Each sub-slice PR provides representative `EXPLAIN ANALYZE` evidence for:

```text
default list
common combined filter
one high-volume path
```

Acceptance: representative list DTO performs no lazy-per-row association traversal.

---

## 10. Security/privacy/observability

- tenant restriction in DB query;
- tenant from authenticated context;
- reuse existing `SystemRole`, human/service authorities and ServiceClient scopes;
- no raw email/phone in logs/error messages;
- no auth tokens/password hashes/raw provider response in responses/logs;
- custom fields cannot bypass privacy rules;
- `404` hides cross-tenant existence;
- reuse existing trace/correlation context;
- log endpoint/operation/status/latency and safe tenant context;
- no raw business request/response body logging by default;
- payment/contact/custom-field payload follows redaction policy;
- operational metric labels must be bounded cardinality;
- no customerId/invoiceId/email/raw high-cardinality tenant labels in metrics.

Do not introduce parallel `requestId` when existing `traceId`/`correlationId` already provide correlation.

---

## 11. Tests

Every paginated list endpoint includes:

```text
default page/size
max size 200
invalid page/size -> 400
deterministic order + tie-breaker
important individual filters
representative AND combined filters
search normalization/matching semantics
allowed/invalid sorting
empty result
tenant A cannot see tenant B
wrong nested parent -> 404
unauthenticated -> 401
human own-tenant access
service scope positive/negative where M2M exposed
tenant user -> admin API = 403
no duplicate rows from joins
representative no-N+1
ProblemDetail code/traceId/correlationId for 400/403/404/409
```

### 11.1 9A-1 tests

```text
Customer externalId uniqueness
PUT does not mutate externalId/tenant
status command validation
email normalization/uniqueness
phone normalization/uniqueness
one ACTIVE primary email
one ACTIVE primary phone
primary atomic demotion
contact ownership
segment tenant isolation
segment add/remove idempotency
```

### 11.2 9A-2 tests

```text
Contract tenant/customer ownership
externalId uniqueness
contractNumber may repeat when externalId differs
validTo >= validFrom
valid/invalid lifecycle transitions
terminal CLOSED/CANCELLED
optimistic version conflict
Invoice contract/customer consistency
```

### 11.3 9B tests

```text
exact BigDecimal amounts
persisted Invoice paid/outstanding/paymentStatus authoritative
Clock/business-zone overdue calculation
partial allocation
payment over-allocation rejection
invoice over-allocation rejection
currency mismatch
customer mismatch
parallel allocation locking
commandId idempotent replay
commandId conflicting payload
reversal
second reversal -> 409
parallel allocate/reverse safety
transaction rollback atomicity
```

### 11.4 9C tests

```text
one active case per tenant+invoice
case cannot open for paid/cancelled/zero-outstanding invoice
valid/invalid case transitions
case close reason required
optimistic version conflict
promise valid transitions
promise overdue projection without automatic BROKEN mutation
promise explicit break
promise currency/outstanding validation
dispute resolution/cancel
collection action overdue
collection action complete/cancel
timeline stable chronology
no duplicated financial state
```

### 11.5 9D tests

```text
dashboard currency grouping
dashboard asOf/businessDate
dueToday/dueSoon semantics
aging bucket boundaries
aggregate tenant isolation
import errors paging/redaction
generated-document authorization
campaign/run/recipient/template pagination
OpenAPI compatibility check catches representative breaking change
```

Persistence/concurrency tests use PostgreSQL/Testcontainers according to existing test-runtime contract.

---

## 12. Performance acceptance

Minimum:

```text
DB-side pagination/filter/sort
projection for representative list paths
no unbounded collection load
no lazy-per-row N+1
indexes backed by query plans
bounded dashboard query count
no expensive COUNT where UI does not need totals
server-side max page size
```

Do not introduce Elasticsearch/OpenSearch without measured need.

---

## 13. OpenAPI/frontend contract

All public Slice 9 endpoints MUST be present in generated OpenAPI.

Before `Frontend API Ready` verify:

```text
query params documented
enum values visible
state-command request/response documented
response/request DTO documented
400/401/403/404/409 consistent
ProblemDetail schema documented
Page/Slice schema consistent
nullable fields explicit
money/date/time conventions consistent
business date timezone documented
examples contain no real PII/secrets
```

Frontend TypeScript client must be generatable/typeable without persistence knowledge.

After 9D generated OpenAPI baseline is compatibility boundary.

---

## 14. Implementation order

```text
9A-1 Customer + Segment normalization
        ↓
9A-2 Contract foundation + API
        ↓
9B Receivables + Payments hardening/normalization
        ↓
9C Collection foundation + API
        ↓
9D Frontend support + normalization + API freeze
```

Each implementation PR starts from current `main`. Documentation/spec branch MUST NOT be used as code base branch.

9D depends on 9A-2/9B/9C read models for complete dashboard.

---

## 15. Explicit out-of-scope

- React/frontend implementation;
- new IAM/ABAC framework;
- replacement identity/security model;
- duplicate PrincipalType/technical-account model;
- fine-grained business RBAC without confirmed requirement;
- SMS/WhatsApp/Telegram/Push provider adapters;
- BPM/workflow engine;
- automatic collection escalation/assignment/rules engine;
- automatic case-opening scheduler;
- automatic Promise-to-Pay breach scheduler;
- customer-level CollectionCase in MVP;
- reopening CLOSED CollectionCase in Slice 9;
- generic reporting/query DSL;
- Elasticsearch/OpenSearch without measured requirement;
- billing/plans/usage metering;
- 1C/ERP-specific connectors;
- provider-specific technical roles;
- arbitrary cross-tenant admin search;
- FX conversion engine if no authoritative FX model exists;
- arbitrary mutation of invoice/payment original financial facts;
- automatic Contract renewal workflow;
- hard delete financial/collection history.

---

## 16. Definition of Done — sub-slice

Each sub-slice is complete only when:

1. Scope is implemented against current `main`, not obsolete assumptions.
2. Domain ownership is verified; no competing authoritative model introduced.
3. Tenant isolation is enforced in repository/SQL.
4. Pagination/filter/sort are bounded and deterministic.
5. DTOs expose no JPA/internal secret model.
6. Existing identity/security model is reused.
7. Existing ProblemDetail/correlation conventions are reused.
8. Existing `Clock` and explicit business ZoneId are used for time-dependent behavior.
9. Business state transitions match this spec and are domain/application controlled.
10. Financial mutation concurrency/idempotency rules are enforced where applicable.
11. PostgreSQL/Testcontainers integration tests are green.
12. Liquibase migration is in master changelog when schema changed.
13. Representative query plans/indexes checked; no lazy-per-row N+1.
14. OpenAPI reflects contract.
15. `mvn verify` is green.
16. PR is not auto-merged without explicit decision.

---

## 17. Definition of Done — Frontend API Ready

Collectra backend becomes **Frontend API Ready** only after all Slice 9 implementation PRs are merged and frontend can implement without backend workarounds:

```text
Login/session
Customers
Customer detail/profile
Emails/phones
Segments/membership
Contracts
Receivables/invoices
Payments
Payment allocation/reconciliation/reversal
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

Final gates:

```text
tenant isolation verified
existing human/service security model verified
canonical ProblemDetail contract stable
business date/timezone semantics stable
financial allocation concurrency/idempotency verified
state transitions documented and tested
representative list endpoints free of N+1
OpenAPI breaking-change CI gate enabled
```

После этого новые frontend requirements могут добавлять endpoint-specific capabilities, но отсутствие базового CRUD/read/list/filter/paging/workflow contract не должно блокировать основной UI.
