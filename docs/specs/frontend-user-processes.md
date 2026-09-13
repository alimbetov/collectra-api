# FrontendWeb — User Processes

Status: REVIEWED / API-ALIGNED BUSINESS-SYSTEM BASELINE

## 1. Purpose

Зафиксировать пользовательские процессы Collectra до разработки React DTO/client layer.

Последовательность проектирования:

```text
Business process
    ↓
User task
    ↓
Screen / command
    ↓
Existing API contract
    ↓
Frontend DTO
    ↓
React query/mutation
```

Frontend DTO и API hooks не проектируются в отрыве от процессов.

## 2. Primary business outcome

Collectra помогает организации пройти полный цикл:

```text
onboard customer
    ↓
record contract / receivable
    ↓
track outstanding debt
    ↓
communicate with customer
    ↓
collect payment / reconcile
    ↓
manage collection exceptions
    ↓
measure delivery and collection outcome
```

Каналы коммуникаций на текущем этапе mock-driven. Пользовательский процесс от этого не меняется.

## 3. Actor model

### Operator

Работает с customers, receivables, campaigns/messages в рамках выданных permissions.

### Collection Officer

Работает с overdue invoices и collection cases: Promise-to-Pay, dispute, next action, closure.

### Campaign Manager

Создаёт/активирует campaigns, выбирает audience/template/channel, запускает run, контролирует recipients/messages.

### Content Manager

Управляет templates, versions, placeholders, preview, validation и publication.

### Tenant Administrator

Управляет membership, invitations, roles/permissions и пользовательским доступом.

Один человек может совмещать роли. UI обязан строить доступ по authorities, а не по жёсткому frontend role name.

## 4. Process P1 — Tenant registration and first login

Business goal: создать организацию и первого администратора.

```text
User opens registration
    ↓
enters tenant slug/company/email/password
    ↓
POST /api/v1/auth/tenants/register
    ↓
receives auth tokens
    ↓
loads current user / tenant context
    ↓
opens initial dashboard/onboarding
```

UI states:

- validation error;
- duplicate/conflict;
- registration success;
- authenticated bootstrap failure.

After successful bootstrap, frontend must never ask ordinary tenant user to type tenant UUID again.

## 5. Process P2 — Login/session lifecycle

Web login uses stable human-readable tenant slug, not tenant UUID:

```text
Login form
  tenant slug
  email
  password
  ↓
POST /api/v1/auth/login/by-slug
  ↓
access + refresh token
  ↓
GET /api/v1/identity/me
  ↓
load permissions/navigation
  ↓
workspace
```

`POST /api/v1/auth/login` with `tenantId` remains a backward-compatible API for existing clients/tests and is not the normal `frontendweb` login flow.

Unknown slug and invalid credentials must be externally indistinguishable (`401 UNAUTHORIZED`) to avoid tenant enumeration.

Token expiry:

```text
API 401 due access expiry
  ↓
POST /api/v1/auth/refresh
  ├─ success -> retry original request once
  └─ failure -> clear session -> login
```

Logout:

```text
POST /api/v1/auth/logout
```

Security page additionally supports logout-all and session revocation through existing identity API.

## 6. Process P3 — Customer onboarding

Business goal: создать customer master data, contacts and segmentation.

```text
Customers -> New customer
    ↓
create profile
    ↓
customer detail opens
    ↓
add email(s)
    ↓
add phone(s)
    ↓
set primary contacts
    ↓
assign segment(s)
    ↓
optionally create contract
```

Rules visible in UI:

- externalId is identity/business reference, not arbitrary editable display field;
- only one active primary email and phone should exist;
- changing a contact value is modeled as deactivate old + create new;
- segments are managed through membership commands;
- backend remains authoritative for invariants.

Success outcome: Customer detail becomes operational landing page for all customer-related work.

## 7. Process P4 — Contract onboarding

Business goal: register commercial relationship without duplicating receivable state.

```text
Customer -> Contracts -> New contract
    ↓
externalId + contractNumber + validity
    ↓
POST /api/v1/contracts
    ↓
contract ACTIVE
    ↓
invoice creation/import may reference contract
```

Lifecycle process:

```text
ACTIVE
 ├─ suspend -> SUSPENDED
 ├─ close   -> CLOSED
 └─ cancel  -> CANCELLED

SUSPENDED
 ├─ activate -> ACTIVE
 ├─ close    -> CLOSED
 └─ cancel   -> CANCELLED
```

Frontend actions depend on current status and use `version`.

## 8. Process P5 — Receivable creation/import

There are two entry modes.

### Manual invoice

```text
Customer/Receivables -> New invoice
    ↓
select customer
optional contract
invoice number/external id
invoice date/due date
amount/currency
document reference/custom fields
    ↓
POST /api/v1/invoices
```

### Bulk/API import

