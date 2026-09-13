# FrontendWeb — UI / User Process / API Review

Status: REVIEWED — CHANGES REQUIRED BEFORE DTO FREEZE  
Date: 2026-09-13  
Branch: `spec/post-slice10a-hardening-roadmap`

Reviewed documents:

- `frontend-ui-information-architecture.md`
- `frontend-user-processes.md`
- `frontend-screen-api-matrix.md`

## 1. Executive conclusion

The three documents form a sound product baseline, but they currently mix three different kinds of statements without marking the boundary clearly:

1. **FACTUAL** — already supported by the current backend API;
2. **DERIVABLE** — can be implemented by the frontend with the current API without unsafe fan-out or duplicated business logic;
3. **DESIRED** — good UX/product requirement, but current API does not yet expose enough data or a suitable command/query.

Before TypeScript DTO freeze and React implementation, every screen/action must be classified as `READY`, `PARTIAL`, or `BLOCKED` using the actual response shape, not only the existence of an endpoint.

The current documents must therefore remain analysis documents, not implementation contracts, until the blockers below are resolved.

## 2. Overall assessment

| Area | Assessment | Decision |
|---|---|---|
| Information architecture | strong | keep with corrections |
| Business processes | strong | keep with explicit API-gap markers |
| Screen-to-API mapping | incomplete as implementation gate | upgrade to readiness matrix |
| Tenant isolation model | correct | keep |
| Financial source-of-truth model | correct | keep |
| Workflow command model | correct | keep |
| Provider-neutral channel UX | correct | keep |
| Login/onboarding UX | blocker | backend/UX decision required |
| Customer list projection | partial | backend query enhancement or UI simplification |
| Collection list projection | partial | backend query enhancement or UI simplification |
| Admin UI contract | partial | full endpoint/DTO audit required |
| Dashboard contract | partial | exact response-shape audit required |
| Import/configuration UX | partial | source schema/mapping profile contract audit required |
| Template builder | partial | builder/preset/catalog contract audit required |

## 3. Critical finding F-01 — login requires tenantId

### Current state

`POST /api/v1/auth/login` requires:

```text
tenantId
email
password
```

The UX documents state that an ordinary tenant user should not need to type a tenant UUID after onboarding. That is the correct product requirement, but the current backend does not expose a tenant-discovery/login-by-slug contract in the reviewed public API.

### Risk

Without an explicit design decision, the React team will be forced into one of the following bad options:

- expose raw tenant UUID in login UI;
- hardcode tenant IDs;
- invent an undocumented discovery call;
- infer tenant from unsafe client-side data;
- use tenant information from a previous session only, which does not solve first login/new device.

### Required decision

Choose and specify one canonical login model before `FW1 Authentication`:

**Option A — tenant slug login**

```text
slug + email + password
```

Backend resolves slug to tenant internally.

**Option B — tenant discovery**

```text
email -> discover memberships/tenant choices -> login with selected tenant
```

This requires a privacy-safe discovery contract.

**Option C — tenant-specific entry URL**

```text
https://<tenant>.collectra.../login
```

Frontend resolves tenant context from trusted deployment/routing configuration rather than user-entered UUID.

### Review decision

`Login` is **BLOCKED for final frontend contract**, although registration itself is supported.

## 4. Critical finding F-02 — Customer list columns exceed current projection

### Current `CustomerPage` list item

The current list projection exposes:

```text
id
externalId
customerType
displayName
status
managerUserId
preferredLocale
timezone
segmentIds
createdAt
updatedAt
```

It does **not** expose:

```text
primaryEmail
primaryPhone
managerDisplayName
segmentNames
```

### Current UI document

The proposed Customers list includes:

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

### Risk

Implementing that table literally would encourage per-row calls for contacts/users/segments, creating N+1/network fan-out and violating the stated frontend architecture.

### Required decision

For MVP choose one of:

**Preferred:** extend `CustomerQueryService.CustomerListItem` with bounded display projection fields:

```text
primaryEmailMasked / primaryEmail
primaryPhoneMasked / primaryPhone
managerDisplayName
segment summaries [{id,name}]
```

or

**Minimal:** keep current backend and simplify table columns to fields already returned.

### Review decision

Customers list is **PARTIAL**, not fully READY.

## 5. High finding F-03 — Collection list UX exceeds list projection

Current `CollectionQueryService.CaseItem` exposes:

```text
id
customerId
invoiceId
status
priority
assignedTo
currency
outstandingAmount
paymentStatus
openedAt
closedAt
closeReason
version
```

This supports financial context correctly, but the proposed UI list also expects human-readable:

```text
Customer
Invoice
Assigned user
Next action / overdue indicator
```

The query does not currently provide display names/numbers or next-action projection.

### Risk

A React implementation may fan out to Customer, Invoice, membership and actions APIs for every row.

### Required decision

Prefer a collection work-queue projection that includes bounded presentation fields needed by the table, for example:

```text
customerDisplayName
invoiceNumber
assigneeDisplayName
nextActionType
nextActionDueAt
nextActionOverdue
```

