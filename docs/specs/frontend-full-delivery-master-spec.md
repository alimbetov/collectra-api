# FrontendWeb — Full Delivery Master Specification

Status: EXECUTION SPECIFICATION
Date: 2026-09-14
Scope: FE-00 + FW1..FW12

## 1. Purpose

This document is the execution contract for completing the Collectra React frontend from the current FW0 foundation through the first complete operational/admin frontend release.

It does not replace the frozen API contract or existing UI/process specifications. It defines implementation order, architecture, deliverables, acceptance criteria, test gates, PR boundaries and completion rules for the full frontend plan.

Upstream documents remain authoritative for business/API semantics:

- `frontend-ui-information-architecture.md`
- `frontend-user-processes.md`
- `frontend-screen-api-matrix.md`
- `frontend-ui-process-api-review-2026-09-13.md`
- `frontend-blocker-closure-2026-09-13.md`
- `frontend-react-api-contract.md`

Backend remains authoritative for tenant, authentication, financial, collection, eligibility, delivery and workflow state. React MUST remain a projection, interaction and client-orchestration layer.

---

## 2. Delivery sequence

Mandatory execution order:

```text
FE-00 finish
   ↓
FW1 finish + tests
   ↓
FW2 Workspace / RBAC / i18n / UI primitives
   ↓
FW3 Dashboard
   ↓
FW4 Customers
   ↓
FW5 Receivables
   ↓
FW6 Collections
   ↓
FW7 Campaigns
   ↓
FW8 Messages
   ↓
FW9 Templates ─┐
FW10 Imports ──┼─ may run as separate branches after FW8 base is stable
FW11 Files ────┘
   ↓
FW12 Administration
```

A later slice MAY begin before the previous PR is merged only when:

1. its dependencies are stable;
2. it is based on the exact required parent branch/commit;
3. it does not duplicate unfinished infrastructure;
4. the dependency is documented in the PR body.

No slice may bypass a red mandatory quality gate.

---

## 3. Architectural invariants

### 3.1 Layer direction

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

Lower layers MUST NOT import higher layers.

### 3.2 Domain truth

Frontend MUST NOT implement authoritative:

- invoice balance calculations;
- payment allocation rules;
- collection state machines;
- campaign recipient eligibility;
- retry/delivery state machines;
- tenant authorization;
- server permission decisions;
- file/document lifecycle state transitions.

### 3.3 Server state

TanStack Query is the default server-state mechanism.

- Query keys MUST include all request-affecting filters.
- Mutation invalidation MUST be targeted.
- `queryClient.clear()` is reserved for session/tenant boundary resets.
- Polling MUST stop on terminal states.

### 3.4 Route state

Operational list filters SHOULD be URL-backed.

Example:

```text
/customers?page=1&status=ACTIVE&segment=VIP
```

### 3.5 Money

Frontend formats backend amounts/currencies but MUST NOT derive authoritative balances using JavaScript floating point.

### 3.6 Errors

All API failures pass through the shared HTTP boundary.

Required semantics:

- 400 — render safe validation/problem details;
- 401 — at most one coordinated refresh + one replay;
- 403 — no refresh; forbidden UX;
- 404 — not-found UX;
- 409 — preserve backend conflict semantics and reload authoritative state when required;
- 5xx/network — safe operational error, support identifiers where available.

### 3.7 Security

- access token: memory;
- refresh token: interim `sessionStorage` only under the current backend contract;
- no auth tokens in `localStorage`;
- no credentials/tokens/provider secrets in console logs;
- backend authorization remains authoritative;
- frontend guards are UX/capability projection only.

---

## 4. Global quality gates

Every implementation PR MUST satisfy all applicable gates.

### G-FE-01 — Type safety

```bash
npm run typecheck
```

MUST be green.

### G-FE-02 — Tests

```bash
npm run test:ci
```

MUST be green after FE-00 is merged.

### G-FE-03 — Production build

```bash
npm run build
```

