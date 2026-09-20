# FW4D — Customer segments and membership management

Status: IMPLEMENTED

Depends on: FW4A–FW4C (IMPLEMENTED)

Branch: `feat/frontendweb-fw4d-customer-segments`

## 1. Goal

Provide a tenant-scoped segment registry and safe customer membership editor. Segment metadata
uses optimistic concurrency. Membership add/remove commands are individually idempotent and
refresh the customer detail and list projections without a global cache clear.

## 2. Audited backend contract

| Intent | Endpoint | Concurrency/result |
|---|---|---|
| List segments | `GET /api/v1/customer-segments` | paged, fixed filters/sorts |
| Segment detail | `GET /api/v1/customer-segments/{segmentId}` | tenant-safe `404` |
| Create segment | `POST /api/v1/customer-segments` | `201`, unique tenant/code |
| Edit segment | `PATCH /api/v1/customer-segments/{segmentId}` | expected `version` |
| Add membership | `POST /api/v1/customers/{customerId}/segments/{segmentId}` | idempotent `204` |
| Remove membership | `DELETE /api/v1/customers/{customerId}/segments/{segmentId}` | idempotent `204` |

All endpoints require `ROLE_HUMAN`; no finer segment-write permission exists. The frontend MUST
NOT invent one. Requests never contain `tenantId`.

### Backend hardening in FW4D

- add membership uses PostgreSQL `ON CONFLICT DO NOTHING`, eliminating the concurrent
  check-then-insert race;
- segment update and assignment serialize on the segment row; the API still requires the
  caller's optimistic `version`, while the row lock closes the deactivate-vs-assign race;
- concurrent creates map the database unique constraint to stable `409 DUPLICATE_SEGMENT_CODE`;
- a new membership cannot reference an inactive segment and returns `409 INACTIVE_SEGMENT`;
- deactivating a segment preserves existing memberships and history;
- remove remains idempotent; foreign customer/segment identifiers remain safe `404`.

Membership commands do not carry a customer or segment version. Their concurrency contract is
set semantics (present/absent), not optimistic locking. The segment `version` is used only when
editing segment metadata or active state.

## 3. Routes and ownership

```text
/customers/segments
/customers/segments/:segmentId
/customers/:customerId                 membership editor on overview

entities/customer                      DTO, transport and query keys
features/customer-segments             URL state, forms, mutations and UI
pages/customers/CustomerSegmentsPage   composition and safe routing
```

`/customers/segments/:segmentId` renders the registry and opens the selected segment editor after
the tenant-scoped detail request. Browser Back closes the deep-linked editor.

## 4. Registry contract

URL-owned state:

```text
search  optional, trimmed
active  true | false | absent
page    zero-based, default 0
size    20 | 50 | 100, default 20
sort    name/code/createdAt/updatedAt + asc/desc, default name,asc
```

- invalid or unknown URL values canonicalize to defaults;
- changing search/filter/size/sort resets page to zero;
- search is debounced by 300 ms;
- list keeps previous page data while fetching and displays total count;
- rows use segment ID as key and display code, name, active state and updated time;
- no unpaged request or client-side filtering is allowed.

## 5. Create and edit

Create command:

```ts
{ code: string; name: string; description: string | null }
```

Update command:

```ts
{ name: string; description: string | null; active: boolean; version: number }
```

- code is trimmed, uppercased by the server, required, max 80 and immutable after create;
- name is required and max 200; description is optional and max 1000;
- edit captures the detail response version when opened and never increments it in the browser;
- `409 VERSION_CONFLICT` keeps the draft and offers explicit authoritative reload;
- `409 DUPLICATE_SEGMENT_CODE` keeps the create draft and maps to the code field;
- inactive state change requires confirmation because it removes the segment from future
  assignment options, but does not remove existing customers.

Successful create/update replaces the exact detail cache where applicable and invalidates segment
registry/options. Segment update also invalidates customer detail/list projections because segment
name/code labels are embedded there.

## 6. Customer membership editor

The overview segment card exposes `Manage segments`. The dialog loads bounded active options via
the existing segment list contract and retains already assigned inactive segments from the
customer detail as removable, labelled state.

- additions and removals are computed against the authoritative `segmentIds` snapshot;
- save sends only the delta; unchanged memberships produce no requests;
- commands run sequentially to keep failure reconciliation deterministic;
- controls are disabled while saving; double submit cannot duplicate requests;
- after complete success, refetch customer detail and invalidate customer lists plus bounded
  segment option queries; the registry has no membership-derived fields;
- on partial failure, refetch customer detail before presenting the error. The server remains
  authoritative; the UI never assumes rollback across independent HTTP commands;
- `INACTIVE_SEGMENT` refreshes options/detail and asks the user to review the selection;
- `404` uses the same safe missing-resource behavior as customer detail.

Membership mutation intentionally does not invalidate dashboard or contact queries.

## 7. Cache matrix

| Mutation | Detail | Customer lists | Segment lists/options | Contacts | Dashboard |
|---|---:|---:|---:|---:|---:|
| Create segment | — | — | invalidate | — | — |
| Update segment | set segment detail | invalidate | invalidate | — | — |
| Membership delta | refetch customer | invalidate | invalidate options | — | — |

No mutation calls `queryClient.clear()`.

## 8. Errors, dirty state and accessibility

- known `ProblemDetail.code` values drive localized messages; opaque `5xx` detail is masked;
- field errors bind through `FormField`; unknown validation errors use a form alert;
- dirty create/edit/membership dialogs protect close, SPA navigation and page unload;
- dialogs use shared focus trap and restore focus; status is not communicated by color alone;
- Russian and Kazakh messages are complete.

## 9. Required tests

- URL codec round-trip, canonical defaults and page reset;
- transport method/path/query/body tests with encoded IDs and no tenant ID;
- registry loading, pagination, filtered empty, `403` and safe `404` scenarios;
- create normalization and duplicate-code mapping;
- update sends captured version and `VERSION_CONFLICT` never retries;
- PostgreSQL integration covers concurrent-safe/repeated add, repeated remove, inactive rejection
  and tenant isolation;
- membership delta sends only additions/removals;
- partial failure performs authoritative refetch;
- invalidation matrix excludes contacts/dashboard;
- direct segment URL, ru/kk labels and accessible dialog/form controls.

## 10. Out of scope

Bulk segment operations, rule-based/dynamic segments, segment deletion, customer-count analytics,
drag ordering, arbitrary query DSL and changing a segment code.

## 11. Definition of Done

- registry and deep-linked editor use bounded tenant-scoped APIs;
- stale segment metadata cannot overwrite a newer committed version;
- membership add/remove are safe under retry and concurrent duplicate POST;
- inactive segments cannot receive new memberships but existing membership is preserved;
- customer and segment projections converge through the documented targeted refreshes;
- frontend typecheck/tests/build and full repository CI are green;
- documentation status is `IMPLEMENTED` and the narrow PR is merged.