If this is intentionally deferred, the MVP list must display IDs only or omit those columns.

### Review decision

Collections list is **PARTIAL**.

## 6. High finding F-04 — screen matrix tracks endpoints, not screen readiness

The current `frontend-screen-api-matrix.md` answers mostly:

> Does an endpoint exist?

It must instead answer:

> Can this screen be implemented correctly and efficiently from the available contract?

The matrix needs the following columns:

```text
Screen / action
API
Permission
Request contract
Response contract
Pagination
Concurrency/version
Readiness: READY | PARTIAL | BLOCKED
Gap / required backend change
```

This becomes the actual gate for DTO generation.

## 7. High finding F-05 — permissions/navigation model needs exact authority mapping

The documents correctly state that UI visibility follows authorities rather than hard-coded role names.

However, only some capabilities currently have explicit permissions in the reviewed controllers, e.g. campaigns/templates, while several tenant business controllers are guarded broadly by `ROLE_HUMAN`.

Before implementing sidebar/action guards, create a factual permission matrix:

```text
route
page capability
read authority
manage authority
special command authority
fallback behavior
```

Do not invent frontend permission constants that the backend does not issue.

## 8. High finding F-06 — Administration is not yet DTO-ready

The identity module exposes membership, invitation, role, permission and account-lifecycle controllers, but the current three frontend documents intentionally stop before listing their exact paths and DTOs.

That means `Administration` belongs in the information architecture, but is **PARTIAL** for implementation until the concrete endpoint audit is completed.

Platform administration must remain a distinct route tree and auth context.

## 9. Medium finding F-07 — Dashboard cards must follow actual response fields

The dashboard information architecture currently names desirable KPIs such as:

```text
customers
outstanding receivables
overdue receivables
active collection cases
retry/failed delivery
priority workload
```

The presence of four dashboard endpoints is confirmed, but exact DTO fields still need to be frozen.

Rule: do not design card labels/charts beyond fields actually returned by `DashboardQueryService` unless a backend read-model change is explicitly added.

Dashboard stays **PARTIAL until exact DTO audit**.

## 10. Medium finding F-08 — Contract navigation decision should remain configurable

The UI architecture currently makes Contracts secondary rather than a mandatory top-level area. This is a good default, but it should not become a hard product invariant.

Recommendation:

```text
MVP: Customer -> Contracts is primary navigation path
Optional route: /contracts available for global search/list
Sidebar visibility: configurable/product decision
```

The backend already supports a global paged contract list, so hiding it permanently would unnecessarily constrain future enterprise usage.

## 11. Medium finding F-09 — import process must distinguish ingestion from configuration

The import process currently combines:

```text
source schema
mapping profile
template version
batch upload
```

For user experience these are two different capabilities:

### Import configuration

Admin/data-manager task:

```text
source schema
mapping profile/version
field mapping
validation configuration
```

### Import execution

Operator task:

```text
choose approved profile
choose template/output
upload payload
submit
monitor result
```

These should be separate routes/features and may have different permissions.

## 12. Medium finding F-10 — Templates need authoring state contract before editor implementation

The current template process is directionally correct:

```text
create template
create version
edit
validate
preview
publish
reopen/archive
```

Before React editor work, audit exact contracts for:

- field catalogue;
- template builder;
- presets;
- placeholder namespace;
- compatible channel/locale rules;
- version transition semantics;
- preview payload constraints.

Do not hardcode placeholder trees from documentation when an authoritative field catalogue API exists.

## 13. Medium finding F-11 — deep links need label/source strategy

The proposed cross-navigation is correct, but a detail response often returns identifiers rather than fully hydrated labels.

Before React routing, define which pages obtain display names through:

1. the primary response projection;
2. one bounded parallel detail query;
3. a dedicated lightweight lookup API.

Forbidden pattern:

```text
list 50 rows
-> fetch customer 50 times
-> fetch contract 50 times
-> fetch assignee 50 times
```

## 14. Medium finding F-12 — frontend command idempotency policy is correct but needs lifecycle rules

The proposed allocation behavior is correct: one `commandId` per user intent, reused for safe retry of the same intent.

The React contract must additionally define:

- command ID created on confirmation, not page render;
- preserved while request result is unknown;
- discarded after authoritative success;
- a new edited allocation intent receives a new command ID;
- browser retry mechanisms must not silently generate a new ID for the same uncertain submission.

Apply the same principle to import `Idempotency-Key`.

## 15. Medium finding F-13 — frontend-visible async state needs polling policy

Campaign runs, messages, imports and document generation are asynchronous.

The user-process document identifies the workflows but does not yet specify a frontend observation policy.

The React contract phase must define bounded polling/refetch rules, e.g.:

```text
active import batch -> poll while non-terminal
active campaign run -> poll summary/list at bounded interval
message detail -> refetch while transient
background tab -> reduce/stop polling
terminal state -> stop polling
```

Do not introduce WebSocket/SSE unless backend support is explicitly implemented.

