# FW3 — Dashboard

Status: DRAFT / API READY

Depends on: FW2 closure

Suggested branch: `feat/frontendweb-fw3-dashboard`

## Цель

Создать первый полностью рабочий authenticated screen: tenant dashboard с четырьмя
server-owned projections, переходами в операционные реестры и полным набором
loading/empty/error/forbidden states.

## Backend contract

`DashboardController`, `DashboardQueryService`, `ROLE_HUMAN`:

- `GET /api/v1/dashboard/summary`;
- `GET /api/v1/dashboard/receivables`;
- `GET /api/v1/dashboard/delivery`;
- `GET /api/v1/dashboard/collections`.

DTO остаются определёнными разделом Dashboard в
[`frontend-react-api-contract.md`](frontend-react-api-contract.md). `asOf` и
`businessDate` отображаются как freshness context.

## Route и модули

```text
/
src/pages/dashboard/DashboardPage.tsx
src/entities/dashboard/api/dashboard.api.ts
src/entities/dashboard/api/dashboard.queries.ts
src/entities/dashboard/model/dashboard.types.ts
src/features/dashboard/ui/*Card.tsx
```

Query keys: `dashboard.summary()`, `dashboard.receivables()`, `dashboard.delivery()`,
`dashboard.collections()`.

## UI contract

- KPI: customers, outstanding, overdue, active collection cases;
- receivables: outstanding, overdue, aging, due today/soon;
- delivery: recipients, sent, retry, failed, skipped;
- collections: active, overdue actions, promises due/overdue/broken;
- each card fails independently; one failed projection does not hide successful cards;
- card links preserve documented filters, for example overdue -> `/receivables?overdue=true`;
- currency-separated values are never summed across currencies in React.

## Invariants

- React does not derive `paid/outstanding/overdue` or workflow state from raw rows.
- No polling by default; manual refresh invalidates four dashboard keys.
- A card is empty only after a successful response with zero values, not while loading.
- Response freshness is visible when data can be operationally stale.
- Dashboard navigation never invents filters unsupported by target list APIs.

## Error semantics

- `401` follows global auth recovery;
- `403` renders the shared forbidden state;
- per-card `5xx` uses ProblemDetailPanel with retry for that key;
- no raw backend error text or payload is rendered.

## Tests

- API contract/MSW tests for all four responses;
- query-key and independent failure tests;
- currency and LocalDate/Instant formatting tests;
- navigation preserves target URL filters;
- loading -> success, empty and partial error component tests;
- architecture test: dashboard feature imports only entities/shared.

## Implementation order

1. DTO/API/query keys.
2. Card view models using shared formatters.
3. Four independent queries and page composition.
4. Filter-preserving navigation.
5. MSW/component/accessibility tests.

## Не входит

Charts, live WebSocket updates, custom dashboard layout, cross-currency aggregation and
frontend financial calculations.

## Definition of Done

- four projections render without row fan-out or client recomputation;
- partial backend failure remains usable;
- links open the correct filtered register;
- ru/kk and keyboard/accessibility states are covered;
- frontend CI is green.
