# FrontendWeb — Layered Implementation Plan

Status: SPECIFICATION / IMPLEMENTATION ROADMAP

Purpose: зафиксировать целевую архитектуру React frontend, правила зависимостей между слоями, последовательность frontend work slices и обязательные quality gates. Документ не дублирует screen/API contracts; он определяет, **как** frontend должен быть реализован поверх уже согласованных backend contracts.

This document is downstream from:

- `frontend-ui-information-architecture.md`
- `frontend-user-processes.md`
- `frontend-screen-api-matrix.md`
- `frontend-ui-process-api-review-2026-09-13.md`
- `frontend-blocker-closure-2026-09-13.md`
- `frontend-react-api-contract.md`

Authoritative rule: backend remains the source of business truth. React is a projection, interaction and client-orchestration layer. Frontend MUST NOT become a second domain engine.

---

## 1. Goals

The frontend architecture MUST:

1. keep transport, domain projection, user action and page composition concerns separated;
2. allow feature slices to evolve without creating one global `api.ts`, one global store or page components with embedded business rules;
3. preserve tenant isolation and authenticated-session boundaries;
4. use backend-provided financial and workflow state instead of recomputing it in JavaScript;
5. make loading, error, permission and empty states first-class implementation concerns;
6. provide deterministic query invalidation and bounded polling;
7. remain testable at transport, feature and page-composition levels;
8. keep implementation compatible with the frozen DTO/API contracts.

---

## 2. Architectural model

Use a pragmatic layered structure inspired by Feature-Sliced Design, without requiring unnecessary ceremony.

```text
frontendweb/src/
├── app/
├── pages/
├── widgets/
├── features/
├── entities/
└── shared/
```

Allowed dependency direction:

```text
app
 ↓
pages
 ↓
widgets
 ↓
features
 ↓
entities
 ↓
shared
```

A lower layer MUST NOT import from a higher layer.

Examples:

- `shared` MUST NOT import `customer`, `campaign`, `page` or `widget` code.
- `entities/customer` MUST NOT import `pages/customers`.
- `features/campaign-create` MAY import campaign entities and shared infrastructure.
- pages MAY compose widgets/features/entities but SHOULD contain minimal behavior.

Circular dependencies across slices are prohibited.

---

## 3. Target source tree

```text
frontendweb/src/
├── app/
│   ├── providers/
│   ├── router/
│   ├── query-client/
│   ├── error-boundary/
│   └── App.tsx
│
├── pages/
│   ├── auth/
│   ├── dashboard/
│   ├── customers/
│   ├── receivables/
│   ├── collections/
│   ├── campaigns/
│   ├── messages/
│   ├── templates/
│   ├── imports/
│   ├── files/
│   └── admin/
│
├── widgets/
│   ├── app-shell/
│   ├── sidebar/
│   ├── topbar/
│   ├── page-header/
│   └── data-table/
│
├── features/
│   ├── auth/
│   ├── customer-search/
│   ├── customer-filter/
│   ├── invoice-filter/
│   ├── collection-action/
│   ├── campaign-create/
│   ├── campaign-run/
│   ├── message-retry/
│   └── file-upload/
│
├── entities/
│   ├── user/
│   ├── customer/
│   ├── contract/
│   ├── invoice/
│   ├── payment/
│   ├── collection-case/
│   ├── campaign/
│   ├── message/
│   └── import-batch/
│
└── shared/
    ├── api/
    ├── auth/
    ├── config/
    ├── lib/
    ├── hooks/
    ├── ui/
    ├── types/
    └── utils/
```

The exact folder count may evolve, but layer responsibilities and dependency direction are contractual.

---

## 4. Layer responsibilities

### 4.1 `shared`

`shared` contains domain-agnostic infrastructure.

Expected areas:

```text
shared/api/
├── http-client.ts
├── api-error.ts
├── contracts.ts
├── request.ts
└── query-keys.ts

shared/auth/
├── token-storage.ts
├── session-store.ts
├── auth-events.ts
└── auth-types.ts
```

Responsibilities:

- HTTP transport;
- JSON serialization/deserialization;
- `Authorization: Bearer` injection;
- RFC7807 / Spring `ProblemDetail` handling;
- `traceId` / `correlationId` preservation;
- FormData support;
- common page/slice contracts;
- session token storage primitives;
- cross-cutting UI primitives.

`shared/api` MUST NOT expose domain methods such as `getCustomers()` or `createCampaign()`.

---

### 4.2 `entities`

An entity represents a frontend projection of a backend business entity.

Typical entity slice:

```text
entities/customer/
├── api/
│   ├── customer.api.ts
│   └── customer.queries.ts
├── model/
│   ├── customer.types.ts
│   └── customer.keys.ts
└── ui/
    ├── CustomerName.tsx
    └── CustomerStatusBadge.tsx
```

