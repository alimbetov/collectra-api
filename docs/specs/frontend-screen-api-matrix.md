# FrontendWeb — Screen to API Matrix

Status: FRONTEND CONTRACT READY / DTO FREEZE NEXT

Purpose: связать UX с фактическим backend API до проектирования TypeScript DTO и React query/mutation layer.

## Readiness legend

- `READY` — текущий API достаточен для проектирования React DTO/hooks.
- `READY+` — API дополнительно усилен в этой ветке под frontend projection.
- `LATER` — сознательно не блокирует первый frontend release.

## Authentication / bootstrap

| Screen / action | API | Status |
|---|---|---|
| Tenant registration | `POST /api/v1/auth/tenants/register` | READY |
| Frontend login by tenant slug | `POST /api/v1/auth/login/by-slug` | READY+ |
| Legacy login by tenant UUID | `POST /api/v1/auth/login` | READY / compatibility |
| Refresh | `POST /api/v1/auth/refresh` | READY |
| Logout | `POST /api/v1/auth/logout` | READY |
| Logout all | `POST /api/v1/auth/logout-all` | READY |
| Bootstrap current user | `GET /api/v1/identity/me` | READY |
| Edit profile | `PATCH /api/v1/identity/me` | READY |
| Change password | `POST /api/v1/identity/me/change-password` | READY |
| Sessions | `GET /api/v1/identity/me/sessions` | READY |
| Revoke session | `DELETE /api/v1/identity/me/sessions/{id}` | READY |

Frontend MUST use slug login so ordinary users never type or store a tenant UUID before authentication.

## Dashboard

| Screen | API | Status |
|---|---|---|
| Dashboard shell/KPIs | `GET /api/v1/dashboard/summary` | READY |
| Receivables card | `GET /api/v1/dashboard/receivables` | READY |
| Delivery card | `GET /api/v1/dashboard/delivery` | READY |
| Collections card | `GET /api/v1/dashboard/collections` | READY |

Dashboard read models expose `asOf` / `businessDate`; React may aggregate already projected buckets for presentation but MUST NOT recalculate authoritative invoice/payment state.

## Customers

| Screen / action | API | Status |
|---|---|---|
| Customer list/search/filter | `GET /api/v1/customers` | READY+ |
| Customer detail | `GET /api/v1/customers/{id}` | READY |
| Create customer | `POST /api/v1/customers` | READY |
| Edit profile | `PUT /api/v1/customers/{id}` | READY |
| Change status | `PATCH /api/v1/customers/{id}/status` | READY |
| Email list/add/update | `/api/v1/customers/{id}/emails...` | READY |
| Phone list/add/update | `/api/v1/customers/{id}/phones...` | READY |
| Segment membership | `/api/v1/customers/{id}/segments/{segmentId}` | READY |
| Segment list/detail/create/update | `/api/v1/customer-segments...` | READY |

`CustomerListItem` is now a screen-oriented projection and includes:

```text
id / externalId / customerType / displayName / status
managerUserId / managerDisplayName
primaryEmail / primaryPhone
segmentIds / segments[{id,code,name}]
preferredLocale / timezone
createdAt / updatedAt
```

The list MUST NOT trigger per-row requests for contacts, managers or segment labels.

## Contracts

| Screen / action | API | Status |
|---|---|---|
| Contract list | `GET /api/v1/contracts` | READY |
| Contract detail | `GET /api/v1/contracts/{id}` | READY |
| Create / edit | `POST /api/v1/contracts`, `PUT /api/v1/contracts/{id}` | READY |
| Suspend / activate / close / cancel | explicit command endpoints | READY |

## Receivables

### Invoices

| Screen / action | API | Status |
|---|---|---|
| Invoice list/filter | `GET /api/v1/invoices` | READY |
| Invoice detail | `GET /api/v1/invoices/{id}` | READY |
| Create invoice | `POST /api/v1/invoices` | READY |
| Invoice allocations | `GET /api/v1/invoices/{id}/allocations` | READY |

Invoice response owns `paidAmount`, `outstandingAmount`, `paymentStatus`, `overdue`, `daysOverdue`, and `businessDate`.

### Payments

| Screen / action | API | Status |
|---|---|---|
| Payment list/filter | `GET /api/v1/payments` | READY |
| Payment detail | `GET /api/v1/payments/{id}` | READY |
| Create payment | `POST /api/v1/payments` | READY |
| Payment allocations | `GET /api/v1/payments/{id}/allocations` | READY |
| Allocate | `POST /api/v1/payments/{id}/allocations` | READY |
| Reverse allocation | `POST /api/v1/payments/{paymentId}/allocations/{allocationId}/reverse` | READY |

React reuses one `commandId` for replay of the same allocation intent.

## Collections

| Screen / action | API | Status |
|---|---|---|
| Collection work queue | `GET /api/v1/collection-cases` | READY+ |
| Case detail | `GET /api/v1/collection-cases/{caseId}` | READY |
| Create/update/start/hold/close | explicit case command endpoints | READY |
| Promises | `/promises...` | READY |
| Disputes | `/disputes...` | READY |
| Actions | `/actions...` | READY |
| Timeline | `GET /api/v1/collection-cases/{caseId}/timeline` | READY |

`CaseItem` is now a work-queue projection and includes:

```text
customerId / customerDisplayName
invoiceId / invoiceNumber
currency / outstandingAmount / paymentStatus
status / priority
assignedTo / assigneeDisplayName
nextActionType / nextActionDueAt / nextActionOverdue
openedAt / closedAt / closeReason / version
```

Customer/invoice/assignee/next-action data is resolved server-side in bounded batches.

## Templates / builder

