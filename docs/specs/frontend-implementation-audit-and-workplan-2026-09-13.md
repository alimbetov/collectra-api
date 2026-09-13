# FrontendWeb — Implementation Audit and Work Plan

Status: AUDITED / EXECUTION BASELINE
Date: 2026-09-13
Branch: `spec/frontend-layered-implementation-plan`

## 1. Purpose

This document audits `frontend-layered-implementation-plan.md` against the current Collectra repository and turns the architectural roadmap into an executable frontend delivery plan.

It is downstream from:

- `frontend-ui-information-architecture.md`;
- `frontend-user-processes.md`;
- `frontend-screen-api-matrix.md`;
- `frontend-ui-process-api-review-2026-09-13.md`;
- `frontend-blocker-closure-2026-09-13.md`;
- `frontend-react-api-contract.md`;
- `frontend-layered-implementation-plan.md`.

Backend remains authoritative for business, financial, tenant and workflow state. React remains a projection, interaction and client-orchestration layer.

---

## 2. Audit verdict

The layered plan is architecturally sound and can be retained as the primary frontend architecture contract.

The following decisions are approved:

- dependency direction `app -> pages -> widgets -> features -> entities -> shared`;
- centralized HTTP transport in `shared/api`;
- TanStack Query for server state;
- URL-backed operational filters;
- no Redux by default;
- backend-authoritative money/workflow state;
- permission projection based on `/identity/me`, with backend authorization authoritative;
- bounded polling;
- one frontend work branch/PR per slice or tightly scoped sub-slice;
- contract audit before UI assembly.

However, the plan is not yet fully executable without additional cross-cutting work. Five items are A0 gates and must be solved before frontend development scales beyond FW1/FW2.

---

## 3. Findings

### A0-01 — Frontend test infrastructure is missing

Current `frontendweb/package.json` has build/typecheck scripts but no test runner, DOM test environment, React component test utilities or request mocking layer.

The roadmap requires unit/component/integration tests, therefore the repository must first provide an executable test command.

Required baseline:

- Vitest;
- jsdom;
- React Testing Library;
- `@testing-library/user-event`;
- request mocking suitable for frontend integration tests, preferably MSW;
- `npm test` and `npm run test:ci` scripts.

Gate:

```text
npm run typecheck
npm run test:ci
npm run build
```

all pass locally and in CI.

---

### A0-02 — CI currently does not validate `frontendweb`

The existing GitHub Actions CI verifies Maven/backend only.

Required frontend CI job:

```text
checkout
setup-node
npm ci
npm run typecheck
npm run test:ci
npm run build
```

Use the committed `package-lock.json`; CI MUST use `npm ci`, not `npm install`.

The frontend job should be independent from backend Testcontainers so a frontend failure is immediately visible.

---

### A0-03 — Auth token-storage policy is not explicit

Current backend behavior is:

- `/api/v1/auth/login/by-slug` returns `{accessToken, refreshToken, tokenType, expiresIn}`;
- `/api/v1/auth/refresh` accepts `{refreshToken}`;
- `/api/v1/auth/logout` accepts `{refreshToken}`;
- refresh token rotation is implemented server-side.

The backend does not currently provide an HttpOnly refresh-cookie contract.

Interim frontend policy for FW1:

1. access token lives in memory;
2. refresh token MAY be stored in `sessionStorage` so a page reload can recover the session within the same browser tab/session;
3. do not persist tokens in `localStorage`;
4. clear refresh token on logout, refresh failure, detected invalid session and tenant/session boundary reset;
5. never log tokens;
6. do not expose token values in React component props or query cache;
7. keep the storage implementation behind `shared/auth/token-storage.ts` so an HttpOnly-cookie migration does not rewrite feature code.

Security note: `sessionStorage` remains readable by JavaScript and therefore does not provide XSS isolation. Long-term production hardening should move refresh credentials to an HttpOnly/Secure/SameSite cookie through a backend contract change.

This is an explicit interim compatibility decision, not the final browser-auth security model.

---