Entity API adapters MAY know backend endpoint paths and frozen DTOs.

Entity query hooks SHOULD own stable TanStack Query keys and basic retrieval policies.

Entity UI MUST remain small and reusable; page workflows do not belong here.

---

### 4.3 `features`

A feature models a user action or business interaction, not a noun.

Examples:

- login/logout;
- customer search/filter;
- assign collection case;
- create campaign;
- prepare/run campaign;
- retry message;
- upload import file.

Typical feature structure:

```text
features/auth/
├── api/
├── model/
├── hooks/
├── ui/
└── index.ts
```

Features MAY compose entities and shared infrastructure.

Business truth remains backend-authoritative. A feature may orchestrate requests and UI state, but MUST NOT reimplement server-side eligibility, money, state machine or tenant rules.

---

### 4.4 `widgets`

Widgets are larger UI compositions used by one or more pages.

Examples:

- `CustomerTable`;
- `CollectionWorkQueue`;
- `CampaignRunSummary`;
- `DashboardReceivablesCard`;
- `AppSidebar`;
- `Topbar`.

Widgets MAY compose entities and features. They SHOULD NOT own navigation-level routing policy.

---

### 4.5 `pages`

Pages are route-level composition shells.

Pages SHOULD be thin.

Preferred shape:

```tsx
export function CustomersPage() {
  return (
    <PageLayout title="Customers">
      <CustomerFilters />
      <CustomerTable />
    </PageLayout>
  );
}
```

Avoid page components containing transport calls, permission parsing, pagination state, mutation invalidation and domain calculations inline.

---

### 4.6 `app`

`app` owns global application wiring:

- React Router;
- QueryClient provider;
- auth bootstrap;
- route protection;
- global error boundary;
- app shell;
- global providers.

No domain-specific receivable, campaign or collection logic belongs in this layer.

---

## 5. State management policy

Do not introduce Redux by default.

Use the smallest appropriate state mechanism:

```text
Server state       -> TanStack Query
Route/filter state -> React Router / URLSearchParams
Auth/session state -> focused auth/session store
Local UI state     -> useState / useReducer
Forms              -> React Hook Form when form complexity justifies it
```

### 5.1 Filters in URL

Operational list filters SHOULD be represented in URL search params where practical.

Example:

```text
/customers?page=2&status=ACTIVE&segment=VIP
```

This preserves:

- browser back/forward;
- page refresh;
- shareable links;
- predictable query keys;
- reproducible operational views.

---

## 6. Transport and API rules

### 6.1 Shared HTTP boundary

All requests MUST pass through the shared HTTP boundary.

Entity/feature code MUST NOT call bare `fetch()` directly.

### 6.2 Errors

Use the frozen `ProblemDetailDto` contract.

Required behavior:

- `400`: render validation/problem details;
- `401`: perform at most one coordinated refresh and one replay;
- `403`: do not refresh; show forbidden state;
- `404`: safe not-found handling;
- `409`: reload authoritative state where required;
- `5xx`: generic operational error with `traceId` / `correlationId` where available.

### 6.3 Financial values

Current backend `BigDecimal` wire representation is JSON number, mapped to TypeScript `number` by the frozen API contract.

Frontend MUST NOT use JavaScript floating-point arithmetic to derive authoritative monetary state.

Examples that remain backend-authoritative:

- `outstandingAmount`;
- `paidAmount`;
- payment status;
- allocation validity;
- invoice eligibility;
- campaign eligibility based on financial state.

### 6.4 Tenant boundary

Tenant identity is derived from authenticated context after login. Normal operational API calls MUST NOT treat user-entered tenant UUID as authoritative tenant scope.

### 6.5 Sensitive provider data

Frontend MUST NOT expose provider credentials, secrets or raw provider payloads unless an explicit backend contract designed for operational inspection exists.

---

## 7. TanStack Query policy

### 7.1 Query keys

Query keys MUST be stable, feature/domain-specific and include all server-query-affecting filters.

Example pattern:

```ts
customerKeys.all
customerKeys.list(filters)
customerKeys.detail(customerId)
```

### 7.2 Invalidation

Normal mutations MUST invalidate only affected query families.

Do not globally clear QueryClient after every mutation.

Global/session-bound cache clearing is appropriate for:

- logout;
- refresh-session failure;
- explicit tenant/session boundary change.

### 7.3 Polling

Polling is allowed only for non-terminal asynchronous resources.

Rules:

- bounded interval;
- stop on terminal state;
- stop on component disposal;
- avoid permanent unmanaged `setInterval` loops;
- prefer TanStack Query refetch policy.

---

## 8. Authentication/session architecture

FW1 is the first active implementation slice after foundation.

Required flow:

```text
tenantSlug + email + password
            ↓
POST /api/v1/auth/login/by-slug
            ↓
         tokens
            ↓
GET /api/v1/identity/me
            ↓
user + roles + permissions
            ↓
     protected application
```