MUST be green.

### G-FE-04 — CI

GitHub Actions frontend job MUST be green.

### G-FE-05 — API contract

DTOs and endpoint usage MUST match `frontend-react-api-contract.md` and actual backend implementation. Nullability, enum/status values and pagination shapes MUST NOT be guessed.

### G-FE-06 — Layering

No reverse/circular layer dependencies.

### G-FE-07 — States

Every route/widget performing remote loading MUST implement applicable loading, empty, error, forbidden, success and mutation-pending/disabled states.

### G-FE-08 — Accessibility baseline

Interactive shared UI MUST support labels, keyboard operation, visible focus and semantic button/link behavior.

### G-FE-09 — Safe observability

Problem details may surface `code`, `traceId`, `correlationId`; secrets/raw sensitive data may not.

### G-FE-10 — PR discipline

One slice/sub-slice per PR. No automatic merge while validation is red or pending.

---

# 5. FE-00 — Frontend test and CI foundation

## Objective

Make frontend validation executable and mandatory before business screens scale.

## Current state

Implementation exists in `chore/frontendweb-test-ci-foundation` / PR #69, but the CI frontend job is blocked at `npm ci` until the real lockfile reflects the added test dependencies.

## Required deliverables

- Vitest;
- jsdom;
- React Testing Library;
- `@testing-library/user-event`;
- `@testing-library/jest-dom`;
- MSW;
- shared test setup;
- `npm run test`;
- `npm run test:ci`;
- frontend GitHub Actions job;
- committed real `package-lock.json`.

## Required tests

Baseline transport tests:

- successful JSON response;
- 204 response;
- RFC7807/ProblemDetail mapping.

## Acceptance criteria

```bash
npm ci
npm run typecheck
npm run test:ci
npm run build
```

all green locally and in CI.

## Exit

PR #69 may be merged only after the frontend CI job is green.

---

# 6. FW1 — Authentication and session

## Objective

Deliver a deterministic authenticated browser session boundary on top of the frozen backend auth contract.

## Backend surface

```text
POST /api/v1/auth/login/by-slug
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/identity/me
```

## Deliverables

- exact DTOs including backend nullability;
- login by `tenantSlug + email + password`;
- access token memory storage;
- refresh token session storage;
- Bearer injection;
- single-flight refresh coordinator;
- exactly one request replay;
- final authenticated 401 => session invalidation;
- refresh failure/malformed payload => session invalidation;
- `/identity/me` bootstrap;
- `AuthProvider`;
- `RequireAuth`;
- primitive `PermissionGuard` / `RequirePermission` capability;
- logout with guaranteed local cleanup;
- protected route redirect preserving internal path/search/hash;
- query-cache clear on session loss/logout.

## Required tests

1. successful login stores tokens and loads `/me`;
2. invalid login leaves session unauthenticated;
3. page bootstrap with refresh token restores `/me`;
4. three concurrent authenticated 401s issue exactly one refresh request;
5. successful refresh replays each failed request once;
6. replayed 401 invalidates session;
7. refresh 401/403/5xx/network/malformed JSON invalidates session;
8. ordinary 403 does not trigger refresh;
9. logout backend failure still clears local session without unhandled rejection;
10. unauthenticated protected route redirects to `/login`;
11. successful login restores safe internal return route;
12. external/protocol-relative return path is rejected.

## Acceptance criteria

- FE-00 available in branch/base;
- all auth tests green;
- typecheck/test/build green;
- no refresh loop;
- no token logging;
- DTOs match backend contract.

---

# 7. FW2 — Workspace, RBAC UX, i18n and shared UI foundation

## Objective

Turn the authenticated shell into a reusable operational workspace so later business slices do not create duplicated UX infrastructure.

## Scope

### App shell

Create/refactor reusable app-shell, sidebar, topbar and page-header widgets.

Required behavior:

- authenticated user projection;
- user menu;
- logout entry;
- responsive navigation;
- active route indication;
- page content shell.