### A0-04 — FW1/FW2 responsibility overlaps

The current roadmap places `permission guard` in FW1 and permission-driven navigation/route guards in FW2.

Approved split:

**FW1 owns session security primitives:**

- login;
- token storage;
- refresh coordination;
- current-user bootstrap;
- authenticated vs unauthenticated route boundary;
- primitive `RequireAuth`;
- primitive `RequirePermission`;
- logout/session-loss handling.

**FW2 owns authenticated workspace UX:**

- sidebar/topbar;
- permission-aware navigation;
- user menu;
- 403/404 presentation;
- profile entry point;
- global page layout/breadcrumb conventions.

This prevents FW1 from becoming a full application-shell redesign.

---

### A0-05 — Frontend observability/error-support behavior needs a concrete baseline

The API contract already carries `traceId` / `correlationId` through ProblemDetail. The UI implementation plan must define how operators/users can use them.

Required baseline:

- `ApiError` preserves `code`, `traceId`, `correlationId`, validation errors and HTTP status;
- unexpected failures show a safe user-facing message;
- support/reference identifier is displayable/copyable on error screens;
- credentials, access tokens, refresh tokens and raw provider secrets are never logged;
- production console logging is minimal and sanitized.

External browser telemetry/Sentry is not required for FW1 and should be a separate later architecture decision.

---

## 4. A1 gaps — should be solved early, but do not block initial FW1 coding

### A1-01 — Locale, timezone and formatting layer

The current user contract exposes `locale` and `timezone`; the product baseline includes multilingual user/customer data.

Create domain-agnostic formatting utilities:

```text
shared/lib/format/
  date.ts
  date-time.ts
  money.ts
  number.ts
```

Rules:

- formatting may use `Intl`;
- monetary display uses backend amount + currency;
- formatting never performs authoritative monetary arithmetic;
- user timezone is applied to timestamps where the screen contract calls for local display;
- business `LocalDate` values must not be silently converted through UTC timestamps.

### A1-02 — i18n foundation

Collectra targets RU/KZ initially. Do not hard-code large volumes of English operational text into feature slices.

Add a minimal translation boundary before many pages are implemented.

Initial requirement:

- `ru` primary application messages;
- `kk`/Kazakh structure ready and incrementally populated;
- locale selection sourced from current user/profile;
- fallback locale defined;
- translation keys organized by feature/domain.

Do not block FW1 transport work on full translation coverage, but establish i18n no later than FW2.

### A1-03 — Configuration boundary

Runtime/frontend configuration must have one typed access point under `shared/config`.

At minimum define:

- API base behavior (same-origin `/api` by default);
- application environment label where needed;
- dev-only diagnostics flags if introduced.

Do not scatter direct `import.meta.env` access across features.

### A1-04 — Styling/UI primitives

Before multiple list pages are implemented, establish reusable primitives for:

- button;
- input;
- select;
- form field/error;
- status badge;
- spinner/skeleton;
- alert/problem panel;
- empty state;
- page header;
- table/pagination shell;
- dialog/confirmation.

A large UI framework remains optional; the primitive contract is required.

### A1-05 — Accessibility baseline

Operational UI components should provide:

- associated form labels;
- keyboard navigation;
- visible focus;
- semantic buttons/links;
- accessible error text;
- dialog focus management when dialogs are introduced.

Do not postpone all accessibility work until the end because shared primitives will propagate mistakes across all screens.

---

## 5. Current repository readiness

### Backend API readiness

The screen/API matrix reports the core first-release surfaces as READY or READY+ for:

- auth/bootstrap;
- dashboard;
- customers;
- contracts;
- invoices/payments;
- collections;
- templates;
- campaigns;
- message monitoring;
- imports/configuration;
- files;
- administration.

This means frontend sequencing can be product-driven rather than blocked on broad backend CRUD creation.

### Frontend readiness

FW0 currently provides:

- React/Vite/TypeScript;
- React Router;
- TanStack Query;
- HTTP client skeleton;
- placeholder application shell;
- `package-lock.json`;
- successful local typecheck;
- successful local production build.

