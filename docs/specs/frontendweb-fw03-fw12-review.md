# FW3–FW12 specification review against current project

Status: REVIEWED / CORRECTIONS APPLIED

Reviewed baseline: `main@02515d0`

Reviewed sources:

- controllers, request/response records and query services for dashboard, customer,
  contract, receivable, collection, campaign, communication, template, import, file and
  identity packages;
- current frontend source after FW2B;
- frontend API contract, screen/API matrix and user-process documents;
- representative PostgreSQL/security/frontend API integration tests.

## Executive conclusion

The original FW3–FW12 set had the correct product sequence and most endpoint names, but
was not yet implementation-ready for all slices. It inherited optimistic readiness claims
from the screen/API matrix that are contradicted by current code. This review changes
readiness from a binary label to explicit backend gates and removes frontend workarounds.

## Findings and decisions

| ID | Severity | Slice | Evidence in current code | Decision |
|---|---|---|---|---|
| R-01 | BLOCKER | all | `main` contains FW2A/B but not FW2C/D | recover FW2C/D and close FW2E before FW3 |
| R-02 | BLOCKER | FW3/FW5 | public `BigDecimal` becomes JS `number`; `Decimal = number` | approve a money transport ADR; prefer decimal strings before financial UI |
| R-03 | HIGH | FW3 | summary has no overdue KPI; overdue exists only as per-currency aging buckets | do not invent cross-currency KPI; allow presentation-only per-currency bucket sum |
| R-04 | HIGH | FW3/FW6 | collection list has no overdue-action filter/sort | no dead filtered link; add fixed operational filter API in FW6 prerequisite |
| R-05 | BLOCKER | FW4 | Contracts are in user process/API but absent from FW3–FW12 delivery slices | add FW4D contract list/detail/lifecycle |
| R-06 | HIGH | FW4/FW6 | manager/assignee IDs need a selector; directory requires `USER_READ` and is unpaged | add bounded identity lookup or permission-aware deferment; never load unbounded directory |
| R-07 | BLOCKER | FW5 | invoice/payment rows have customer/contract UUIDs but no labels | add bounded batch labels to list projections; forbid row lookups |
| R-08 | HIGH | FW5 | allocation list endpoints return unbounded `List` | page allocation history or document/enforce a hard domain limit |
| R-09 | HIGH | FW6 | queue lacks next-action filters; case children/timeline return unbounded lists | add due filters/sort and page or hard-bound child history |
| R-10 | BLOCKER | FW7 | no direct run detail endpoint; campaign detail omits selection and list metadata | add screen-oriented campaign/run detail projections |
| R-11 | MEDIUM | FW8 | API exposes attempt count/current error, not attempt history | call UI delivery state detail, not attempt history; do not synthesize history |
| R-12 | HIGH | FW9 | no `GET /templates/{id}`; detail after reload would scan paged list | add direct template detail endpoint |
| R-13 | MEDIUM | FW9 | builder catalogue/assets are unpaged lists and content fields lack explicit size limits | enforce tenant limits/request bounds or add paging before scale acceptance |
| R-14 | HIGH | FW10 | create is `202` but processing currently executes synchronously before response | poll only if returned status is non-terminal; do not promise background execution |
| R-15 | BLOCKER | FW10 | `/imports/{id}/errors` synthesizes one generic batch error | implement durable record/field diagnostics with paging/masking/retention |
| R-16 | BLOCKER | FW11 | no files list; public `FileMetadata` also includes tenantId | add public list/detail DTO without tenantId/storage internals |
| R-17 | HIGH | FW12 | users and invitations return unbounded lists | add fixed-filter paging before production tenant administration |
| R-18 | CONTRACT | FW12 | frontend document says `membershipId`; actual JSON field is `id` | normalize adapter now and migrate backend DTO to explicit `membershipId` in reviewed baseline |
| R-19 | MEDIUM | all | status/channel types are documented as arbitrary strings | centralize known-value maps with unknown fallback; never crash on additive enum value |
| R-20 | HIGH | FW4 | customer/status/contact requests omit expected version although responses expose it | add explicit expected-version contract for stale-form protection |
| R-21 | BLOCKER | FW7 | each prepare call creates a new run; command has no idempotency key | add idempotent prepare intent before network replay is safe |
| R-22 | HIGH | FW9/FW12 | draft template and role updates expose no revision token | add version/ETag precondition or explicitly reject multi-editor safety claim |
| R-23 | HIGH | FW10 | schema/profile editors lack direct version detail and revision-safe update contract | add reloadable detail DTOs and revision preconditions |
| R-24 | HIGH | FW12 | member/role detail routes have no direct detail endpoint | add direct tenant-scoped detail or remove deep routes; list scanning is forbidden |

## Corrected readiness