### RBAC UX

- permission-aware navigation visibility;
- route-level permission boundary;
- 403 page;
- permissions sourced from `/identity/me`;
- no frontend-only authorization assumptions.

### Routing

- 404 route;
- reusable protected/permission route wrappers;
- route metadata where useful for title/breadcrumbs.

### i18n

Establish translation boundary before mass feature implementation.

Minimum:

```text
ru — primary/default application locale
kk — supported structure and key namespace
fallback locale — explicit
```

### Formatting

Create domain-agnostic `date`, `date-time`, `money`, `number` formatters using `Intl`. Apply user locale/timezone where appropriate, preserve LocalDate semantics, and perform no business arithmetic.

### Shared UI primitives

Minimum reusable primitives:

```text
Button
Input
Select
TextArea
FormField
Alert/ProblemPanel
StatusBadge
Spinner
Skeleton
EmptyState
PageHeader
DataTable shell
Pagination
Dialog
ConfirmDialog
```

### Error/support UX

- global ErrorBoundary;
- ProblemDetail panel;
- safe support/reference identifiers;
- copy trace/correlation id action where present.

## Tests

- permission guard allow/deny;
- sidebar visibility by permissions;
- 403/404 routing;
- formatting utilities;
- shared form/button accessibility baseline;
- global problem panel rendering.

## Exit criteria

Later slices MUST consume shell, formatting, error and core UI primitives without reimplementing them.

---

# 8. FW3 — Dashboard

## Objective

Deliver the first real authenticated operational overview using backend aggregates only.

## Scope

Consume frozen Dashboard API/DTO groups for summary, receivables, aging, delivery and collections.

Required UI:

- customers count;
- active contracts;
- open collection cases;
- active campaigns;
- outstanding by currency;
- aging buckets;
- delivery metrics;
- collection metrics;
- business/as-of date projection.

## Rules

- no recalculation of server aggregates;
- currency values formatted by provided currency;
- safe loading/error/empty states.

## Tests

- successful rendering;
- multiple currencies;
- zero/empty metrics;
- loading/error states;
- date/currency formatting.

---

# 9. FW4 — Customers

Recommended sub-slices:

```text
FW4A list/search/filter
FW4B detail
FW4C edit/contact commands
```

## FW4A

Deliver paginated customer list with external id/type/display name/status, manager, primary email/phone, segments and URL-backed search/filter/pagination.

## FW4B

Customer detail route with identity/profile, contacts, segments, manager and related entry points supported by backend contracts.

## FW4C

Expose only backend-supported mutations. Invalidate only affected customer/query families.

## Tests

- pagination;
- URL filters;
- empty list;
- safe contact display;
- detail loading/not-found;
- mutation invalidation.

---

# 10. FW5 — Receivables

Recommended sub-slices:

```text
FW5A invoices
FW5B payments
FW5C allocations/reversal
FW5D contracts
```

Use backend values for invoice/due dates, original/paid/outstanding amount, currency, payment status, overdue and days overdue.

Payments render backend state only. Allocations/reversal expose backend commands and handle 409/version conflict by reloading authoritative state. Contracts use backend pagination/version fields.

## Tests

- money formatting without derived arithmetic;
- status/overdue projection;
- pagination/filtering;
- conflict handling;
- mutation invalidation.

---

# 11. FW6 — Collections

Recommended sub-slices:

```text
FW6A work queue + detail/timeline
FW6B assignment + next action
FW6C Promise-to-Pay
FW6D disputes
FW6E lifecycle commands
```

Work queue consumes backend projection for customer, invoice, status, priority, assignee, amount/payment status, next action/due/overdue and lifecycle/version fields.

All commands use backend state/version semantics. Duplicate submissions must be disabled while mutation is pending.

## Tests

- queue filters;
- overdue next action;
- assignment mutation;
- Promise-to-Pay/dispute flows where endpoints exist;
- 409/version conflict;
- terminal/closed state restrictions.