| Screen / action | API | Status |
|---|---|---|
| Template management | `/api/v1/templates...` | READY |
| Builder catalogue | `GET /api/v1/template-builder/catalog` | READY |
| Draft validate/preview | `POST /api/v1/template-builder/validate|preview` | READY |
| Structured builder validate/preview | `/template-builder/documents/...` | READY |
| Save/update builder draft | `/template-builder/templates/{id}/versions...` | READY |
| Builder version read/publish | `/template-builder/versions/{id}...` | READY |
| Assets | `/api/v1/template-builder/assets...` | READY |

Builder catalogue already exposes field definitions, assets, available channels and builder syntax/schema version.

## Campaigns

| Screen / action | API | Status |
|---|---|---|
| Campaign list/detail | `GET /api/v1/campaigns...` | READY |
| Create | `POST /api/v1/campaigns` | READY |
| Activate | `POST /api/v1/campaigns/{campaignId}/activate` | READY |
| Prepare run | `POST /api/v1/campaigns/{campaignId}/runs` | READY |
| Runs / recipients | nested GET endpoints | READY |
| Eligibility recheck | `POST /api/v1/campaigns/runs/{runId}/eligibility-recheck` | READY |

Exact `CampaignSelection` contract:

```text
customerIds: Set<UUID>
segmentIds: Set<UUID>
daysOverdueFrom: Integer?
daysOverdueTo: Integer?
amountFrom: BigDecimal?
amountTo: BigDecimal?
```

## Messages / delivery monitoring

| Screen / action | API | Status |
|---|---|---|
| Run message list | `GET /api/v1/campaigns/{campaignId}/runs/{runId}/messages` | READY |
| Message detail | `GET /api/v1/campaigns/{campaignId}/runs/{runId}/messages/{messageId}` | READY |

`MessageListItem` exposes id/run/customer/channel/masked destination/status/attempt/retry/sent/created data. `MessageDetail` additionally exposes invoice/template/locale/providerMessageId, normalized safe error code/summary and attachment states. Provider credentials/raw bodies remain hidden.

## Imports — execution

| Screen / action | API | Status |
|---|---|---|
| Upload tabular file | `POST /api/v1/import-batches` multipart | READY |
| Submit JSON | `POST /api/v1/import-batches/json` | READY |
| Submit XML | `POST /api/v1/import-batches/xml` | READY |
| Batch result | `GET /api/v1/import-batches/{batchId}` | READY |

All create operations use `Idempotency-Key`.

## Imports — configuration

### Source schemas

`/api/v1/source-schemas` supports definition CRUD, version creation, fields, row configuration, validation and publish/reopen/archive transitions.

### Mapping profiles

`/api/v1/mapping-profiles` supports definitions, versions, mapping rules, validation, file test, single-rule test and publish/reopen/archive transitions.

Status: **READY** for Data Manager / configuration UI.

## Files

| Screen / action | API | Status |
|---|---|---|
| Upload | `POST /api/v1/files` multipart | READY |
| Metadata | `GET /api/v1/files/{fileId}` | READY |
| Download content | `GET /api/v1/files/{fileId}/content` | READY |
| Presigned download | `GET /api/v1/files/{fileId}/download-url` | READY |
| Delete | `DELETE /api/v1/files/{fileId}` | READY |

## Administration

| Screen / action | API | Status |
|---|---|---|
| Member list | `GET /api/v1/identity/users` | READY+ |
| Member current roles | `GET /api/v1/identity/memberships/{id}/roles` | READY+ |
| Assign roles | `PUT /api/v1/identity/memberships/{id}/roles` | READY |
| Block/activate member | `PATCH /api/v1/identity/memberships/{id}/status` | READY |
| Member sessions | `/memberships/{id}/sessions...` | READY |
| Invitations | `/api/v1/identity/invitations...` | READY |
| Roles | `/api/v1/identity/roles...` | READY |
| Permissions | `GET /api/v1/identity/permissions` | READY |

Member list now exposes `membershipId`, `userId`, `email`, `displayName`, `status`. Role editing loads current role ids only when the selected member is opened.

Platform administrator endpoints remain a separate route tree and are not part of tenant frontend MVP.

## Common transport contract

### Pagination

Frontend must support both current shapes:

```text
Page-like:
items | content
page
size
totalElements? 
totalPages?
hasNext
```

Feature adapters normalize these shapes; generic UI code must not depend on Spring `Page` internals.

### ProblemDetail

Current error contract is RFC-7807/Spring `ProblemDetail` plus stable extensions:

```text
type
title
status
detail
instance
code
traceId
correlationId
errors?        // validation map
batchId?       // import failure
```

401/403/404/409 behavior remains defined in `frontend-user-processes.md`.

## Blocker closure

The previous pre-DTO audit items are now resolved:

| ID | Item | Result |
|---|---|---|
| G-UI-01 | tenant resolution/login | CLOSED — slug login added |
| G-UI-02 | Customer list projection | CLOSED — enriched bounded projection |
| G-UI-03 | Collection work queue | CLOSED — enriched bounded projection |
| G-UI-04 | Dashboard DTO | CLOSED — exact read models audited |
| G-UI-05 | Administration API | CLOSED — member projection + role read added |
| G-UI-06 | SourceSchema/MappingProfile | CLOSED — exact APIs audited |
| G-UI-07 | Template builder/catalog/presets | CLOSED for first frontend scope |
| G-UI-08 | CampaignSelection | CLOSED — exact shape fixed |
| G-UI-09 | MessageSlice/MessageDetail | CLOSED — exact shape audited |
| G-UI-10 | Page/Slice/ProblemDetail | CLOSED — transport normalization rule fixed |

Next artifact: `frontend-react-api-contract.md` with exact TypeScript DTOs, API functions, TanStack Query keys and invalidation graph.