Current source tree is intentionally small (`app`, `shared`, `main.tsx`, global styles). It has not yet accumulated architectural debt, so now is the correct point to establish layer boundaries.

---

## 6. Approved implementation sequence

The frontend should be delivered in five macro phases.

```text
Phase 0  Engineering gates
Phase 1  Identity and workspace
Phase 2  Core operational read surfaces
Phase 3  Operational commands/workflows
Phase 4  Content/import/admin capabilities
```

### Phase 0 — Engineering gates

#### FE-00 — Test/CI foundation

Branch suggestion:

`chore/frontendweb-test-ci-foundation`

Scope:

- install/configure Vitest + jsdom + Testing Library + MSW;
- add test setup;
- add smoke test;
- add `test` / `test:ci` scripts;
- add frontend CI job using `npm ci`;
- preserve existing backend CI.

Exit gate:

```text
npm ci
npm run typecheck
npm run test:ci
npm run build
```

all green locally and in GitHub Actions.

This can be developed in parallel with early FW1 code but MUST land before FW1 is declared complete.

---

## 7. Phase 1 — Identity and workspace

### FW1 — Authentication & Session

Implementation branch already exists:

`feat/frontendweb-fw1-auth-session`

#### FW1.1 Contract freeze

Implement exact DTOs:

```ts
interface SlugLoginRequest {
  tenantSlug: string;
  email: string;
  password: string;
}

interface TokenRequest {
  refreshToken: string;
}

interface AuthTokensDto {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

interface MeDto {
  id: UUID;
  email: string;
  displayName: string | null;
  locale: string;
  timezone: string;
  membershipId: UUID;
  membershipStatus: string;
  roles: string[];
  permissions: string[];
}
```

Do not invent tenant UUID input for normal login.

#### FW1.2 Shared auth storage

Create:

```text
shared/auth/
  token-storage.ts
  session-store.ts
  auth-events.ts
  auth-types.ts
```

Implement the approved interim token policy.

#### FW1.3 HTTP auth transport

Refactor `shared/api/http-client.ts` to support:

- bearer injection;
- public/auth endpoint bypass metadata;
- ProblemDetail preservation;
- one retry marker;
- AbortSignal preservation;
- safe JSON/FormData handling.

#### FW1.4 Single-flight refresh coordinator

One shared promise for concurrent refresh attempts.

Required scenario:

```text
request A -> 401
request B -> 401
request C -> 401
        \     |     /
         one refresh
              |
     rotated token pair
        /     |     \
 replay A replay B replay C
```

No recursive refresh and no second replay.

#### FW1.5 Current-user bootstrap

Implement `GET /api/v1/identity/me` as the canonical frontend identity/capability projection.

Startup states:

```text
bootstrapping
anonymous
authenticated
session-lost
```

Avoid rendering protected pages before bootstrap is resolved.

#### FW1.6 Login page

Route `/login`.

Fields:

- workspace/tenant slug;
- email;
- password.

Behavior:

- server errors mapped safely;
- no tenant enumeration messaging;
- successful login performs `/me` bootstrap then navigates to workspace;
- already-authenticated user is redirected away from login.

#### FW1.7 Route/security primitives

Implement:

- `RequireAuth`;
- `RequirePermission`;
- safe unauthenticated redirect preserving intended route where appropriate.

#### FW1.8 Logout/session loss

Logout sequence:

```text
POST /auth/logout
clear local token/session state
clear tenant-bound QueryClient cache
navigate /login
```

If server logout cannot complete, local credentials are still cleared.

#### FW1.9 Tests

Minimum required tests:

- successful login -> tokens -> `/me` -> authenticated;
- invalid login stays anonymous;
- bearer header added to protected request;
- three concurrent 401s -> one refresh;
- successful refresh replays each request once;
- refresh failure clears session/cache;
- 403 does not refresh;
- refresh endpoint 401 does not recursively refresh;
- logout clears local state;
- permission guard allow/deny.

