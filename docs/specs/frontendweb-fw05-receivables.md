# FW5 — Receivables and payments

Status: REVIEWED / BACKEND PROJECTION AND MONEY GATES

Depends on: FW4 customers/contracts, approved decimal transport, receivable list projection closure

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

### Required backend closure

- enrich `InvoiceItem` with `customerDisplayName` and `contractNumber` and `PaymentItem`
  with `customerDisplayName` using bounded batch queries; current DTO exposes only UUIDs;
- page allocation history endpoints or enforce and document a hard per-payment/invoice
  allocation limit; current endpoints return unbounded lists;
- apply the approved decimal-string (or reviewed equivalent) transport to monetary fields;
- create invoice/payment has no idempotency key: frontend must not auto-replay an ambiguous
  request; a follow-up backend idempotency contract is preferred.

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
- customer/contract labels come from the server list projection and links preserve context;
- create form sends typed Decimal/date fields without locale-formatted payloads;
- detail displays backend-calculated balances and allocation history.

## FW5B — payments and allocations

- server-paged payment list/detail/create;
- allocation form selects an eligible invoice through bounded `GET /invoices` search and
  constrains customer/currency consistently with backend validation;
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
- monetary form values remain canonical decimal strings; no floating-point arithmetic;
- create invoice/payment network ambiguity enters a reconciliation state and searches by
  the submitted external ID; it is not blindly replayed.

## Tests

- list URL codecs and server paging;
- currency/date/businessDate formatting;
- create invoice/payment validation mapping;
- commandId stability across network retry;
- allocation/reversal invalidation graph;
- 409 reconciliation and double-submit prevention;
- cross-currency fixture proves no combined total.
- one list request supplies customer/contract labels and performs no row lookups;
- allocation history paging/hard-limit boundary test;
- ambiguous create reconciliation test.

## Implementation order

1. Backend money/projection/allocation-history closure and OpenAPI update.
2. DTO/API/query key and decimal form boundary.
3. Invoice list/detail/create.
4. Payment list/detail/create.
5. Allocation and reversal workflow.
6. Conflict/ambiguity and integration-style MSW scenarios.

## Не входит

Frontend accounting ledger, automatic allocation, bulk payment import, optimistic balances
and exchange-rate conversion.

## Definition of Done

- backend remains the sole financial source of truth;
- list labels are server-resolved without N+1 and money crosses the boundary losslessly;
- allocation replay is idempotent from the UI intent perspective;
- all affected cache projections refresh deterministically;
- frontend CI and critical user scenarios are green.
