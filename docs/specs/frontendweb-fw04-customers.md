# FW4 — Customers

Status: DRAFT / API READY

Depends on: FW2 closure, FW3 shared query conventions

Suggested branches: `feat/frontendweb-fw4a-customer-list`,
`feat/frontendweb-fw4b-customer-detail`, `feat/frontendweb-fw4c-customer-segments`

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

## Routes и модули

```text
/customers
/customers/new
/customers/:customerId
/customers/:customerId/{overview|contacts|contracts|invoices|payments|collections|activity}
/customers/segments
/customers/segments/:segmentId
```

Entities: `customer`, `customer-segment`. Features: customer filters/form/status,
contact editor, segment membership.

## FW4A — list

- URL-owned page/size/sort/search/status/type/manager/segment/contact/date filters;
- debounced search updates URL and resets page;
- DataTable uses server projection and stable row IDs;
- filter serialization is canonical: empty/default values are removed;
- create/open/status actions reflect backend availability, not inferred tenant roles.

## FW4B — detail and contacts

- tabs use nested routes and deep links;
- overview edit form keeps server DTO separate from form DTO;
- status change uses confirmation and invalidates detail/list/dashboard;
- contact mutations invalidate customer detail, contact list and customer lists because
  primary contact projection may change;
- `404` remains foreign-tenant safe.

## FW4C — segments

- paged segment registry and edit form;
- membership add/remove is explicit, pending-safe and invalidates customer/segment views;
- duplicate membership response is mapped by ProblemDetail code;
- segment labels are never joined by client row fan-out.

## Concurrency and mutation rules

- no optimistic customer status/contact/membership change without rollback contract;
- submit is disabled per intent, not for unrelated page actions;
- server validation maps `errors` to fields, unknown errors to form alert;
- unsaved form navigation requires confirmation;
- request DTO never contains tenantId.

## Tests

- filter URL round-trip and page reset tests;
- MSW list confirms one customer request and no per-row requests;
- form DTO mapping and field-error tests;
- mutation invalidation matrix tests;
- direct URL, forbidden/not-found and unsaved-change scenarios;
- ru/kk labels and accessible tab/form tests.

## Implementation order

1. Customer/segment DTO, API, query keys and URL codec.
2. FW4A list/read projection.
3. FW4B detail/contacts/status commands.
4. FW4C segments/membership.
5. Cross-tab invalidation and scenario tests.

## Не входит

Bulk edit/delete, arbitrary query DSL, frontend manager/segment joins and activity feed not
backed by the current API.

## Definition of Done

- list remains one paged server request for any page;
- deep links and browser navigation restore filters/tabs;
- mutations refresh all affected projections without global cache clear;
- tenant/RBAC/error contracts and frontend CI are green.