## 16. Medium finding F-14 — route and URL-state contract should be specified before components

The IA says filters should live in URL state where practical. This should become normative for paged operational lists.

Recommended canonical routes:

```text
/dashboard
/customers
/customers/:customerId
/contracts
/contracts/:contractId
/receivables/invoices
/receivables/invoices/:invoiceId
/receivables/payments
/receivables/payments/:paymentId
/collections
/collections/:caseId
/templates
/templates/:templateId
/campaigns
/campaigns/:campaignId
/campaigns/:campaignId/runs/:runId
/imports
/imports/:batchId
/files/:fileId
/admin/...
/profile
/profile/security
```

Query filters, page and sort should be URL-serializable using backend parameter names where practical.

## 17. Confirmed strong decisions

The following decisions should not be weakened during DTO/React implementation:

### A. Backend authoritative business state

Frontend must not recompute:

- payment state;
- outstanding amount;
- authoritative overdue state;
- allocation validity;
- collection transition validity;
- campaign eligibility;
- message retry/send eligibility.

### B. Explicit commands instead of generic status editing

Use backend lifecycle commands for contracts, collection cases, promises, disputes, actions, campaign activation and template publication.

### C. Tenant comes from authenticated context

Tenant-facing business requests must not add arbitrary tenant ID selectors/headers invented by frontend.

### D. Provider-neutral delivery UI

Mock EMAIL/SMS/WhatsApp/Telegram/In-App and future real providers use the same message/delivery UI.

### E. Server-side list operations

Paging, filtering and sorting stay server-side for unbounded collections.

### F. Optimistic concurrency

When responses expose `version`, React stores and sends it for commands that require it; `VERSION_CONFLICT` causes authoritative reload.

## 18. Required document changes before DTO phase

### `frontend-ui-information-architecture.md`

Change:

1. mark each major screen as `READY`, `PARTIAL`, or `BLOCKED`;
2. remove/mark unsupported list columns rather than implying they are currently available;
3. add canonical route map;
4. separate Import Configuration from Import Execution;
5. state that display labels must come from bounded projection, not row-by-row hydration;
6. mark Administration/Dashboard/Template Builder as pending exact DTO audit.

### `frontend-user-processes.md`

Change:

1. mark P2 login as blocked until tenant resolution UX/API is defined;
2. add explicit first-login/new-device tenant resolution process;
3. distinguish configuration roles from import execution;
4. add async observation/polling process;
5. add command-idempotency lifecycle rules;
6. add empty/loading/stale/conflict recovery behavior for long-running processes.

### `frontend-screen-api-matrix.md`

Change:

1. add readiness/status column;
2. add permissions;
3. add response sufficiency assessment;
4. add exact CustomerSegment endpoints now known;
5. flag Customer list projection gap;
6. flag Collection work-queue projection gap;
7. flag login tenant-resolution gap;
8. expand admin/source-schema/mapping/template-builder contracts before marking them READY;
9. include exact `ProblemDetail` and page/slice contract audit as mandatory shared rows.

## 19. Recommended readiness classification now

| Feature | Readiness |
|---|---|
| Tenant registration | READY |
| Login | BLOCKED — tenant resolution UX/API |
| Refresh/logout/session | READY/PARTIAL pending token-storage decision |
| Profile | READY |
| Dashboard | PARTIAL — exact response shape audit |
| Customers detail/write | READY |
| Customers list | PARTIAL — display projection gap |
| Customer contacts | READY |
| Customer segments | READY for CRUD/membership; DTO audit now straightforward |
| Contracts | READY |
| Invoices | READY |
| Payments | READY |
| Allocations/reversal | READY |
| Collection case detail/workflow | READY |
| Collection work queue | PARTIAL — labels/next-action projection |
| Templates basic management | READY |
| Template builder/catalog/presets | PARTIAL |
| Campaign basic workflow | READY |
| Campaign selection wizard | PARTIAL until exact `CampaignSelection` audit |
| Messages | READY for read/monitoring, exact DTO audit required |
| Import batch submit/result | READY |
| Import configuration | PARTIAL |
| Files | READY, exact enum/metadata audit required |
| Tenant administration | PARTIAL |
| Platform administration | separate future frontend route tree |

## 20. Gate before `frontend-react-api-contract.md`

Do not freeze TypeScript DTOs until the following are resolved or explicitly accepted as deferred:

```text
G-UI-01 tenant resolution/login contract
G-UI-02 Customer list projection decision
G-UI-03 Collection work-queue projection decision
G-UI-04 Dashboard exact DTOs
G-UI-05 Administration exact endpoints/DTOs
G-UI-06 SourceSchema/MappingProfile exact endpoints/DTOs
G-UI-07 Template builder/catalog/preset exact endpoints/DTOs
G-UI-08 CampaignSelection exact contract
G-UI-09 MessageSlice/MessageDetail exact contract
G-UI-10 shared Page/Slice/ProblemDetail contract
```

After those checks, `frontend-react-api-contract.md` can be generated without inventing frontend-only business state or introducing accidental N+1 network behavior.