---

# 12. FW7 — Campaigns

Recommended sub-slices:

```text
FW7A list/detail
FW7B builder + CampaignSelection
FW7C prepare/run
FW7D recipients/run monitoring
FW7E eligibility recheck UX
```

Frontend MUST submit backend `CampaignSelection` and MUST NOT reimplement recipient eligibility.

Builder supports backend-defined customer/segment ids, overdue range, amount range, template version, channel and scheduling fields where exposed.

Run monitoring displays backend recipient/sent/failed/skipped/retry/pending counters and lifecycle state. Polling stops at terminal status.

## Tests

- selection serialization;
- prepare/run mutation;
- duplicate submit prevention;
- terminal polling stop;
- counters equal backend values;
- ineligible/paid recheck projection.

---

# 13. FW8 — Messages / Delivery Operations

## Objective

Operational visibility into delivery without exposing sensitive destinations/provider secrets.

## Scope

- paged/sliced message list;
- status/channel/customer/campaign-run filters;
- message detail;
- masked destination;
- attempt/retry state;
- provider reference where contract permits;
- safe error code/summary;
- attachment projection;
- retry only when backend permits it.

## Tests

- no unmasked destination leakage;
- filters/pagination;
- retry state;
- error summary rendering;
- attachment state;
- 403/terminal behavior.

---

# 14. FW9 — Templates

## Scope

- template list/detail;
- editor;
- versions;
- locale/channel variants;
- placeholder selection;
- preview;
- HTML/PDF preview integration where supported.

Placeholder catalog comes from backend/reference contract. Server validation is authoritative.

## Tests

- create/edit validation;
- placeholder insertion;
- locale/channel switching;
- preview success/failure;
- immutable/version conflict semantics.

---

# 15. FW10 — Imports

Recommended sub-slices:

```text
FW10A execution/results
FW10B SourceSchema
FW10C MappingProfile
FW10D validation/testing UX
```

Flow:

```text
Upload → SourceSchema → MappingProfile → Validation → Execution → ImportBatch → Result/errors
```

Support only backend-defined JSON/XML/CSV/Excel surfaces. Mapping UI clearly separates source field from canonical/custom destination.

## Tests

- upload FormData;
- mapping validation;
- bad file/problem response;
- async polling terminal stop;
- partial/batch failure rendering.

---

# 16. FW11 — Files / Attachments

## Scope

- files list/detail;
- generated documents;
- message attachments;
- download;
- lifecycle/expiry visibility;
- backend status projection (`PENDING`, `READY`, `FAILED`, deleted/expired semantics where exposed).

Only offer download when backend status/endpoint allows it. Never expose storage credentials or secret URLs.

## Tests

- pending/ready/failed states;
- disabled download before ready;
- download error;
- expired/deleted representation.

---

# 17. FW12 — Administration

Recommended sub-slices:

```text
FW12A members/users
FW12B invitations
FW12C roles/permissions
FW12D sessions/security
FW12E tenant/reference settings
FW12F SourceSchema/MappingProfile admin integration if needed
```

Every admin route/action is permission-gated in UX; backend remains authoritative. Dangerous actions require confirmation. Password/token/credential values are never displayed except explicit one-time-secret workflows defined by backend.

## Tests

- permission-driven navigation/actions;
- invitations;
- role/permission changes;
- session revoke;
- confirmation flows;
- conflict/error handling.

---

# 18. Cross-cutting requirements

## Query keys

Each entity/domain owns stable query-key factories.

## Mutations

Every mutation defines pending UI, success behavior, targeted invalidation, ProblemDetail mapping, conflict behavior and duplicate-click prevention.

## Pagination

Use backend page/slice semantics exactly. Do not infer totals for slice-only contracts.

## Tables

Operational tables define stable row key, loading, empty, error, pagination, accessible controls and responsive behavior where required.

## Forms

Use simple controlled state for simple forms. Add React Hook Form only when form complexity justifies it.

## Observability