| Slice | Readiness after review | Required closure |
|---|---|---|
| FW3 | READY AFTER P0 | FW2 closure and money transport decision |
| FW4 | READY WITH SPLIT | add FW4D contracts; bounded manager lookup is conditional |
| FW5 | BACKEND PROJECTION GATE | list labels, allocation bound, money contract |
| FW6 | API HARDENING GATE | operational next-action filters and bounded assignee lookup |
| FW7 | BACKEND DETAIL GATE | campaign and run detail projections |
| FW8 | READY AFTER FW7 | current-state monitoring only |
| FW9 | BACKEND DETAIL GATE | direct template detail and bounded builder resources |
| FW10 | BACKEND DIAGNOSTICS GATE | durable record errors; conditional polling |
| FW11 | BACKEND LIST GATE | paged registry and safe public DTO |
| FW12 | PAGING GATE | paged members/invitations and membership field normalization |

## Cross-cutting decisions

### Money

Before FW3/FW5, add a reviewed API decision for monetary decimals. Preferred contract:

```ts
type DecimalString = string; // canonical plain decimal, no exponent
```

Backend serializes monetary `BigDecimal` as strings and accepts the same request shape,
with documented precision/scale. The intentional OpenAPI baseline change must be reviewed.
Until then React MUST NOT perform arithmetic on parsed JS numbers.

### Detail routes

A reloadable `/:id` page requires a direct tenant-scoped detail query. Scanning list pages,
depending on router state, or trusting a stale list cache is forbidden.

### Display labels

High-volume list rows must contain required labels in their projection. Fetching customer,
contract, user, template or invoice data once per row is forbidden. Selection controls use
bounded server search, not a full tenant directory download.

### Paging

Operational history and tenant-owned registries are paged. Small reference catalogues may
remain lists only with a documented server-side maximum and query-count test.

### Polling

HTTP `202` alone does not prove asynchronous processing. Polling is driven by an explicit
non-terminal response status and stops immediately for terminal responses.

### Optimistic concurrency

JPA `@Version` alone does not protect a stale browser form when the request omits the
version: a later request loads the latest row and can overwrite it. Editable DTOs must carry
an expected version (or `If-Match` ETag), and stale writes map to stable `409` before the UI
claims conflict-safe editing.

## Test gaps to close with implementation

- no dedicated dashboard API integration test was found;
- no direct campaign/run detail contract exists to test;
- collection projection test does not cover overdue filter because it does not exist;
- import error test cannot prove record-level durability because storage does not exist;
- file tests cover ID operations, not a tenant-paged registry;
- identity tests cover correctness, not paging/large-tenant boundedness.

Each prerequisite backend PR must add PostgreSQL tenant isolation, paging/sort validation,
stable ProblemDetail codes and OpenAPI compatibility coverage before its frontend slice.

## Evidence map

| Domain | Current implementation inspected |
|---|---|
| Dashboard | `DashboardController`, `DashboardQueryService` |
| Customers/contracts | `CustomerController`, `CustomerQueryService`, `CustomerService`, `ContractController` |
| Receivables | `ReceivableController`, `ReceivableQueryService`, allocation integration tests |
| Collections | `CollectionController`, `CollectionQueryService`, blocker-closure projection test |
| Campaigns/messages | `CampaignController`, `CampaignService`, `CampaignFrontendQueryService`, `MessageController`, `MessageQueryService` |
| Templates | `TemplateManagementController`, `TemplateBuilderController`, `TemplateFrontendQueryService`, `TemplateAssetService` |
| Imports | `ImportBatchController`, `ImportBatchService`, `ImportHistoryController`, `FrontendHistoryQueryService` |
| Files | `FileController`, `FileMetadata`, `StoredFileRepository`, file integration tests |
| Administration | current-user, membership, invitation, role and permission controllers plus security-management tests |

This report reviews the public/application boundary rather than JPA entities alone. Where a
DTO is currently a nested Java record, the required frontend contract still belongs in the
OpenAPI/public API and must not be inferred from persistence fields.

## Recommended execution order after review

```text
P0 FW2C/D recovery + FW2E
P0 money decimal ADR/API contract
FW3 Dashboard
FW4A Customers list -> FW4B detail/contacts -> FW4C segments -> FW4D contracts
FW5 backend projections/allocation paging -> FW5 UI
FW6 operational filters/assignee lookup -> FW6 UI
FW9 template detail/bounds -> FW9 UI
FW7 campaign/run detail -> FW7 UI -> FW8 monitoring
FW10 diagnostics -> FW10 UI
FW11 registry -> FW11 UI
FW12 identity paging -> FW12 UI
```

FW9 may run in parallel after P0. FW10/FW11/FW12 backend closures may also proceed in
parallel, but frontend PRs start only after their contracts are merged and the OpenAPI
baseline is intentionally updated.
