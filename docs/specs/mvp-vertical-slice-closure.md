# Collectra MVP Vertical-Slice Closure

Status: EXECUTION SPEC
Branch: `feat/mvp-vertical-slice-closure`
Baseline: `2548f0c1ecfc72e8a4f3216785be29b14f32856f`

## Goal

Close existing backend capabilities through the web UI before adding unrelated backend scope.

MVP journey:

```
Integration Setup -> Service Client -> Schema + Mapping -> Integration Source
-> Readiness -> Activate -> Ingestion -> Import/validation
-> Customer -> Contract -> Receivable -> Collection
-> Campaign -> Template -> Message/Delivery -> Monitoring -> Analytics/Report
```

A capability is DONE only when the UI-to-database vertical slice is usable, authorized, tested, observable, and green on the exact merge-candidate SHA.

## Delivery invariants

1. Backend owns lifecycle, permissions, validation, readiness and stable problem codes.
2. Frontend does not duplicate backend lifecycle/readiness rules.
3. Every tenant resource is tenant-scoped server-side; UI filtering is never a security boundary.
4. Every mutation has pending/disabled, validation, conflict, forbidden, failure and success UX.
5. Unbounded collections use server pagination and deterministic sorting.
6. IDs inserted into URLs are encoded.
7. Long operations expose durable status; polling is bounded and stops at terminal state/unmount.
8. Secrets are server-generated and only one-time disclosed where the contract permits it.
9. No mock/fake/stub/hardcoded data in a DONE production path.
10. No merge without green backend and frontend CI on the exact candidate SHA.
11. OpenAPI compatibility must remain green.
12. New tenant endpoints require authorization and tenant-isolation integration tests.

Priority: close existing verticals first; create backend contracts only when a required UI action has no safe contract.

---

# V0 — Foundation merge gate

Finish `feat/integration-contract-foundation`.

Required:
- synchronize with current `main` and resolve conflicts;
- record exact candidate SHA;
- `mvn spotless:check`;
- `bash scripts/test-slice-07.sh unit`;
- `mvn clean verify -Pslice10a-coverage`;
- frontend `npm ci`, typecheck, tests and production build;
- IntegrationSource lifecycle/security, ServiceClient lifecycle/security and Mapping metadata tests execute;
- OpenAPI compatibility green;
- merge to `main` only after all required jobs are green.

Exit: `main` contains I0/I1 foundation and is green.

---

# V1 — Integration Setup Center UI

Routes:

```
/integrations
/integrations/service-clients
/integrations/service-clients/new
/integrations/service-clients/:clientId
/integrations/sources
/integrations/sources/new
/integrations/sources/:sourceId
/integrations/sources/:sourceId/readiness
```

All routes use permission guards; backend 403 remains authoritative.

## V1.1 Service Clients

Reuse:
- GET `/api/v1/integration/service-clients`
- GET `/api/v1/integration/service-clients/{id}`
- GET `/api/v1/integration/service-client-scopes`
- POST `/api/v1/integration/service-clients`
- POST `/{id}/rotate-secret`
- POST `/{id}/credentials/{credentialId}/activate`
- POST `/{id}/block`
- POST `/{id}/unblock`

Screens:
- list with status, identity, scopes and lifecycle actions;
- create form populated from backend scope catalogue;
- detail;
- one-time secret modal with explicit copy/acknowledge;
- rotation and credential activation;
- block/unblock confirmation.

A disclosed secret must never be placed in React Query cache, localStorage, URL, logs or durable UI history.

## V1.2 Integration Sources

Add entity API/query/mutation layer for the existing source endpoints.

Screens:
- source list;
- create;
- detail/edit;
- readiness;
- activate/suspend/archive.

Selectors load tenant-owned Service Clients, source schemas and mappings.

Optimistic concurrency:
- send `version`;
- map `VERSION_CONFLICT` to conflict UX;
- allow reload; never silently overwrite.

## V1.3 Readiness

React renders backend readiness; it does not calculate readiness.

Response must support:
- overall READY/BLOCKED;
- stable check code/state;
- resource type and safe reference;
- stable explanation/message arguments;
- recommended action/navigation where applicable.