Problem panels expose safe copyable support references when trace/correlation ids exist.

## i18n

Feature copy after FW2 should use translation keys except technical/developer-only surfaces.

## Accessibility

Accessibility is encoded in shared primitives and not postponed to final release.

---

# 19. Branch and PR plan

Recommended branches:

```text
chore/frontendweb-test-ci-foundation
feat/frontendweb-fw1-auth-session
feat/frontendweb-fw2-workspace-rbac
feat/frontendweb-fw3-dashboard
feat/frontendweb-fw4a-customer-list
feat/frontendweb-fw4b-customer-detail
feat/frontendweb-fw4c-customer-edit
feat/frontendweb-fw5a-invoices
feat/frontendweb-fw5b-payments
feat/frontendweb-fw5c-allocations
feat/frontendweb-fw5d-contracts
feat/frontendweb-fw6a-collection-queue
feat/frontendweb-fw6b-collection-actions
feat/frontendweb-fw6c-ptp
feat/frontendweb-fw6d-disputes
feat/frontendweb-fw7a-campaign-list
feat/frontendweb-fw7b-campaign-builder
feat/frontendweb-fw7c-campaign-run
feat/frontendweb-fw7d-campaign-monitoring
feat/frontendweb-fw8-messages
feat/frontendweb-fw9-templates
feat/frontendweb-fw10-imports
feat/frontendweb-fw11-files
feat/frontendweb-fw12-admin
```

Every PR body includes scope, API contracts used, architectural notes, tests added, actual validation status/output and deferred items. Never claim green CI unless verified.

---

# 20. Definition of Done — each sub-slice

A frontend sub-slice is DONE only when all applicable statements are true:

1. backend contract audited;
2. DTO nullability/status semantics verified;
3. correct layer placement used;
4. no reverse/circular dependency introduced;
5. stable query keys implemented;
6. URL state used for operational filters where appropriate;
7. loading/empty/error/permission states implemented;
8. mutation pending/conflict behavior implemented when applicable;
9. sensitive data masking preserved;
10. tests cover primary happy path and critical failure paths;
11. `npm run typecheck` green;
12. `npm run test:ci` green;
13. `npm run build` green;
14. GitHub frontend CI green;
15. PR review has no unresolved blocking findings;
16. merge occurs only after explicit approval.

---

# 21. Definition of Done — complete frontend plan

The first complete frontend delivery is DONE when:

- FE-00 through FW12 required scope is merged;
- all first-release routes are reachable only under correct auth/permission conditions;
- primary operational workflows are usable end-to-end against backend contracts;
- RU application locale is operational and KK translation structure is present;
- shared formatting/UI/error patterns are consistently reused;
- no page duplicates authoritative backend business logic;
- no auth token/provider secret leakage is present;
- all mandatory frontend CI gates are green on `main`;
- core flows have integration/component coverage protecting auth, mutations, filters, polling and error behavior;
- remaining deferred enhancements are explicitly documented.

---

# 22. Immediate execution plan

Execute in this order:

```text
1. FE-00
   - refresh real package-lock.json
   - run npm ci/typecheck/test:ci/build
   - obtain green frontend CI
   - review and merge PR #69

2. FW1
   - rebase/update on FE-00
   - add auth/session integration tests
   - validate refresh/session invariants
   - run typecheck/test/build
   - review and merge PR #70 only when green

3. FW2
   - workspace + RBAC UX
   - i18n boundary
   - formatting/config boundaries
   - reusable UI primitives
   - global 403/404/error UX

4. FW3 Dashboard
5. FW4 Customers (A/B/C)
6. FW5 Receivables (A/B/C/D)
7. FW6 Collections (A/B/C/D/E)
8. FW7 Campaigns (A/B/C/D/E)
9. FW8 Messages
10. FW9 Templates
11. FW10 Imports
12. FW11 Files
13. FW12 Administration
```

No later phase changes these architectural invariants without an explicit spec amendment and review.
