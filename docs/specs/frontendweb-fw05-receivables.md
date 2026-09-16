# FW5 — Receivables and payments

Status: DRAFT / API READY

Depends on: FW4 customer navigation

Suggested branches: `feat/frontendweb-fw5a-receivables`,
`feat/frontendweb-fw5b-payments-allocations`

## Цель

Дать оператору server-paged invoices/payments workspace с деталями, регистрацией
платежа, allocation/reversal и явным отображением authoritative финансового состояния.

## Backend contract

`ReceivableController`, `ROLE_HUMAN`:

- `GET/POST /api/v1/invoices`, `GET /api/v1/invoices/{id}`;
- `GET/POST /api/v1/payments`, `GET /api/v1/payments/{id}`;
- invoice/payment allocations;
- allocate and reverse allocation commands.

Backend owns `paidAmount`, `outstandingAmount`, `paymentStatus`, `overdue`,
`daysOverdue` and `businessDate`.

## Routes

```text
/receivables?view=invoices|payments
/receivables/invoices/new
/receivables/invoices/:invoiceId
/receivables/payments/new
/receivables/payments/:paymentId
```

Query keys are separated by invoice/payment list/detail/allocations and contain normalized
server filters.

## FW5A — invoices

- server-paged list with customer/contract/status/overdue/date/amount filters;
- amounts always render with response currency;
- customer and contract links preserve origin context;
- create form sends typed Decimal/date fields without locale-formatted payloads;
- detail displays backend-calculated balances and allocation history.

## FW5B — payments and allocations

- server-paged payment list/detail/create;
- allocation form selects an eligible invoice through bounded server search;
- one `commandId` is created for an allocation intent and reused for replay;
- reversal requires reason/confirmation and uses backend command semantics;
- successful allocation/reversal invalidates payment, invoice, both allocation lists,
  affected list projections and dashboard receivables.

## Financial invariants

- never calculate authoritative outstanding/payment status in React;
- never sum different currencies into one KPI;
- do not apply optimistic balance changes;
- displayed `businessDate` is distinct from browser current date;
- `409` keeps entered values and offers refresh/reconcile, never blind retry;
- Decimal values remain strings at form/transport boundary if OpenAPI declares decimal
  string semantics; no floating-point arithmetic.

## Tests

- list URL codecs and server paging;
- currency/date/businessDate formatting;
- create invoice/payment validation mapping;
- commandId stability across network retry;
- allocation/reversal invalidation graph;
- 409 reconciliation and double-submit prevention;
- cross-currency fixture proves no combined total.

## Implementation order

1. DTO/API/query key and money form boundary.
2. Invoice list/detail/create.
3. Payment list/detail/create.
4. Allocation and reversal workflow.
5. Conflict and integration-style MSW scenarios.

## Не входит

Frontend accounting ledger, automatic allocation, bulk payment import, optimistic balances
and exchange-rate conversion.

## Definition of Done

- backend remains the sole financial source of truth;
- allocation replay is idempotent from the UI intent perspective;
- all affected cache projections refresh deterministically;
- frontend CI and critical user scenarios are green.
