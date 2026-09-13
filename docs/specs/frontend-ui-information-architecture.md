# FrontendWeb — UI Information Architecture

Status: REVIEWED / API-ALIGNED PRODUCT/UI BASELINE

## 1. Goal

Спроектировать пользовательский интерфейс Collectra поверх фактически существующего `/api/v1` backend contract так, чтобы frontend не создавал собственную бизнес-логику и не дублировал authoritative state backend.

UI проектируется вокруг пользовательской работы, а не вокруг Java package structure.

## 2. Product roles

### Tenant User

Операционная работа с клиентами, договорами, дебиторской задолженностью, кейсами взыскания, кампаниями и сообщениями в пределах своего tenant.

### Tenant Administrator

Всё доступное Tenant User плюс управление пользователями, ролями, приглашениями и настройками tenant в пределах предоставленных permissions.

### Platform Administrator

Отдельный platform/admin UI. Не смешивается с обычным tenant workspace.

## 3. Main navigation

```text
Collectra
├── Dashboard
├── Customers
├── Receivables
│   ├── Invoices
│   └── Payments
├── Collections
├── Campaigns
├── Templates
├── Imports
├── Files
├── Administration            // permission-based
│   ├── Members
│   ├── Invitations
│   ├── Roles & Permissions
│   └── Integrations / technical configuration (later)
└── Profile
    ├── My profile
    ├── Security
    └── Sessions
```

Contracts не выносятся в первичную навигацию как обязательный top-level пункт. Основной UX: открыть Contract из Customer detail или через Receivables filters. Отдельный Contracts list может быть добавлен как secondary navigation для организаций с большим contract volume.

## 4. Application shell and authentication

Desktop-first admin application.

```text
+--------------------------------------------------------------+
| Logo | Tenant name                         Search | User menu |
+----------------------+---------------------------------------+
| Sidebar              | Breadcrumbs                           |
| Dashboard            |---------------------------------------|
| Customers            | Page title                Actions     |
| Receivables          |---------------------------------------|
| Collections          | Filters / toolbar                     |
| Campaigns            |---------------------------------------|
| Templates            | Main content                          |
| Imports              |                                       |
| Files                |                                       |
| Administration       |                                       |
+----------------------+---------------------------------------+
```

Frontend login form:

```text
Tenant / workspace slug
Email
Password
```

It uses `POST /api/v1/auth/login/by-slug`. Ordinary web users never type or manage tenant UUID before authentication. The old UUID-based login endpoint remains compatibility-only.

Global requirements:

- tenant context is derived from authenticated session, never selectable arbitrary tenantId in ordinary tenant workspace;
- navigation items are permission-aware;
- 401 -> auth recovery/login;
- 403 -> access denied screen/action disabled;
- 404 -> generic not-found/foreign-tenant-safe state;
- 409 -> business conflict with actionable UI message;
- server `ProblemDetail.code` drives user-visible error mapping;
- pagination/filter/sort remain server-side.

## 5. Dashboard

Backend surface:

```text
GET /api/v1/dashboard/summary
GET /api/v1/dashboard/receivables
GET /api/v1/dashboard/delivery
GET /api/v1/dashboard/collections
```

UI composition:

```text
Dashboard
├── KPI row
│   ├── Customers
│   ├── Outstanding receivables
│   ├── Overdue receivables
│   └── Active collection cases
├── Receivables card
│   ├── total outstanding
│   ├── overdue amount / aging buckets
│   └── due today / due soon
├── Delivery card
│   ├── recipients
│   ├── sent
│   ├── retry
│   ├── failed
│   └── skipped
└── Collections card
    ├── active cases
    ├── overdue actions
    ├── promises due/overdue/broken
    └── open disputes
```

Dashboard is a navigation surface, not a second source of truth. Clicking KPI/card opens the corresponding filtered list.

## 6. Customers

### Customers list

Columns are now directly supported by the server-side list projection:

```text
Name
External ID
Type
Status
Primary email
Primary phone
Manager
Segments
Updated
```

`GET /api/v1/customers` returns primary contacts, manager display label and segment summaries in bounded batch queries. React MUST NOT fetch contacts/managers/segments row-by-row.

Filters:

```text
search
status
customerType
manager
segment
externalId
email
phone
created range
```

Actions:

```text
New customer
Open customer
Change status
```

### Customer detail

```text
Customer: Acme LLP
[Overview] [Contacts] [Contracts] [Invoices] [Payments] [Collections] [Activity]
```

Overview:

- identity/profile;
- external ID;
- type/status;
- manager;
- locale/timezone;
- custom fields;
- segments;
- high-level receivable counters only when supplied by an authoritative query surface.

Contacts:

- email list;
- phone list;
- add;
- set primary;
- activate/deactivate.

Do not edit email/phone value in place if backend contract models replacement as deactivate + add.

Contracts, invoices, payments and collection cases use server-side `customerId` filters.

## 7. Contracts

Backend supports list/detail/create/update and lifecycle commands:

```text
ACTIVE -> SUSPENDED / CLOSED / CANCELLED
SUSPENDED -> ACTIVE / CLOSED / CANCELLED
```

UI:

```text
Contract detail
├── Contract number / external ID
├── Customer link
├── Status badge
├── Validity dates
├── Renewal date
├── Custom fields
├── Related invoices
└── Lifecycle actions
```

Lifecycle actions use current `version`; stale update maps to `409 VERSION_CONFLICT` and UI reload prompt.

## 8. Receivables

### Invoices list

```text
Invoice number
Customer
Contract
Invoice date
Due date
Original amount
Paid
Outstanding
Currency
Payment status
Days overdue
```

Primary filters:

```text
search
customer
contract
paymentStatus
currency
due range
overdue
amount range
outstanding range
```

Frontend never recalculates authoritative `paidAmount`, `outstandingAmount`, `paymentStatus`, `overdue` or `daysOverdue`.

### Payments list/detail

```text
Date
Customer
Reference
Amount
Currency
Source
```

Payment detail contains payment facts and allocations. Allocation mutation creates one UUID `commandId` per user intent and reuses it on safe replay.

## 9. Collections

This is the main operational workspace for collection officers.

### Collection work queue

The list projection directly supports:

```text
Customer
Invoice
Status
Priority
Assigned user
Opened
Outstanding / currency / payment status
Next action / due / overdue indicator
```

Filters:

```text
customer
invoice
status
priority
assignedTo
```

Customer/invoice/assignee labels and earliest pending next action are resolved server-side. No row fan-out is permitted from React.

### Collection case detail

```text
Case header
├── Customer
├── Invoice
├── Outstanding amount (read-only from Receivable)
├── Status / priority / assignee
└── workflow actions

Tabs / panels
├── Timeline
├── Promise to Pay
├── Disputes
└── Actions
```

Supported operations map directly to backend commands:

```text
start
hold
close
create promise
fulfill/break/cancel promise
create dispute
resolve/cancel dispute
create action
complete/cancel action
```

UI never permits arbitrary status dropdown where backend exposes explicit commands.

## 10. Templates

Templates are split into two user concerns.

### Template Management

- definitions;
- versions;
- locale/channel/status;
- publish/reopen/archive lifecycle.

### Template Builder

The current builder API supplies:

- field catalogue;
- assets;
- supported channels;
- placeholder/loop/asset syntax;
- builder schema version;
- draft validation and preview;
- structured builder document validation and preview;
- save/update builder versions.

Recommended editor:

```text
+-----------------------+-------------------------------+
| Fields/placeholders   | Editor                        |
| {{customer.*}}        | subject                       |
| {{invoice.*}}         | HTML/structured content       |
| {{custom.*}}          | stylesheet                    |
+-----------------------+-------------------------------+
| Preview                                                |
+--------------------------------------------------------+
```

Channel mocks do not change this UX: template/version/channel model stays provider-neutral.

## 11. Campaigns

### Campaign list

```text
Name
Channel
Template
Status
Scheduled at
Last run summary when supplied by read projection
```

Filters:

```text
search
status
channel
scheduled range
created range
```

### Campaign creation wizard

```text
Step 1 — Basics
  name
  channel

Step 2 — Audience
  customerIds
  segmentIds
  daysOverdueFrom / daysOverdueTo
  amountFrom / amountTo

Step 3 — Template
  choose published compatible templateVersion

Step 4 — Schedule
  now / scheduledAt

Step 5 — Review
  audience summary
  template preview
  channel
  schedule

Create
```

### Campaign detail