Refresh flow:

```text
API request
   ↓
401
   ↓
single-flight refresh coordinator
   ↓
new access token
   ↓
replay original request once
```

If refresh fails:

```text
clear tokens/session
clear tenant-bound query cache
redirect to /login
```

Requirements:

- concurrent `401` responses MUST share one refresh request;
- login/refresh/logout calls MUST be excluded from recursive refresh interception;
- only one replay is permitted;
- `403` MUST NOT trigger refresh;
- `MeDto.permissions` is the primary frontend capability projection;
- backend authorization remains authoritative.

---

## 9. Frontend work slices

### FW0 — Foundation

Status: CLOSED.

Delivered:

- React;
- Vite;
- TypeScript strict mode;
- React Router;
- TanStack Query;
- shared HTTP client skeleton;
- app shell;
- frozen React API contract;
- real npm lockfile;
- successful `npm run typecheck`;
- successful production `npm run build`.

No further business behavior should be added to FW0.

---

### FW1 — Authentication & Session

Status: NEXT / IN PROGRESS.

Scope:

1. verify exact backend `login`, `refresh`, `logout`, `me` request/response contracts;
2. add auth/current-user DTOs;
3. implement token/session storage;
4. add bearer injection;
5. add single-flight refresh coordinator;
6. replay one failed request after successful refresh;
7. bootstrap `GET /api/v1/identity/me`;
8. implement login page/form;
9. implement protected routes;
10. implement permission guard;
11. implement logout/session-loss handling;
12. add focused auth/session tests;
13. typecheck/build/test before PR.

Exit gate:

- successful login via tenant slug;
- current user loaded;
- protected routes inaccessible while unauthenticated;
- one-refresh behavior proven under concurrent 401s;
- refresh failure clears session and tenant cache;
- no refresh loop;
- `npm run typecheck` green;
- `npm run build` green.

---

### FW2 — Application Shell + RBAC Projection

Scope:

- authenticated sidebar/topbar;
- current user projection;
- logout action;
- permission-driven navigation visibility;
- route-level permission guards;
- 403 page;
- 404 page;
- global error boundary.

Rule: frontend permissions improve UX only; they never replace backend authorization.

---

### FW3 — Dashboard

Scope:

- summary;
- receivables;
- aging;
- delivery;
- collections.

Frontend displays backend aggregates. No authoritative financial recalculation.

---

### FW4 — Customers

Scope:

- paginated list;
- search/filter;
- segments;
- manager/status projection;
- primary contact projection;
- customer detail.

Use URL-backed filters and stable query keys.

---

### FW5 — Receivables

Scope:

- contracts;
- invoices;
- payments;
- allocations/detail projections.

Frontend displays server-calculated `originalAmount`, `paidAmount`, `outstandingAmount`, `paymentStatus` and `daysOverdue`.

---

### FW6 — Collections

Scope:

- work queue;
- priority/status/assignee filters;
- overdue next-action projection;
- assign/action workflows;
- promise-to-pay/dispute/close flows as backend endpoints become available.

---

### FW7 — Campaigns

Scope:

- list/detail;
- campaign builder;
- audience selection;
- template/channel selection;
- prepare/run;
- run/recipient monitoring.

Frontend MUST use the backend `CampaignSelection` contract. Audience eligibility MUST NOT be recreated in the browser.

---

### FW8 — Messages / Delivery Operations

Scope:

- paged message list;
- status/channel/customer/campaign filters;
- message detail;
- masked destination;
- attempts/retry state;
- provider reference/error summary;
- attachments;
- retry action when backend permits it.

---

### FW9 — Templates

Scope:

- template list;
- editor;
- versions;
- placeholders;
- locale/channel variants;
- preview;
- HTML/PDF preview integration.

---

### FW10 — Imports

Scope:

```text
Upload
  ↓
SourceSchema
  ↓
MappingProfile
  ↓
Validation
  ↓
ImportBatch
  ↓
Result/errors
```

This slice should remain aligned with backend metadata-driven import contracts.

---

### FW11 — Files / Attachments

Scope:

- files list;
- generated documents;
- attachment states;
- download;
- lifecycle/expiry visibility.

Frontend must support backend states including `PENDING`, `READY`, `FAILED` and deleted/expired semantics where exposed.

---

### FW12 — Administration

Scope:

- users;
- roles;
- permissions;
- segments;
- manager/admin reference data;
- tenant settings;
- channels;
- SourceSchema;
- MappingProfile.

Administration is deliberately later than core operational workflows.

---

## 10. Standard implementation pipeline for every frontend slice

Every frontend slice MUST follow this sequence:

```text
1. Backend contract audit
2. DTO/type definition
3. Entity API adapter
4. TanStack Query hooks
5. Feature action/orchestration
6. Widget components
7. Page composition
8. Permission wiring
9. Loading/error/empty states
10. Tests
11. npm run typecheck
12. npm run build
13. PR review
```

Do not start from visual page assembly before transport and domain projection contracts are confirmed.

---

## 11. Testing strategy

Frontend testing should be layered.

### Unit

Use for:

- pure formatters;
- query-key builders;
- URL filter parsing;
- auth/session reducer/store behavior;
- refresh coordinator logic.

### Component

Use for:

- login form behavior;
- permission guard;
- filter widgets;
- important reusable entity/widget rendering states.

### Integration

Use for:

- login → current user bootstrap;
- concurrent 401 → one refresh → request replay;
- refresh failure → logout/session clear;
- page filter → query key/request mapping;
- mutation → targeted invalidation.

### End-to-end

Add only for high-value user journeys after core screens exist:

- login;
- customer lookup;
- receivable inspection;
- collection action;
- campaign prepare/run;
- delivery troubleshooting.

---

## 12. UI state contract

Every async operational screen MUST define four explicit states:

1. loading;
2. success;
3. empty;
4. error.

Where mutations exist, also define:

- submitting;
- conflict (`409`);
- forbidden (`403`);
- recoverable validation failure.

Do not rely on console-only errors.

---

## 13. Pagination and list rules

- backend pagination remains authoritative;
- page/slice DTO shapes follow the frozen React API contract;
- list pages SHOULD avoid loading entire tenant datasets;
- filter changes SHOULD reset page index when required;
- row identity MUST use stable backend IDs;
- totals MUST come from backend page metadata, not client-side guesses.

---

## 14. Permissions and navigation

`GET /api/v1/identity/me` is the current-user capability source for frontend UX.

Rules:

- hide/disable navigation/actions based on `permissions` where useful;
- direct route entry must still be guarded;
- `403` from backend remains authoritative;
- role names SHOULD NOT be hard-coded as the primary authorization mechanism when a permission is available.

---

## 15. Forms

Simple forms may use controlled React state initially.

Introduce React Hook Form when validation, nested fields, dynamic arrays or reusable field components justify it.

Frontend validation is UX validation only. Server validation remains authoritative and MUST be rendered from `ProblemDetail.errors` where available.

---

## 16. Styling and component-library policy

Do not introduce a large UI framework solely for convenience during early slices.

First stabilize:

- layout primitives;
- typography;
- spacing;
- form controls;
- buttons;
- table states;
- status badges;
- modal/dialog behavior.

A component library may be adopted later through an explicit decision if it materially reduces implementation cost without constraining the product's operational UI.

---

## 17. Branch and PR discipline

Frontend implementation SHOULD use one branch per work slice or tightly scoped sub-slice.

Examples:

```text
feat/frontendweb-fw1-auth-session
feat/frontendweb-fw3-dashboard
feat/frontendweb-fw4-customers
```

Rules:

- do not develop new feature work directly on `main`;
- keep spec-only changes separate where practical;
- run typecheck/build/tests before marking PR ready;
- do not claim frontend validation green without actual command output/CI evidence;
- do not auto-merge implementation PRs without explicit approval.

---

## 18. Current execution state

At the time of this plan:

```text
FW0 Foundation                CLOSED
FW1 Authentication & Session  NEXT / ACTIVE BRANCH
FW2+                          PLANNED
```

Current implementation branch:

```text
feat/frontendweb-fw1-auth-session
```

This specification branch intentionally contains no FW1 runtime implementation.

---

## 19. Architectural invariants

The following are non-negotiable unless changed by an explicit architecture decision:

1. backend is authoritative for business and financial state;
2. lower frontend layers do not depend on upper layers;
3. transport is centralized in `shared/api`;
4. server state uses TanStack Query;
5. operational filters prefer URL state;
6. tenant boundary follows authenticated context;
7. refresh is single-flight and bounded to one replay;
8. `403` never triggers token refresh;
9. tenant/session cache is cleared on session loss/logout, not on normal mutations;
10. polling is bounded and terminal-state-aware;
11. frontend never performs authoritative money calculations;
12. permissions drive UX, backend authorization drives security;
13. every slice must pass contract audit before UI implementation;
14. every implementation PR must have real typecheck/build/test evidence appropriate to its scope.

---

## 20. Definition of Done for the frontend architecture phase

The frontend architecture phase is considered established when:

- FW1 implements the session boundary according to this plan;
- FW2 establishes authenticated app shell and permission projection;
- FW3 or FW4 proves the entity → feature → widget → page pattern on a real backend-backed screen;
- tests cover the auth refresh boundary and at least one real query/mutation workflow;
- CI includes frontend typecheck/build/test execution using the committed lockfile.

After that point, remaining work should primarily be incremental feature delivery rather than architectural restructuring.