```text
Imports
  ↓
select source schema / mapping profile / template version
  ↓
upload CSV/XLSX or send JSON/XML according to allowed API
  ↓
Idempotency-Key
  ↓
202 Accepted
  ↓
view batch result
  ↓
created business records become visible in Customers/Receivables
```

UI must not maintain a second import-only representation of financial truth after persistence. The created Invoice is authoritative.

Import configuration (Source Schema / Mapping Profile) is a Data Manager/Admin flow; import execution is an Operator flow. They are separate screens and permission surfaces.

## 9. Process P6 — Daily receivables monitoring

Business goal: identify what requires attention.

```text
Dashboard
    ↓
click overdue/outstanding KPI
    ↓
Invoices list with filters
    ↓
inspect invoice
    ↓
choose next business action
```

Typical actions:

```text
open customer
open contract
open document
inspect allocations
create/open collection case
prepare communication campaign
```

Frontend uses backend-provided:

```text
paidAmount
outstandingAmount
paymentStatus
overdue
daysOverdue
```

No local recomputation of financial state.

## 10. Process P7 — Payment registration and allocation

### Register payment

```text
Payments -> New payment
    ↓
customer/date/amount/currency/reference/source
    ↓
POST /api/v1/payments
    ↓
payment detail
```

### Allocate payment

```text
Payment detail
    ↓
Allocate
    ↓
choose invoice for same customer/currency
    ↓
enter amount
    ↓
frontend creates commandId UUID
    ↓
POST /api/v1/payments/{id}/allocations
    ↓
refresh payment + invoice authoritative state
```

If request outcome is uncertain due network error, frontend retries same user intent with the same `commandId`.

### Reverse allocation

```text
Allocation -> Reverse
    ↓
confirm + reason
    ↓
send current allocation version
    ↓
backend reverses atomically
    ↓
refresh invoice/payment
```

409 conflict must trigger authoritative reload, never local compensation logic.

## 11. Process P8 — Collection case lifecycle

Business goal: manage operator workflow when receivable needs intervention.

### Open case

```text
Overdue invoice
    ↓
Open collection case
    ↓
priority + assignee
    ↓
POST /api/v1/collection-cases
    ↓
case workspace
```

### Work case

```text
OPEN
  ↓ start
IN_PROGRESS
  ├─ create Promise-to-Pay
  ├─ create dispute
  ├─ create next action
  ├─ hold
  └─ close
```

The collection list is a server-side work-queue projection and already supplies customer/invoice labels, financial context, assignee label and the earliest pending next action. React must not fan out row-by-row lookups.

### Promise-to-Pay

```text
create promise(amount, currency, promisedDate)
    ↓
ACTIVE
 ├─ fulfill
 ├─ break
 └─ cancel
```

An overdue ACTIVE promise may be shown as overdue by backend projection, but frontend must not silently convert status to BROKEN.

### Dispute

```text
create dispute
    ↓
OPEN
 ├─ resolve(resolutionCode, summary)
 └─ cancel
```

### Action

```text
create action(type, description, dueAt, priority)
    ↓
PENDING
 ├─ complete
 └─ cancel
```

### Timeline

Every case workspace uses backend timeline as chronological operator context. Frontend does not reconstruct timeline from several unrelated lists when authoritative timeline exists.

## 12. Process P9 — Template authoring

Business goal: prepare provider-neutral communication content.

```text
Templates -> New template
    ↓
code/name/documentType
    ↓
create version
    ↓
locale + channel + subject + content + stylesheet
    ↓
insert placeholders using builder catalogue
    ↓
Validate
    ↓
Preview with sample payload
    ↓
Publish
```

The existing Template Builder API supplies fields, assets, channels, syntax and builder schema version, plus draft/document validation and preview.

Revision process:

```text
Published version
    ↓
reopen/new editable state according to backend semantics
    ↓
edit
    ↓
validate/preview
    ↓
publish
```

Campaign creation should select a published compatible template version rather than free-form content.

## 13. Process P10 — Campaign creation and execution

Business goal: communicate with selected customers without leaking provider specifics into business process.

```text
Campaigns -> New campaign
    ↓
Basics: name + channel
    ↓
Audience selection
    ↓
Template version
    ↓
Schedule
    ↓
Review
    ↓
POST /api/v1/campaigns
    ↓
activate
    ↓
prepare run
    ↓
materialize recipients/messages
    ↓
mock delivery worker processes messages
    ↓
operator monitors run/messages
```

Audience selection contract is explicit:

```text
customerIds
segmentIds
daysOverdueFrom / daysOverdueTo
amountFrom / amountTo
```

The user sees `channel`, delivery status and business-safe errors. The UI does not care whether delivery used mock, KumoMTA or a future real SMS/Telegram/WhatsApp provider.

## 14. Process P11 — Eligibility change after campaign preparation

```text
Campaign prepared
    ↓
customer/payment state changes
    ↓
operator/system eligibility recheck
    ↓
ineligible recipient/message is skipped according to backend contract
```

Frontend expectation:

- show eligibility/run status returned by backend;
- never decide paid/unpaid eligibility from stale browser data;
- after recheck, invalidate/reload run recipients/messages.

## 15. Process P12 — Delivery monitoring

```text
Campaign detail
    ↓
Run
    ↓
Recipients / Messages
    ↓
filter status/channel/customer
    ↓
open message detail
```

Operator needs to answer:

```text
Was it prepared?
Was it attempted?
Was it accepted/sent?
Is it waiting for retry?
Did it fail permanently?
What safe reason is visible?
Are required attachments ready?
```

Provider credentials/raw response bodies are not user-facing diagnostics. Mock outcomes must produce the same frontend-visible status machine as later real adapters.

## 16. Process P13 — File/document interaction

Files are embedded into business workflows.

```text
Invoice detail -> linked document -> download/open
```

```text
Upload file
    ↓
receive FileMetadata/fileId
    ↓
reference file from owning domain operation
```

Download may use direct content endpoint or presigned URL. UI should prefer backend-supported safe flow rather than constructing storage URLs.

## 17. Process P14 — Tenant administration

```text
Administration
    ├─ invite member
    ├─ view members (email/display name/status)
    ├─ read current member roles
    ├─ assign/update roles
    ├─ manage custom roles
    └─ review permissions
```

Business rule: frontend permission visibility is convenience only. Every operation must still be authorized by backend.

Last-admin and lifecycle invariants must surface backend conflicts as explicit guidance rather than generic failure.

## 18. Process P15 — Profile and security

```text
User menu -> Profile
    ↓
GET /api/v1/identity/me
```

User can:

- update display name/locale/timezone;
- change password;
- view sessions;
- revoke a session;
- logout all.

Changing locale/timezone should invalidate/re-render frontend formatting context without changing business dates already returned by backend.

## 19. Cross-process navigation

Mandatory deep links:

```text
Customer -> Contract
Customer -> Invoice
Customer -> Payment
Customer -> Collection Case
Invoice -> Customer
Invoice -> Contract
Invoice -> Collection Case
Payment -> Invoice via allocation
Collection Case -> Customer + Invoice
Campaign -> Run -> Recipient -> Message
Message -> Customer where customerId is available
Dashboard KPI -> filtered operational list
```

## 20. User decision points that belong to backend

Frontend must not independently decide:

```text
whether invoice is paid
days overdue authoritative meaning
whether allocation fits payment/invoice
whether collection case can open
whether workflow transition is valid
whether recipient is eligible
whether message may send with attachment state
whether retry is due
whether tenant/user has authority
```

Frontend may pre-disable obviously impossible actions for UX, but backend response is authoritative.

## 21. Error-driven process behavior

### 400

Show field/filter validation mapped from `ProblemDetail.errors` / `code`.

### 401

Attempt refresh once where appropriate; otherwise return to login.

### 403

Do not retry. Show permission/access state.

### 404

Show not found. Do not reveal whether foreign-tenant object exists.

### 409

Map business `code` to specific next action:

```text
VERSION_CONFLICT               -> reload and review changes
IDEMPOTENCY_CONFLICT           -> stop replay, show conflict
ALLOCATION_EXCEEDS_PAYMENT     -> reload payment balance
ALLOCATION_EXCEEDS_INVOICE     -> reload invoice balance
CURRENCY_MISMATCH              -> choose compatible invoice/payment
CUSTOMER_MISMATCH              -> choose same-customer resource
COLLECTION_CASE_ALREADY_ACTIVE -> open existing case if discoverable through list/filter
```

Every ProblemDetail may include `traceId` and `correlationId` for support diagnostics; frontend does not expose sensitive server details.

## 22. Async process policy

For import batches, campaign runs/messages and generated documents:

```text
non-terminal -> bounded polling
terminal     -> stop polling
window hidden/background -> slow down or pause
mutation success -> immediate targeted refetch
route unmount -> stop polling
```

Never use an unbounded global `setInterval` loop.

## 23. React boundary derived from processes

```text
features/auth
features/dashboard
features/customers
features/contracts
features/receivables
features/collections
features/templates
features/campaigns
features/messages
features/imports
features/files
features/admin
features/profile
```

Each feature owns:

```text
api.ts
contracts.ts
queries.ts
mutations.ts
components/
pages/
```

Shared transport concerns:

```text
shared/api/http-client
shared/api/problem-detail
shared/auth/session
shared/ui
shared/utils
```

## 24. Next analysis step

Produce `frontend-react-api-contract.md` with:

1. exact TypeScript DTOs derived from backend responses;
2. query parameter types;
3. mutation request types;
4. normalized `PageResponse` / `SliceResponse` / `ProblemDetail` types;
5. TanStack Query keys;
6. API functions;
7. cache invalidation graph after mutations;
8. auth refresh interceptor behavior;
9. permission guards;
10. screen-to-query mapping.

This DTO/client contract must derive from this process model and the factual backend API, not from JPA entities.
