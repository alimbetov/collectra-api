# FrontendWeb FW3–FW12 — implementation plan

Status: DRAFT FOR REVIEW

Baseline: `main@02515d0`

## Цель

Зафиксировать последовательность реализации business UI после закрытия общей frontend
платформы FW2. Каждый slice ниже имеет отдельное ТЗ и реализуется отдельной code-веткой
от актуального `main`.

## Обязательный входной gate

До FW3 в `main` должны находиться FW2A–FW2D и closure FW2E:

- routing/RBAC shell, `ru/kk`, locale/timezone и `Intl` formatting;
- Button/FormField/Alert/StatusBadge/Spinner/EmptyState/DataTable/Pagination/Dialog;
- ErrorBoundary и безопасный ProblemDetail/correlation ID UX;
- toast/confirmation, dialog focus trap и architecture dependency guard;
- frontend CI: install, typecheck, unit tests и build.

FW2C/FW2D нельзя считать доставленными только потому, что их stacked PR закрыт: код
должен присутствовать именно в `main`.

## Dependency graph

```text
FW2 closure
  ├─> FW3 Dashboard ─> FW4 Customers ─> FW5 Receivables ─> FW6 Collections
  │                                                └───────> FW7 Campaigns ─> FW8 Messages
  ├─> FW9 Templates ───────────────────────────────────────> FW7 Campaigns
  ├─> Backend import diagnostics ──────────────────────────> FW10 Imports
  ├─> Backend files registry ──────────────────────────────> FW11 Files
  └─> FW12 Administration
```

FW9 и FW12 можно выполнять параллельно после FW2. FW7 требует доступного template
selection flow, но не обязан ждать полного visual builder, если существующую published
version можно выбрать через API.

## Общий frontend contract

### Модули

```text
src/app/                         composition, routes, providers, query client
src/pages/<domain>/              route-level page composition
src/features/<domain>/           commands, forms, filters and workflows
src/entities/<domain>/           DTO, query keys, API and display model
src/shared/api/                  HTTP/RFC7807 transport only
src/shared/i18n/                 ru/kk messages and Intl formatters
src/shared/ui/                   business-neutral UI primitives
```

Dependency direction: `app -> pages -> features -> entities -> shared`. Reverse imports,
cross-feature imports and direct page-to-page imports are forbidden by architecture test.

### Server state

- TanStack Query owns remote state; React component state owns only ephemeral UI state.
- Query keys are centralized factories and always include normalized filters.
- Paging/filter/sort state is serialized in URL; browser back/forward restores the screen.
- Changing any filter resets `page` to `0`.
- Previous page data may remain visible only with an explicit refreshing indicator.
- Mutations invalidate the smallest authoritative query set; no global cache clear.
- Backend remains authoritative after mutation; optimistic updates require a specified
  rollback contract and are otherwise forbidden.

### Tenant and security

- Tenant identity comes only from authenticated session/JWT.
- React never accepts or sends an arbitrary tenant ID for tenant workspace queries.
- Navigation/action guards improve UX but never replace backend authorization.
- `403`, `404` and foreign-tenant absence remain indistinguishable where backend makes
  them indistinguishable.
- PII is displayed only when already returned by the screen-oriented API; raw delivery
  destinations, provider payloads, credentials and internal exception messages are not
  rendered or logged.

### Mutations and concurrency

- Double submit is disabled while a mutation is pending.
- Idempotency/command IDs are generated once per user intent and reused on network replay.
- Entity `version` is sent for versioned commands and `409` never triggers blind retry.
- After `409`, UI preserves user input, refreshes authoritative state and offers an
  explicit retry/reconciliation action.
- Destructive and lifecycle actions require a shared confirmation dialog.

### Error and async semantics

- `ProblemDetail.code` drives mapped user text; `detail` is not a localization key.
- Opaque `5xx` details are hidden. Only validated correlation/trace ID is copyable.
- Polling is enabled only for non-terminal async states, pauses when the tab is hidden,
  uses a bounded interval and stops on terminal state/unmount.
- No business retry loop is implemented in React.

### Accessibility and localization

- No scattered user-visible literals: both `ru` and `kk` catalogs are updated together.
- LocalDate is formatted without timezone conversion; Instant uses the user timezone.
- Amounts always include backend-provided currency.
- Forms expose label, hint and field/server errors via accessible relationships.
- Tables, dialogs, menus and toasts remain keyboard operable with deterministic focus.

## Backend prerequisites found by audit

| Frontend slice | Missing backend contract | Required result |
|---|---|---|
| FW7 | direct campaign-run detail projection | tenant-scoped `GET /campaigns/{campaignId}/runs/{runId}` with counters/timestamps |
| FW10 | durable record/field import diagnostics | tenant-scoped paged errors with masking, retention and stable ordering |
| FW11 | files registry list | tenant-scoped paged/filterable `GET /api/v1/files` projection |
| FW12 | unbounded tenant member/invitation lists | paged fixed-filter projections before large-tenant production acceptance |

No frontend slice may work around these gaps using unbounded downloads, local joins or
client-side scanning.

## Delivery order

| Order | Specification | Suggested code branch |
|---|---|---|
| 1 | [FW3 Dashboard](frontendweb-fw03-dashboard.md) | `feat/frontendweb-fw3-dashboard` |
| 2 | [FW4 Customers](frontendweb-fw04-customers.md) | split FW4A/FW4B/FW4C |
| 3 | [FW5 Receivables](frontendweb-fw05-receivables.md) | split list/detail and payments |
| 4 | [FW6 Collections](frontendweb-fw06-collections.md) | split queue/detail workflows |
| 5 | [FW7 Campaigns](frontendweb-fw07-campaigns.md) | split list/detail and run execution |
| 6 | [FW8 Message monitoring](frontendweb-fw08-message-monitoring.md) | `feat/frontendweb-fw8-message-monitoring` |
| 7 | [FW9 Templates](frontendweb-fw09-templates.md) | split management and builder |
| 8 | [FW10 Imports](frontendweb-fw10-imports.md) | after import diagnostics API |
| 9 | [FW11 Files](frontendweb-fw11-files.md) | after files registry API |
| 10 | [FW12 Administration](frontendweb-fw12-administration.md) | split tenant/profile/platform surfaces |

## Definition of Ready для каждого code PR

- prerequisite branches merged in `main`;
- referenced endpoint and DTO confirmed against current OpenAPI artifact;
- permission names confirmed against backend annotations;
- route, query keys, URL state and invalidation graph written in the slice spec;
- backend blocker is closed rather than emulated on frontend;
- `main` frontend CI is green.

## Global Definition of Done

- all slice-specific acceptance scenarios pass;
- no N+1 HTTP request pattern from list rows;
- no tenant ID or PII leaks through URL, logs or error UI;
- `npm ci`, `npm run typecheck`, `npm run test:ci`, `npm run build` are green;
- OpenAPI compatibility and backend required checks remain green;
- documentation status distinguishes implemented code from real-environment acceptance.