```text
Overview
Runs
Recipients
Messages
```

Actions:

```text
Activate
Prepare/Create run
Eligibility recheck
```

Message delivery status remains provider-neutral. Mock and real providers render through the same UI.

## 12. Message / delivery monitoring

Nested under campaign run:

```text
Campaign -> Run -> Messages
```

List columns supported by current message projection:

```text
Customer reference
Channel
Masked destination
Status
Attempt count
Next retry
Sent at
Created at
```

Message detail additionally shows:

- invoice/template references;
- resolved locale;
- processing/retry/sent timestamps;
- provider message id where safe;
- normalized error code + safe summary;
- attachment status;
- no secrets/raw provider auth or raw provider body.

## 13. Imports

Import is split into two workflows.

### Import Configuration — Data Manager/Admin

```text
Source Schema
  definitions -> versions -> fields -> row configuration -> validate -> publish

Mapping Profile
  definitions -> versions -> rules -> test -> validate -> publish
```

### Import Execution — Operator

```text
1 Select published mapping profile version
2 Select template/version and output formats
3 Upload file or choose JSON/XML mode
4 Submit with stable Idempotency-Key
5 Show accepted batch
6 Bounded poll/view batch result
7 Show safe errors/result documents
```

The created business entities are authoritative after persistence; frontend does not maintain a parallel financial model.

## 14. Files

Files are contextual resources rather than a standalone DMS.

Supported UI actions:

- upload;
- show metadata;
- open/download;
- request presigned download URL;
- delete when permission and domain rules allow.

File picker should be reusable from invoices, templates/assets, attachments and imports where relevant.

## 15. Administration

Administration is permission-driven.

```text
Members
Invitations
Roles
Permissions
Account lifecycle
```

The member list now returns membership id, user id, email, display name and status. Current member role ids are available from a dedicated endpoint when opening role editing. This avoids embedding an unbounded role graph into every row.

Navigation item is hidden if user cannot access administration surfaces. Backend remains authoritative; hiding UI is not an authorization control.

## 16. Profile & security

Current user API supports:

```text
GET/PATCH /api/v1/identity/me
POST /api/v1/identity/me/change-password
GET /api/v1/identity/me/sessions
DELETE /api/v1/identity/me/sessions/{id}
POST /api/v1/auth/logout-all
```

UI:

```text
My Profile
├── Display name
├── Locale
└── Timezone

Security
├── Change password
└── Sessions
    ├── current session
    ├── other sessions
    └── revoke
```

## 17. Shared UX patterns

### Status badges

All enum/status rendering uses a centralized frontend dictionary per domain. Unknown backend enum value renders as `Unknown (<raw>)`, not a page crash.

### Optimistic concurrency

Entities exposing `version` keep it in frontend state. Mutation sends current version. `409 VERSION_CONFLICT` => inform user and reload authoritative state.

### Destructive/business-significant actions

Close/cancel/reverse/archive/publish use confirmation dialogs and explain the business consequence.

### Filters

Filters live in URL query state where practical so screens are bookmarkable/shareable. No client-side filtering of unbounded lists.

### Async polling

Import batches, campaign runs/messages and generated documents use bounded polling that stops on terminal state, slows/pauses in background and stops on route unmount.

### Loading

Use skeletons for first load and localized spinners for mutations. Do not blank an entire detail page for a single command.

## 18. UI implementation order

```text
FW-UX1 Auth + application shell
FW-UX2 Dashboard
FW-UX3 Customers + contacts + segments
FW-UX4 Contracts
FW-UX5 Invoices + payments + allocations
FW-UX6 Collections workspace
FW-UX7 Templates / Builder
FW-UX8 Campaigns + runs + messages
FW-UX9 Imports + files
FW-UX10 Administration + profile
```

## 19. Definition of Done for UI baseline

This UI baseline is accepted when:

- every primary screen maps to an existing backend bounded context/API;
- business state is not recomputed in browser;
- user commands map to explicit backend commands;
- tenant/security model is respected;
- large lists use server paging/filter/sort;
- list projections avoid frontend N+1/fan-out;
- provider-specific channel implementation does not leak into ordinary operator UX;
- screen hierarchy supports the end-to-end business process defined in `frontend-user-processes.md`;
- the screen/API contract is tracked by `frontend-screen-api-matrix.md`.