FW1 exit gate:

- test suite green;
- typecheck green;
- production build green;
- no tokens in logs/query cache;
- authenticated route bootstrap deterministic.

---

### FW2 — Authenticated Workspace + RBAC UX

Suggested branch:

`feat/frontendweb-fw2-workspace-rbac`

Scope:

- authenticated app shell;
- sidebar and topbar;
- current-user menu;
- permission-aware navigation;
- 403 page;
- 404 page;
- profile/security/sessions navigation entry points;
- breadcrumbs/page-header convention;
- shared loading/error/empty primitives;
- typed runtime config boundary;
- initial i18n boundary;
- locale/timezone formatters.

Recommended first navigation model follows the approved information architecture:

```text
Dashboard
Customers
Receivables
Collections
Campaigns
Templates
Imports
Files
Administration (permission-based)
Profile
```

Exit gate:

- direct protected-route access behaves correctly;
- navigation visibility follows permissions;
- backend 403 is rendered as authoritative denial;
- locale/timezone formatting is centralized;
- no page-specific shell duplication.

---

## 8. Phase 2 — Core operational read surfaces

The goal is to prove the architecture on high-value read models before implementing many complex mutations.

### FW3 — Dashboard

Suggested branch:

`feat/frontendweb-fw3-dashboard`

Implement:

- summary query;
- receivables query;
- delivery query;
- collections query;
- KPI/card widgets;
- card-to-filtered-screen navigation.

No frontend recalculation of authoritative receivable/payment state.

### FW4 — Customers

Suggested branch:

`feat/frontendweb-fw4-customers`

Split if needed:

```text
FW4A customer list/search/filter
FW4B customer detail/read tabs
FW4C customer profile/contact commands
```

FW4A is the first canonical proof of:

```text
entity API -> query hooks -> feature filters -> widget table -> page composition
```

Required URL-backed filters and server pagination.

### FW5 — Receivables

Recommended split:

```text
FW5A invoices list/detail
FW5B payments list/detail
FW5C allocations/reversal commands
FW5D contracts secondary views/actions
```

Keep command/idempotency/version conflict handling in feature slices, not entity UI.

### FW6 — Collections

Recommended split:

```text
FW6A collection work queue/detail/timeline
FW6B assign/next-action commands
FW6C promise-to-pay
FW6D disputes
FW6E lifecycle close/hold/start commands
```

Prioritize read/work-queue UX before complex command dialogs.

---

## 9. Phase 3 — Communication workflows

### FW7 — Campaigns

Recommended split:

```text
FW7A campaign list/detail
FW7B builder + CampaignSelection
FW7C prepare/run
FW7D run recipients/status monitoring
FW7E eligibility recheck/conflict handling
```

Audience eligibility remains backend-authoritative.

### FW8 — Messages / Delivery Operations

Implement:

- paged operational list supported by current backend contract;
- status/channel/customer/campaign-run filters where exposed;
- message detail;
- masked destination only;
- retry/error state;
- attachment state;
- retry command only where explicitly supported.

Raw provider secrets/payloads remain hidden.

---

## 10. Phase 4 — Content, imports, files and admin

### FW9 — Templates

Recommended split:

```text
FW9A template list/version lifecycle
FW9B template builder catalogue/editor
FW9C validate/preview
FW9D assets and PDF/HTML preview
```

### FW10 — Imports

Recommended split:

```text
FW10A import execution/results
FW10B SourceSchema management
FW10C MappingProfile management
FW10D mapping test/validation UX
```

Do not combine execution and metadata configuration into one giant PR.

### FW11 — Files / Attachments

Implement:

- upload;
- metadata;
- download/presigned download;
- generated-document/attachment status projection;
- delete/lifecycle state.

### FW12 — Administration

Recommended split:

```text
FW12A members
FW12B invitations
FW12C roles/permissions
FW12D sessions/security
FW12E tenant/reference configuration
```

Platform administration remains separate from ordinary tenant workspace.

---

## 11. Cross-cutting rules for every implementation PR