Extend the I1 DTO if necessary before the screen is DONE.

Acceptance:
- tenant admin can create a client, acknowledge secret, create a source, inspect blockers and activate;
- foreign tenant IDs disclose nothing;
- read-only user cannot mutate;
- direct URL access respects permission guards;
- 401 refresh and 403 UX verified;
- no mocks.

---

# V2 — Real source-oriented ingestion

Goal: turn IntegrationSource into an executable data path.

Contract requirements:
- service authentication;
- bind request to ACTIVE IntegrationSource;
- tenant derived from authenticated service credential, never request body;
- idempotency key;
- bounded payload/media types;
- correlation id and optional external event id;
- deterministic accepted response with operation/import id.

Pipeline:

```
HTTP ingest -> service auth -> ACTIVE source/scope validation
-> idempotency reservation -> durable ingestion envelope/outbox
-> parse -> schema validation -> mapping -> business import
-> domain persistence -> terminal status
```

No remote call or large parsing while holding a DB transaction/row lock.

States:
`RECEIVED -> QUEUED -> PROCESSING -> SUCCEEDED | PARTIALLY_SUCCEEDED | FAILED`.

Security:
- byte/content/header limits;
- no arbitrary server-side URL fetch until SSRF controls exist;
- sanitized logs;
- no auth headers/secrets/raw sensitive payloads in logs;
- blocked/expired client denied;
- suspended/archived source denied;
- insufficient scope denied;
- tenant isolation tests.

Idempotency: unique tenant/source/idempotency-key reservation. Replay returns original operation semantics without duplicate business writes.

Observability: accepted/rejected, latency, terminal states, retries/recovery and validation failures. No high-cardinality tenant/customer/source identifiers as metric labels.

Exit: a real authenticated request produces a durable, observable import operation.

---

# V3 — Imports UI

Routes:
```
/imports
/imports/new
/imports/:batchId
/imports/schemas
/imports/mappings
```

Reuse ImportBatch/BusinessImport/SourceSchema/MappingProfile APIs where contract-safe.

Required:
- paged/filterable history;
- detail with counts, errors, source, timestamps and terminal state;
- manual file import with schema/mapping selection and duplicate-submit prevention;
- bounded polling while non-terminal;
- schema/mapping version/publish lifecycle where backend supports it;
- transformation catalogue from backend metadata only.

Exit: source-driven imports are inspectable and supported manual import works without Postman.

---

# V4 — Receivables UI

Replace `/receivables` placeholder.

Required:
- paged list;
- backend-supported filters/search/sort;
- amount/currency/status/due date/customer/contract;
- detail route if contract supports it, otherwise explicitly extend backend;
- customer/contract navigation;
- loading/empty/error/403;
- timezone-safe dates and locale-safe money.

Never load an unbounded dataset client-side.

Exit: imported receivables are discoverable and traceable to customer/contract.

---

# V5 — Collections UI

Replace `/collections` placeholder.

Required:
- worklist;
- backend-owned statuses/actions;
- receivable/customer context;
- action validation;
- concurrency protection for competing collectors;
- stable 409 conflict UX;
- permissions;
- server-side audit of relevant actions.

Exit: supported collection workflow works without direct API access.

---

# V6 — Files UI

Replace `/files` placeholder.

Required:
- paged file list;
- upload;
- metadata/status/expiry;
- authenticated safe download;
- authorized delete only if supported;
- MIME/size validation client and server;
- filename treated only as display data;
- 404/403/expired UX;
- safe mapping of RustFS/S3 failures.

Never expose storage credentials or unnecessary internal object keys.

Exit: supported upload/download/delete lifecycle works through UI and integration tests.

---

# V7 — Delivery/message monitoring

Routes:
```
/messages
/messages/:messageId
/campaigns/:campaignId/runs/:runId/messages
```

Required:
- paged tenant-scoped messages;
- supported status/channel/customer/campaign/run filters;
- masked recipient data;
- retry/attempt timeline;
- attachment state;
- safe failure codes;
- stuck/retry/dead indicators;
- no provider secrets/raw unsafe payloads.

Operator commands require explicit backend permission and idempotent command contract.

