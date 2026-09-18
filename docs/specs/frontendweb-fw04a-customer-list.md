# FW4A — Customer list implementation contract

Status: IMPLEMENTATION READY

Depends on: FW2 foundation, FW3 query conventions, `main` containing PR #87 and PR #89.

Implementation branch: `feat/frontendweb-fw4a-customer-list`

## 1. Goal

Replace the `/customers` placeholder with a production-ready tenant-scoped customer registry.
The page owns its complete query state in the URL, performs exactly one customer-list request
per settled filter state and renders only the enriched projection returned by the backend.

FW4A is read-only. Customer creation, detail, contact/status mutations and segment management
belong to FW4B/FW4C. The list must not introduce temporary write behaviour or client-side joins.

## 2. Verified backend contract

Primary endpoint:

```http
GET /api/v1/customers
```

Query parameters:

| Parameter | Type | Default | Contract |
|---|---|---|---|
| `search` | string | absent | trimmed, display-name contains or external-ID prefix |
| `status` | `ACTIVE | INACTIVE | BLOCKED | ARCHIVED` | absent | exact match |
| `customerType` | `INDIVIDUAL | COMPANY` | absent | exact match |
| `managerId` | UUID | absent | exact active/historical manager ID |
| `segmentId` | UUID | absent | membership filter |
| `externalId` | string | absent | exact normalized external ID |
| `email` | string | absent | exact normalized email |
| `phone` | string | absent | exact normalized phone |
| `createdFrom` | RFC 3339 Instant | absent | inclusive |
| `createdTo` | RFC 3339 Instant | absent | inclusive |
| `page` | integer | `0` | zero based, minimum `0` |
| `size` | integer | `50` | `1..200`; UI choices `25/50/100` |
| `sort` | string | `createdAt,desc` | allow-list below |

Allowed sort fields are exactly `createdAt`, `updatedAt`, `displayName`, `externalId`; direction
is `asc` or `desc`. The backend appends `id` in the same direction as the deterministic tie-break.

Response is `PageDto<CustomerListItemDto>` with `items`, `page`, `size`, `totalElements`,
`totalPages` and `hasNext`. Every item already contains primary email/phone, manager label and
segment summaries. React MUST NOT fetch any of those values per row.

Bounded filter option endpoints:

```http
GET /api/v1/identity/user-options?search=&status=ACTIVE&page=0&size=20
GET /api/v1/customer-segments?search=&active=true&page=0&size=20&sort=name,asc
```

The manager endpoint requires `USER_READ`. A `403` disables only the manager selector and does
not redirect the entire customer page. The legacy unpaged `/api/v1/identity/users` is forbidden
for FW4A. Customer and segment list endpoints currently require authenticated `ROLE_HUMAN` and
do not expose a separate `CUSTOMER_READ` permission.

## 3. Frontend modules

Create the following vertical slice without cross-layer imports:

```text
frontendweb/src/entities/customer/
  api/customer.api.ts
  api/customer.api.test.ts
  api/customer.queries.ts
  model/customer.types.ts

frontendweb/src/features/customer-list/
  model/customer-list-filters.ts
  model/customer-list-filters.test.ts
  ui/CustomerFilters.tsx
  ui/CustomerTable.tsx

frontendweb/src/pages/customers/
  CustomersPage.tsx
  CustomersPage.test.tsx
```

Allowed dependency direction remains `app -> pages -> features -> entities -> shared`.
The route replaces only the current `/customers` placeholder.

## 4. DTO and query-key contract

`CustomerListItemDto`, `SegmentSummaryDto`, `CustomerPageDto` and `CustomerListQuery` mirror the
reviewed API contract. Status and type are closed TypeScript unions, not arbitrary strings.

Query keys are tenant-session-local because logout clears the entire `QueryClient`:

```ts
customerKeys.all                         // ['customers']
customerKeys.list(canonicalQuery)        // ['customers', 'list', canonicalQuery]
customerKeys.managerOptions(search,page) // ['identity', 'user-options', ...]
customerKeys.segmentOptions(search,page) // ['customer-segments', 'options', ...]
```

The canonical query object contains no `undefined`, empty strings or default values. Its property
order is fixed by the codec, so equivalent URLs produce identical query keys.

## 5. URL state contract

The URL is the authoritative state for committed filters, page, page size and sort. Component
state is permitted only for the debounced search draft and open/closed filter UI.

Canonical rules:

- omit `page=0`, `size=50` and `sort=createdAt,desc`;
- trim text; remove empty values;
- accept only declared enum, UUID, page-size and sort values;
- invalid URL values fall back to defaults and are removed on the next user-driven update;
- changing any filter, size or sort resets `page` to `0`;
- changing only page preserves every other parameter;
- search commits after 300 ms; submit/Enter commits immediately;
- serialization order is fixed: filters, `page`, `size`, `sort`;
- browser back/forward reconstructs controls and query without a shadow store.