Each PR must include the relevant parts of this checklist:

### Contract

- exact endpoint and DTO audited against backend;
- tenant scope understood;
- permission requirement identified;
- idempotency/version behavior identified for commands.

### Data layer

- centralized HTTP client only;
- stable query keys;
- server pagination/filtering;
- targeted invalidation;
- no tenant-wide full dataset fetch for list pages.

### UI behavior

- loading;
- success;
- empty;
- error;
- forbidden/conflict/validation states where applicable.

### Security

- no secrets/tokens logged;
- only masked contact/provider data where contract requires masking;
- permission-aware UX but backend-authoritative authorization;
- no frontend tenant override.

### Quality

```text
npm ci
npm run typecheck
npm run test:ci
npm run build
```

- CI evidence attached to PR;
- no automatic merge without explicit approval.

---

## 12. Dependency graph

```text
FW0 Foundation [DONE]
      |
      +---- FE-00 Test/CI Foundation -----+
      |                                    |
      +---- FW1 Auth/Session --------------+
                         |
                        FW2 Workspace/RBAC/i18n/formatting
                         |
               +---------+---------+
               |                   |
              FW3                 FW4A
          Dashboard         Customers list
               |                   |
               +---------+---------+
                         |
                        FW5
                    Receivables
                         |
                        FW6
                    Collections
                         |
                        FW7
                     Campaigns
                         |
                        FW8
                     Messages
                         |
               +---------+---------+
               |         |         |
              FW9       FW10      FW11
           Templates   Imports    Files
               \         |         /
                +--------+--------+
                         |
                        FW12
                 Administration
```

Not every later slice is technically blocked by the previous one, but this order minimizes architectural churn and delivers operational value progressively.

---

## 13. Immediate execution backlog

### P0 — now

1. Keep `spec/frontend-layered-implementation-plan` as specification-only.
2. Add this audit/work-plan document.
3. Continue runtime work in `feat/frontendweb-fw1-auth-session`.
4. Create FE-00 test/CI branch from current `main` (or land test infrastructure as the first tightly scoped commit in FW1 if sequencing requires it).
5. Implement FW1.1–FW1.4 before visual login-page polishing.

### P1 — after transport/session core

6. Implement `/me` bootstrap and route guards.
7. Implement login/logout UX.
8. Add complete FW1 integration tests.
9. Merge/land FE-00 CI gate before declaring FW1 complete.
10. Open FW1 PR; do not auto-merge.

### P2 — next milestone

11. FW2 authenticated workspace.
12. Establish i18n + locale/timezone formatting + shared UI states.
13. Implement Dashboard and Customer list as first real backend-backed screens.

---

## 14. Definition of Ready for a frontend slice

A slice is ready to implement only when:

- endpoints exist or are explicitly mocked by contract;
- request/response DTOs are known;
- permissions are known or can be safely handled by backend 403;
- list pagination/filter semantics are known;
- command conflict/idempotency semantics are known where applicable;
- authoritative backend fields are distinguishable from presentation-only frontend state.

---

## 15. Definition of Done for a frontend slice

A slice is done when:

- implementation follows allowed layer dependencies;
- required screen/API workflow is functional;
- loading/empty/error/permission/conflict states are handled;
- relevant tests exist and are green;
- `npm run typecheck` is green;
- `npm run build` is green;
- frontend CI is green;
- no business truth is duplicated in browser logic;
- PR has been reviewed and is not automatically merged without explicit approval.

---

## 16. Final audit decision

`frontend-layered-implementation-plan.md` is **APPROVED WITH REQUIRED HARDENING**.

No rewrite of the core architecture is required.

Required changes are additive:

- establish test/CI foundation;
- explicitly define interim token storage;
- clarify FW1/FW2 ownership;
- establish error-support/observability behavior;
- establish i18n/locale/timezone/config/UI primitive baselines early.

The next implementation focus remains **FW1 Authentication & Session**, with **FE-00 Test/CI Foundation** as the parallel mandatory engineering gate before FW1 completion.
