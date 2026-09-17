# FW3 — Dashboard

Status: REVIEWED / READY AFTER FW2 AND MONEY CONTRACT

Depends on: FW2 closure, approved public decimal transport contract

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

Фактическая семантика текущего кода:

- summary: customers, active contracts, open collection cases, active campaigns and
  outstanding amounts grouped by currency;
- receivables: per-currency outstanding, due today/next 7 days and aging buckets;
- delivery: cumulative counters across all tenant campaign runs, без временного фильтра;
- collections: active cases, overdue actions, promises and disputes;
- каждый endpoint создаёт собственный snapshot, поэтому четыре ответа не являются одной
  атомарной фотографией и их `asOf` могут отличаться.

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

- KPI: customers, active contracts, open collection cases, active campaigns;
- receivables: outstanding, overdue, aging, due today/soon;
- delivery: recipients, sent, retry, failed, skipped;
- collections: active, overdue actions, promises due/overdue/broken;
- each card fails independently; one failed projection does not hide successful cards;
- outstanding/overdue amounts remain grouped by currency;
- overdue presentation may sum already projected aging buckets only inside one currency;
- card links are enabled only when the target route and server filter already exist;
- receivables overdue link is `/receivables?view=invoices&overdue=true`;
- overdue-actions card MUST NOT emit a fake filter until FW6 adds it;
- currency-separated values are never summed across currencies in React.

## Invariants

- React does not derive `paid/outstanding/overdue` or workflow state from raw rows.
- No polling by default; manual refresh invalidates four dashboard keys.
- A card is empty only after a successful response with zero values, not while loading.
- Response freshness is visible when data can be operationally stale.
- Dashboard navigation never invents filters unsupported by target list APIs.
- Delivery labels explicitly describe cumulative tenant counters; UI does not imply
  "today" or "current run" without a backend time/run filter.
- Dashboard money is not implemented on top of lossy `Decimal = number`; the approved
  decimal-string (or reviewed equivalent) contract is a Definition of Ready gate.

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
- independent `asOf` values are rendered without claiming atomic cross-card consistency;
- delivery cumulative-label and per-currency overdue aggregation tests;
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