Exit: delivery can be followed from materialization to SENT/FAILED without DB inspection.

---

# V8 — Tenant analytics/reporting UI

Routes:
```
/analytics
/analytics/communications
/analytics/campaigns
```

Reuse tenant communication analytics; do not build client-side OLAP.

Required:
- tenant-scoped date range;
- supported channel/status/campaign dimensions;
- totals/trends;
- aggregate-backed week/month ranges;
- current-day reconciliation according to backend freshness contract;
- explicit as-of/freshness timestamp;
- loading/empty/error.

Platform analytics stays separate from tenant analytics.

Exit: tenant sees reporting results for the same delivery journey used by E2E.

---

# V9 — Full MVP E2E

Golden scenario:

1. authenticate tenant admin;
2. create/prepare Service Client;
3. configure schema/mapping;
4. create IntegrationSource;
5. verify readiness;
6. activate;
7. authenticate service client;
8. submit idempotent payload;
9. wait for import terminal state;
10. assert Customer;
11. assert Contract;
12. assert Receivable;
13. execute supported Collection transition;
14. create/use Template;
15. create/run Campaign;
16. assert Message;
17. execute deterministic local/mock delivery provider;
18. assert terminal delivery result;
19. assert tenant analytics reflects delivery;
20. navigate resulting records through UI.

Negative E2E:
- duplicate ingestion;
- invalid schema;
- suspended source;
- blocked/expired credential;
- cross-tenant attempt;
- user without permission: API 403 + UI forbidden;
- stale optimistic version;
- transient delivery retry;
- permanent delivery failure;
- missing/failed required attachment;
- recoverable network/API UI failure.

Avoid sleep-based tests; poll observable status with bounded deadlines.

MVP exit:
- V1/V3/V4/V5/V6/V7/V8 primary routes are real, not placeholders;
- golden and security/idempotency negative E2E green;
- backend verify green;
- frontend typecheck/tests/build green;
- no skipped critical tests;
- no production mocks in golden path;
- clean DB bootstrap/migrations green;
- OpenAPI compatibility green.

---

# Cross-cutting frontend contract

Every data screen: loading, empty, error/retry, data.

Every mutation: pending/disabled, field validation, server validation, 401 recovery, 403, 404, 409/version conflict where applicable, success invalidation/refetch.

React Query keys include all resource ids and filters. QueryClient is cleared on logout/session loss. Business state is never inferred from translated labels.

# Cross-cutting backend contract

- ProblemDetail-compatible errors with stable machine-readable codes.
- Tenant ownership in repository/service queries, not post-fetch filtering.
- Explicit permissions on every sensitive read/mutation.
- Bounded pagination and deterministic sorting.
- `Clock` for deterministic business time.
- DB constraints back idempotency/invariants.
- Locking strategy explicit for races.
- Provider/external calls outside long DB transactions.
- Outbox/durable state for async handoff.
- PII/secrets excluded from logs and metrics.

# Definition of Done per slice

DONE requires all:
- route/navigation;
- permission guard where applicable;
- API adapter matches backend;
- backend authorization;
- loading/empty/error/data states;
- mutation pending/error/conflict handling;
- tenant isolation integration test;
- critical frontend behavior test;
- OpenAPI compatibility;
- green CI on exact SHA;
- no placeholder/mock in the primary path.

# Fast execution order

- VC-0 foundation green + merge.
- VC-1 Service Client UI.
- VC-2 IntegrationSource UI + readiness.
- VC-3 executable ingestion + status.
- VC-4 Imports UI.
- VC-5 Receivables UI.
- VC-6 Collections UI.
- VC-7 Files UI.
- VC-8 message monitoring UI.
- VC-9 tenant analytics UI.
- VC-10 golden/negative E2E + release hardening.

Each increment stays independently reviewable and keeps `main` green.

# Explicit non-goals before MVP closure

Defer unless required by the golden journey:
- visual redesign;
- generic workflow/query DSL;
- connector marketplace;
- WebSocket migration where bounded polling suffices;
- broad platform analytics redesign;
- speculative caching;
- new delivery providers;
- unrelated backend domains.

Priority: close the existing product loop, not increase surface area.