Dates are represented in the URL and API as ISO Instants. `datetime-local` controls convert to and
from the user timezone supplied by `useI18n`; no JVM/browser-default timezone contract is assumed.
If `createdFrom > createdTo`, the page shows a local validation message and does not send a request.

## 6. Page behaviour

Page header shows localized title and total count when loaded. FW4A does not show a non-functional
“New customer” action.

Filter surface:

- quick search;
- status and customer type selects;
- advanced external ID, email, phone and created range;
- debounced bounded manager and segment selectors;
- clear-all action returning to canonical `/customers`.

Table columns:

```text
Display name / external ID
Type
Status
Primary email
Primary phone
Manager
Segments
Updated at
```

Rows use `customer.id` as the React key. Missing values render the localized em dash. Segment chips
come from `item.segments`; `segmentIds` are not joined client-side. Status badges use deterministic
tones. `updatedAt` is formatted through the shared `Intl` formatter and current user timezone.

Pagination is server-driven. The UI uses response `page`, `totalPages`, `totalElements`, `hasNext`
and never slices `items` locally. If a deletion or backend data change leaves the URL page beyond
the last page, the page canonically navigates to the last existing page once, without a retry loop.

## 7. Loading and error semantics

- first load: accessible spinner/label; do not render a false empty state;
- refetch: keep previous rows visible and expose a bounded refreshing indicator;
- empty unfiltered list and empty filtered result use different localized messages;
- `401` remains owned by the shared refresh/session-loss flow;
- page-level `403` navigates to `/forbidden`;
- manager-option `403` disables that selector only;
- `400 INVALID_REQUEST` from stale/manual URL input renders `ProblemDetailPanel` and clear-filters;
- `5xx` details remain hidden by shared error handling; retry refetches the current query once per
  user action, never automatically in a business retry loop.

## 8. Accessibility and localization

- every control has a visible label; filter groups use `fieldset`/`legend` where appropriate;
- table has a localized caption and semantic column headers;
- pagination labels and live page status are localized, not component defaults;
- search result count and refetch state use a polite live region;
- keyboard users can reach filter, clear, sort and pagination actions in deterministic order;
- all new strings are added to both `ru` and `kk` catalogs in the same commit;
- customer enum labels are localized and raw backend enum values are never shown as UI text.

## 9. Tests

### Unit

- URL parse/serialize round-trip and stable parameter ordering;
- defaults are omitted and invalid values are rejected safely;
- filter/size/sort changes reset page while page navigation preserves filters;
- debounce commits once and browser navigation rehydrates the draft;
- date range validation prevents the customer request;
- query serialization encodes reserved characters and never emits `tenantId`.

### API/MSW

- exact request parameters and enriched response mapping;
- one customer request renders manager/contact/segments with zero per-row requests;
- backend `ProblemDetail` remains an `ApiError`;
- manager option `403` is isolated from the primary list.

### Component/page

- loading, rows, filtered empty, unfiltered empty and error/retry states;
- localized status/type labels and updated-at formatter;
- page, size and sort interactions update URL and refetch;
- direct URL and browser back/forward restore filters;
- page-level `403` redirects to `/forbidden`;
- no inaccessible unlabeled form control or table.

## 10. Implementation order

1. DTOs, API serialization and query keys.
2. Pure URL codec with unit tests.
3. Filter controls and bounded option loaders.
4. Table projection and localized enum/status rendering.
5. Page orchestration, router replacement and pagination.
6. MSW/page tests, styles and both locale catalogs.
7. `npm run typecheck`, `npm run test:ci`, `npm run build`, then repository CI.

## 11. Out of scope

- create/edit/status/contact mutations;
- customer detail route and tabs;
- segment CRUD or membership changes;
- bulk actions/export/delete;
- generic query DSL;
- client-side joins, unbounded directory downloads or activity feed;
- backend schema/API changes unless implementation exposes a proven contract defect.

## 12. Definition of Done

- `/customers` is no longer a placeholder and is reload/deep-link safe;
- all supported filters, page, size and allow-listed sort are URL-owned and canonical;
- each settled list state issues one paged customer request only;
- no row fan-out or client-side business-state reconstruction exists;
- tenant/session/RBAC/error boundaries reuse shared infrastructure;
- ru/kk, accessibility and responsive states are present;
- unit, API/MSW, page, typecheck, build and repository CI are green;
- implementation PR is limited to FW4A and may merge independently of FW4B–FW4D.
