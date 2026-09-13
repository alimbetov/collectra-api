# FrontendWeb — UI Information Architecture

Status: PROPOSED PRODUCT/UI BASELINE

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

## 4. Application shell

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
│   ├── overdue amount
│   └── aging/state summary
├── Delivery card
│   ├── queued/processing
│   ├── sent
│   ├── retry
│   └── failed
└── Collections card
    ├── open/in progress/on hold
    ├── overdue promises/actions
    └── priority workload
```

Dashboard is a navigation surface, not a second source of truth. Clicking KPI/card opens the corresponding filtered list.

## 6. Customers

Backend surface already supports paged list, detail, create/update, status, contacts and segments.

### Customers list

Columns:

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

Use tabbed workspace:

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
- high-level receivable counters if available from existing query surfaces.

Contacts:

- email list;
- phone list;
- add;
- set primary;
- activate/deactivate.

Do not edit email/phone value in place if backend contract models replacement as deactivate + add.

Contracts tab:

- server-filtered `customerId` contracts;
- create/open contract;
- lifecycle actions according to backend status/version.

Invoices/Payments tabs use corresponding backend filters by `customerId`.

Collections tab uses collection cases filtered by `customerId`.

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

Lifecycle actions must use current `version`; stale update maps to `409 VERSION_CONFLICT` and UI reload prompt.

## 8. Receivables

### Invoices list

Columns:

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

Row emphasis:

- overdue invoices visually distinct;
- paid/cancelled de-emphasized;
- amount formatting based on currency.

### Invoice detail

```text
Invoice header
├── customer / contract links
├── financial summary
├── dates/status
├── linked document
├── allocations
└── collections
```

Frontend never recalculates authoritative `paidAmount`, `outstandingAmount`, `paymentStatus`. It displays backend values.

### Payments list/detail

Columns:

```text
Date
Customer
Reference
Amount
Currency
Source
Allocation state (derived from backend/query support only)
```

Payment detail:

- payment facts;
- allocations;
- allocate action;
- reverse allocation action.

Allocation modal:

```text
Select invoice
Amount
Confirm
```

Frontend generates one UUID `commandId` per user allocation intent and reuses it on retry of the same user action.

## 9. Collections

This is the main operational workspace for collection officers.

### Collection cases list

Columns:

```text
Customer
Invoice
Status
Priority
Assigned user
Opened
Outstanding context
Next action / overdue indicator
```

Filters:

```text
customer
invoice
status
priority
assignedTo
```

### Collection case detail

Single workspace with strongly visible financial context:

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

Templates are a dedicated authoring workspace.

### Template list

Columns:

```text
Name
Code
Document type
Channel
Locale/version summary
Status
Updated
```

### Template detail/editor

```text
Template
├── metadata
├── Versions
│   ├── locale
│   ├── channel
│   ├── subject
│   ├── content HTML
│   ├── stylesheet
│   └── status
├── Placeholder / field catalogue
├── Validate
├── Preview
└── Publish / reopen / archive
```

Recommended editor layout:

```text
+-----------------------+-------------------------------+
| Fields/placeholders   | Editor                        |
| {{customer.*}}        | subject                       |
| {{invoice.*}}         | HTML/content                  |
| {{custom.*}}          | stylesheet                    |
+-----------------------+-------------------------------+
| Preview                                                |
+--------------------------------------------------------+
```

Channel mocks do not change this UX: template/version/channel model stays provider-neutral.

## 11. Campaigns

Backend supports campaign create/list/detail, activate, prepare run, list runs/recipients and eligibility recheck.

### Campaign list

Columns:

```text
Name
Channel
Template
Status
Scheduled at
Last run summary
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

Recommended UX:

```text
Step 1 — Basics
  name
  channel

Step 2 — Audience
  CampaignSelection
  segment/customer/receivable criteria according to backend selection model

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

Message delivery status must remain provider-neutral. Mock and real providers render through the same UI.

## 12. Message / delivery monitoring

Nested under campaign run:

```text
Campaign -> Run -> Messages
```

Columns:

```text
Recipient/customer
Channel
Masked destination
Status
Attempt count
Next retry
Last error summary
Updated
```

Filters:

```text
status
channel
customer
```

Message detail should show:

- immutable materialized destination/content metadata appropriate for operator access;
- status/history;
- attempts/retry timing;
- attachment state;
- safe error summary;
- correlation identifiers where exposed;
- no secrets/raw provider auth.

## 13. Imports

Import UX is a guided process, not a raw upload button.

```text
1 Select source/mapping profile
2 Select template/version and output formats
3 Upload file or choose JSON/XML mode
4 Validate/upload
5 Show accepted batch
6 Poll/view batch result
7 Show errors/result documents
```

Current backend supports multipart, JSON and XML import entry points with `Idempotency-Key`; source schemas and mapping profiles have dedicated APIs.

The frontend must generate a stable `Idempotency-Key` for one import attempt and reuse it only for safe replay of the same intent.

## 14. Files

Files are primarily contextual resources rather than a standalone document management product.

Supported UI actions:

- upload;
- show metadata;
- open/download;
- request presigned download URL;
- delete when permission and domain rules allow.

File picker should be reusable from invoices, templates/assets, attachments and imports where relevant.

## 15. Administration

Administration is permission-driven.

Areas based on current identity API:

```text
Members
Invitations
Roles
Permissions
Account lifecycle
```

Navigation item is hidden if user cannot access administration surfaces. Backend remains authoritative; hiding UI is not an authorization control.

## 16. Profile & security

Current user API supports:

```text
GET/PATCH /api/v1/identity/me
POST /api/v1/identity/me/change-password
GET /api/v1/identity/me/sessions
DELETE /api/v1/identity/me/sessions/{id}
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

All enum/status rendering must use a centralized frontend dictionary per domain. Unknown backend enum value must render as `Unknown (<raw>)`, not crash the page.

### Optimistic concurrency

Entities exposing `version` keep it in frontend state. Mutation sends current version. `409 VERSION_CONFLICT` => inform user and reload authoritative state.

### Destructive/business-significant actions

Close/cancel/reverse/archive/publish must use confirmation dialogs and show business consequence, not only technical action name.

### Filters

Filters live in URL query state where practical so screens are bookmarkable/shareable. No client-side filtering of unbounded lists.

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
FW-UX7 Templates
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
- provider-specific channel implementation does not leak into ordinary operator UX;
- screen hierarchy supports the end-to-end business process defined in `frontend-user-processes.md`.
