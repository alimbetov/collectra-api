# FW4E — Contracts registry, detail and lifecycle

Status: IMPLEMENTED

Depends on: FW4A–FW4D (IMPLEMENTED), Slice 9A2 contract domain (IMPLEMENTED)

Branch: `feat/frontendweb-fw4e-contracts`

## 1. Goal

Deliver the tenant-scoped contract registry, contract detail, customer-scoped contract tab and
safe create/update/lifecycle commands. Metadata updates and lifecycle commands use the exact
server `version`; React never edits `status` directly.

## 2. Audited backend contract and hardening

| Intent | Endpoint | Result/concurrency |
|---|---|---|
| List | `GET /api/v1/contracts` | paged fixed filters and sorts |
| Detail | `GET /api/v1/contracts/{id}` | tenant-safe `404` |
| Create | `POST /api/v1/contracts` | `201`, tenant/external ID unique |
| Update | `PUT /api/v1/contracts/{id}` | required expected `version` |
| Lifecycle | `POST /api/v1/contracts/{id}/{action}` | required expected `version` |

All endpoints require `ROLE_HUMAN`; requests never contain `tenantId`.

FW4E backend hardening:

- update and lifecycle request versions change from primitive `long` to required `Long`; an
  omitted version returns validation `400` instead of silently becoming version zero;
- concurrent create uses the PostgreSQL unique constraint and maps it to stable
  `409 DUPLICATE_EXTERNAL_ID`;
- list/detail responses add `customerExternalId` and `customerDisplayName`; page labels are
  resolved through one tenant-scoped batch load, never one request/query per row;
- validity filter indexes are added by Liquibase `038`;
- stale persistence races remain protected by JPA `@Version` and map to `409 VERSION_CONFLICT`.

## 3. Lifecycle state machine

| Current | Allowed actions | Terminal |
|---|---|---:|
| `ACTIVE` | `suspend`, `close`, `cancel` | no |
| `SUSPENDED` | `activate`, `close`, `cancel` | no |
| `CLOSED` | none | yes |
| `CANCELLED` | none | yes |

The UI renders only allowed commands, requires explicit confirmation and sends the version from
the displayed detail. `INVALID_STATE_TRANSITION` and `VERSION_CONFLICT` trigger authoritative
reload; commands are never automatically retried.

## 4. Routes and module ownership

```text
/contracts
/contracts/:contractId
/customers/:customerId/contracts

entities/contract            DTO, API and query keys
features/contracts           filters, form, lifecycle, invalidation and shared UI
pages/contracts              registry and detail composition
```

Contracts are a first-class workspace navigation item. The customer tab is a filtered view of
the same server resource and links to canonical `/contracts/:contractId` detail.

## 5. Registry contract

Canonical URL state:

```text
search, status, externalId
validFrom, validTo
createdFrom, createdTo        YYYY-MM-DD in URL, UTC day bounds on wire
page                          zero-based
size                          20 | 50 | 100
sort                          fixed allowlist; default createdAt,desc
```

- invalid URL values canonicalize to defaults;
- changing filter, size or sort resets page zero;
- invalid date ranges do not issue a request;
- search is debounced 300 ms and previous page data stays visible while fetching;
- the frontend always sends page/size/sort because backend size defaults to 50;
- rows display contract number/external ID, customer, status, validity and renewal date;
- empty, loading, `403`, safe `404` and retry states are explicit.

## 6. Detail and mutation forms

Create fields: customer, immutable external ID, number, validity dates, renewal date and optional
JSON custom fields. Global create uses the bounded customer list (`size=20`); customer-tab create
locks the known customer and performs no selector request.

Update excludes customer and external ID changes and sends:

```ts
{
  contractNumber: string;
  validFrom: LocalDate;
  validTo: LocalDate | null;
  renewalDate: LocalDate | null;
  customFields: unknown | null;
  version: number;
}
```

The form captures the detail version when opened. `VERSION_CONFLICT` keeps the draft and offers
reload; `INVALID_RANGE` and duplicate external ID have localized messages. Dirty dialogs protect
close, SPA navigation and page unload.

## 7. Query and invalidation contract

| Mutation | Exact detail | All contract lists/customer tab | Dashboard summary |
|---|---:|---:|---:|
| Create | — | invalidate | invalidate |
| Update metadata | set authoritative response | invalidate | — |
| Lifecycle | set authoritative response | invalidate | invalidate |

The dashboard is refreshed for create/lifecycle because `activeContracts` changes. Contact,
segment and unrelated customer queries are not invalidated. No mutation calls `clear()`.

## 8. Required tests

- URL codec, defaults, date conversion, page reset and invalid-range guard;
- transport path/query/body, encoded IDs, required version and absence of tenant ID;
- form normalization, JSON/date validation and captured version;
- lifecycle matrix and command version;
- mutation invalidation matrix;
- registry, direct detail URL, `403`, tenant-safe `404`, customer tab;
- PostgreSQL integration covers required version, stale update/lifecycle, duplicate mapping,
  transitions, tenant isolation and enriched customer labels;
- ru/kk and accessible dialog/form/action labels.

## 9. Out of scope

Contract deletion, arbitrary status editing, bulk lifecycle commands, attachments, amendment
history, invoice implementation (FW5) and arbitrary query DSL.

## 10. Definition of Done

- registry/detail/customer tab consume bounded tenant-scoped endpoints;
- metadata and lifecycle cannot silently overwrite a newer version;
- terminal lifecycle states expose no further actions;
- customer labels have no N+1 path;
- targeted projections converge after every command;
- frontend typecheck/tests/build and full repository CI are green;
- status is `IMPLEMENTED` and the narrow PR is merged.
