# FW4 — Customers

Status: REVIEWED / FW4A READY FOR IMPLEMENTATION

Depends on: FW2 closure, FW3 shared query conventions

Suggested branches: `feat/customer-expected-version-api`,
`feat/frontendweb-fw4a-customer-list`, `feat/frontendweb-fw4b-customer-detail`,
`feat/frontendweb-fw4c-customer-segments`, `feat/frontendweb-fw4d-contracts`

## Цель

Реализовать tenant-scoped реестр клиентов, карточку клиента, контакты и управление
сегментами без N+1 запросов и без хранения бизнес-состояния только на клиенте.

## Backend contract

`CustomerController`, `CustomerSegmentController`, `ROLE_HUMAN`:

- paged/filterable `GET /api/v1/customers` and `GET /api/v1/customers/{id}`;
- create/update/status commands;
- nested email and phone read/write endpoints;
- customer segment membership add/remove;
- paged segment list, detail, create and update.

List projection already includes manager, primary contacts and segment summaries. React
MUST NOT request these values per row. Exact DTO/filters come from
[`frontend-react-api-contract.md`](frontend-react-api-contract.md).

`CustomerResponse`, contact responses and segment responses expose `version`, but customer
update/status/contact patch requests do not carry an expected version. JPA `@Version` alone
does not detect a stale browser form loaded before another already committed update.

Before declaring safe concurrent editing, add expected `version` or `If-Match` to customer,
status and contact patch commands with stable `409` mapping. Segment update already accepts
an optional version; make it required for updates.

## Routes и модули

```text
/customers
/customers/new
/customers/:customerId
/customers/:customerId/{overview|contacts|contracts|invoices|payments|collections}
/customers/segments
/customers/segments/:segmentId
/contracts
/contracts/:contractId
```

Entities: `customer`, `customer-segment`. Features: customer filters/form/status,
contact editor, segment membership.

## FW4A — list

- implementation contract: [`frontendweb-fw04a-customer-list.md`](frontendweb-fw04a-customer-list.md);
- URL-owned page/size/sort/search/status/type/manager/segment/contact/date filters;
- debounced search updates URL and resets page;
- DataTable uses server projection and stable row IDs;
- filter serialization is canonical: empty/default values are removed;
- create/open/status actions reflect backend availability, not inferred tenant roles.

## FW4B — detail and contacts

- tabs use nested routes and deep links;
- overview edit form keeps server DTO separate from form DTO;
- expected version/ETag is captured when the form opens and sent on mutation;
- status change uses confirmation and invalidates detail/list/dashboard;
- contact mutations invalidate customer detail, contact list and customer lists because
  primary contact projection may change;
- `404` remains foreign-tenant safe.
- manager filter/editor requires a bounded user selector and `USER_READ`; without that
  permission, UI preserves an existing manager ID but does not download the tenant directory
  or expose a broken selector.
- selector contract: `GET /api/v1/identity/user-options?search=&status=ACTIVE&page=0&size=20`;
  prefix search is case-insensitive across email/display name, ordering is stable, `size` is
  capped at 50 and results never cross the authenticated tenant boundary;
- the selector response uses `userId`, `label`, `email`, `displayName`, `status` plus standard
  page metadata. React must debounce search and must not call the legacy unpaged
  `/api/v1/identity/users` for customer or collection selectors.
- customer create/update rejects a newly assigned user that is not an active membership of the
  current tenant with `409 INVALID_MANAGER`; an unchanged historical manager remains preservable
  so an unrelated edit does not fail after that membership is blocked.

## FW4C — segments

- paged segment registry and edit form;
- membership add/remove is explicit, pending-safe and invalidates customer/segment views;
- duplicate membership response is mapped by ProblemDetail code;
- segment labels are never joined by client row fan-out.

## FW4D — contracts

Contracts were present in the user process and API matrix but missing from the original
delivery sequence. Implement:

- paged `/contracts` with search/customer/status/externalId/validity/date filters;
- contract detail and customer-scoped contract tab;
- create/update and suspend/activate/close/cancel commands;
- required `version` for update/lifecycle commands and explicit `409` reconciliation;
- invalidation of contract lists/detail, customer contract tab and dependent invoice
  selectors.

## Concurrency and mutation rules

- no optimistic customer/status/contact/contract change without rollback contract;
- submit is disabled per intent, not for unrelated page actions;
- server validation maps `errors` to fields, unknown errors to form alert;
- unsaved form navigation requires confirmation;
- request DTO never contains tenantId.

## Tests

- filter URL round-trip and page reset tests;
- MSW list confirms one customer request and no per-row requests;
- form DTO mapping and field-error tests;
- mutation invalidation matrix tests;
- stale customer/contact/segment and contract version conflict tests;
- direct URL, forbidden/not-found and unsaved-change scenarios;
- ru/kk labels and accessible tab/form tests.

## Implementation order

1. Customer/segment DTO, API, query keys and URL codec.
2. FW4A list/read projection.
3. FW4B detail/contacts/status commands.
4. FW4C segments/membership.
5. FW4D contracts list/detail/lifecycle.
6. Cross-tab invalidation and scenario tests.

## Не входит

Bulk edit/delete, arbitrary query DSL, frontend manager/segment joins and activity feed not
backed by the current API. The unsupported activity tab is not routed.

## Definition of Done

- list remains one paged server request for any page;
- deep links and browser navigation restore filters/tabs;
- mutations refresh all affected projections without global cache clear;
- stale customer/contract forms cannot silently overwrite newer committed state;
- tenant/RBAC/error contracts and frontend CI are green.
