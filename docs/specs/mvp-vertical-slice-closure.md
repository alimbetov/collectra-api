# Collectra MVP Vertical-Slice Closure — Master Execution Spec v3

Status: REVIEWED / EXECUTION MASTER
Branch: `feat/mvp-vertical-slice-closure`
Baseline: `2548f0c1ecfc72e8a4f3216785be29b14f32856f`

## 1. Purpose and authority

This document orchestrates the shortest safe path from the current backend-heavy implementation to a functionally usable Collectra MVP. It is a master plan, not a replacement for reviewed domain specifications.

Normative child specifications:
- Integration control plane: `integration-production-setup-center-master-spec.md`, `i1-integration-security-test-matrix.md`.
- Source ingestion: `integration-ingestion-gap-spec.md`, `integration-journey.md`.
- Receivables/payments: `frontendweb-fw05-receivables.md`.
- Collections: `frontendweb-fw06-collections.md`.
- Message monitoring: `frontendweb-fw08-message-monitoring.md`.
- Imports/configuration: `frontendweb-fw10-imports.md`.
- Files registry: `frontendweb-fw11-files.md`.
- Reporting: `reporting-r11-tenant-daily-projections.md`, `reporting-r12-communication-coverage-audit.md`.
- Delivery API/observability: `slice-08-delivery-api-observability.md`.
- Frontend contract/IA: `frontend-react-api-contract.md`, `frontend-screen-api-matrix.md`, `frontend-ui-information-architecture.md`, `frontend-user-processes.md`.

If this master document is less specific than a reviewed child spec, the child spec governs. If two specs conflict, implementation stops until the conflict is explicitly resolved in documentation and tests.

## 2. MVP outcome

The product loop to close is:

```
Human admin
 -> Service Client
 -> Source Schema + Mapping
 -> Integration Source
 -> Readiness -> Activate

Service client
 -> source-oriented ingestion
 -> import/validation/diagnostics
 -> canonical business ingestion: CUSTOMER / INVOICE / PAYMENT
 -> Allocation
 -> Collection Case

Human operator
 -> Campaign + Template
 -> Message -> Delivery -> Monitoring
 -> Tenant Analytics/Report
```

Files are cross-cutting infrastructure, not an artificial sequential business step:

```
FileService -> import source
            -> template/generated asset
            -> document
            -> message attachment
```

A capability is DONE only when its real UI-to-database path is usable, authorized, tenant-safe, observable, tested and green on the exact merge-candidate SHA.

## 3. Delivery invariants

1. Backend owns lifecycle, permissions, validation, monetary truth, readiness and stable problem codes.
2. Frontend renders backend state; it does not recreate lifecycle/readiness/accounting rules.
3. Tenant ownership is enforced in backend queries/services. UI filtering is never a security boundary.
4. Every mutation implements pending/disabled, validation, conflict where applicable, forbidden, failure and success UX.
5. Potentially unbounded collections use bounded server pagination/cursors and deterministic ordering.
6. IDs in URL paths are encoded; external return paths are validated as internal paths.
7. Polling starts only for explicitly non-terminal server state, is bounded, pauses/stops appropriately and never assumes that HTTP 202 means asynchronous processing.
8. Secrets are server-generated and one-time disclosed only where the backend contract permits it.
9. No production path marked DONE may depend on mock/fake/stub/hardcoded business data.
10. No merge without green backend and frontend CI on the exact candidate SHA.
11. OpenAPI compatibility remains green; contract changes are intentional and reviewed.
12. New/changed tenant endpoints require authorization and tenant-isolation integration coverage.
13. Money crosses the boundary losslessly; React never performs authoritative balance/accounting calculations.
14. Ambiguous non-idempotent network mutations are never blindly replayed.
15. External/provider calls and large parsing work do not execute while holding long DB locks/transactions.
16. PII, credentials, raw provider payloads and sensitive source values do not leak into logs, metrics or UI diagnostics.
17. Backend blockers identified by child specs are resolved before the dependent UI is declared DONE.
18. MVP business ingestion supports exactly the canonical document types `CUSTOMER`, `INVOICE`, and `PAYMENT`; domain entities are not automatically ingestion document types.
19. `CONTRACT` is optional domain context and is not a mandatory ingestion dependency or MVP business document type.
20. Transport idempotency and business idempotency are separate invariants and both are required.
21. Every public contract introduced or changed by a VC is verified against generated OpenAPI and the reviewed baseline in that same VC.

## 4. Blocker/dependency matrix

| Vertical | Existing backend | Required closure before DONE | Frontend state |
|---|---|---|---|
| VC-0 Foundation | I0/I1 branch | sync main, exact green candidate, merge | integration adapters only |
| VC-1 Service Clients | API/scopes/lifecycle available | verify stable problem/permission contracts | screens/routes missing |
| VC-2 IntegrationSource | CRUD/lifecycle/basic readiness available | readiness DTO sufficient for backend-driven remediation | screens/API adapter missing |
| VC-3 Ingestion | mapping + business persistence exist for CUSTOMER/INVOICE/PAYMENT; source control plane exists | executable source endpoint, durable operation/outbox, canonical persistence semantics, idempotency/security/recovery | status UI missing |
| VC-4 Imports | business import and document-generation batch are distinct existing pipelines | durable paged business-ingestion diagnostics; keep document-generation semantics separate; safe config detail/revision/bounds | placeholder |
| VC-5 Receivables | invoice/payment/allocation APIs exist; decimal-string money and allocation command idempotency are already covered | list projections, allocation-history bounds, preserve money/idempotency regression coverage, create ambiguity policy | placeholder |
| VC-6 Collections | case/workflow APIs exist | next-action filters/sort, bounded assignee lookup, child-history bounds, money transport | placeholder |
| VC-7 Files | upload/detail/content/url/delete exist | tenant-paged registry endpoint + dedicated safe public DTO | placeholder |
| VC-8 Messages | run-scoped list/detail ready | no fabricated attempt history; only contract-supported monitoring | screens missing |
| VC-9 Analytics | rich tenant communication analytics exists | performance/freshness validation; aggregate work only if evidence requires it | screens missing |
| VC-10 Release | domain tests exist | full rolling E2E, clean bootstrap, security/recovery hardening | release gate |

Do not start a dependent screen by inventing data that the backend does not safely expose.

## 5. Rolling Golden E2E

E2E is not postponed to VC-10. One deterministic golden scenario grows after every vertical:

```
VC-1: Service Client
VC-2: Service Client -> IntegrationSource -> Readiness
VC-3: ... -> authenticated ingestion -> durable operation
VC-4: ... -> import terminal result/diagnostics
VC-5: ... -> CUSTOMER -> INVOICE + PAYMENT -> Allocation
VC-6: ... -> Collection Case/workflow
VC-7: verify FileService in real consumer paths + registry UI
VC-8: ... -> Campaign -> Message -> Delivery monitoring
VC-9: ... -> tenant analytics reflects delivery
VC-10: complete negative matrix + release hardening
```

Each VC extends rather than replaces the previous golden test. Cross-domain failures are therefore discovered at the earliest integration point.

---

# VC-0 — Foundation merge gate

Normative specs: Integration master + I1 security matrix.

Required:
- synchronize `feat/integration-contract-foundation` with current `main`;
- resolve conflicts without losing either side;
- record exact merge-candidate SHA;
- `mvn --batch-mode --no-transfer-progress spotless:check`;
- `bash scripts/test-slice-07.sh unit`;
- `mvn --batch-mode --no-transfer-progress clean verify -Pslice10a-coverage`;
- frontend `npm ci`, typecheck, test:ci and production build;
- IntegrationSource lifecycle/security, ServiceClient lifecycle/security and Mapping metadata tests execute;
- OpenAPI compatibility green;
- merge only after required jobs are green on that exact SHA.

Exit: `main` contains I0/I1 foundation and remains green.

---

# VC-1 — Service Client UI

Normative spec: Integration Setup Center master.

Routes:
```
/integrations
/integrations/service-clients
/integrations/service-clients/new
/integrations/service-clients/:clientId
```

Reuse the existing Service Client list/detail/scope/create/rotate/credential activation/block/unblock contracts.

Required UX:
- permission-aware navigation and route guard;
- list/detail/create;
- backend scope catalogue, no hardcoded scope authority;
- one-time secret disclosure with explicit acknowledgement;
- rotation and credential activation;
- block/unblock confirmation;
- loading/empty/error/403 states;
- double-submit protection.

Secret invariant: disclosed secret is never written to React Query cache, localStorage/sessionStorage, URL, logs, analytics, toast history or durable reusable component state.

Tests:
- API contract/adapters;
- route permission behavior;
- one-time secret non-persistence;
- mutation disabled/error states;
- foreign tenant/no-disclosure backend coverage.

Rolling E2E: tenant admin creates Service Client and safely acknowledges the secret.

---

# VC-2 — IntegrationSource + Readiness UI

Normative specs: Integration Setup Center master + I1 matrix.

Routes:
```
/integrations/sources
/integrations/sources/new
/integrations/sources/:sourceId
/integrations/sources/:sourceId/readiness
```

Required:
- entity API/query/mutation layer;
- list/create/detail/edit;
- selectors for tenant-owned Service Client, schema and mapping;
- activate/suspend/archive;
- optimistic `version` commands;
- `VERSION_CONFLICT`: preserve intent, reload authoritative resource, require explicit re-apply; never silent overwrite.

Readiness is backend-driven. The response must be sufficient for:
- overall READY/BLOCKED;
- stable check code/state;
- resource type and safe reference where allowed;
- stable explanation/message arguments;
- recommended remediation/navigation where applicable.

If current I1 DTO cannot support this without frontend business inference, extend backend DTO first.

Rolling E2E: Service Client -> source references -> readiness blockers -> remediation -> READY -> activate.

---

# VC-3 — Source-oriented ingestion

Normative specs: `integration-ingestion-gap-spec.md`, `integration-journey.md`.

Do not bypass IntegrationSource and do not create a second mapping engine. Production source ingestion reuses the existing `MappingExecutionService` and business persistence seam.

## Canonical MVP business ingestion standard

The only canonical MVP business document types are:

```
CUSTOMER
INVOICE
PAYMENT
```

The financial flow is:

```
              CUSTOMER
                  |
          +-------+-------+
          v               v
       INVOICE          PAYMENT
          |               |
          +-------+-------+
                  v
              ALLOCATION
                  |
                  v
              COLLECTION
```

`CONTRACT` is optional domain context. It is not a mandatory ingestion step and is not added as an ingestion document type merely because the domain entity exists. An invoice may carry an optional existing `contractId` where the contract is already known through a supported Collectra workflow, but external source ingestion must not require a Collectra-internal contract UUID to complete the canonical CUSTOMER/INVOICE/PAYMENT flow.

Adding a future business document type requires an explicit contract extension: mapping metadata, normalized payload contract, persistence strategy, idempotency/conflict semantics, diagnostics, tenant/security tests and OpenAPI review.

## Pipeline separation

Two existing concepts must not be conflated:

1. **Business ingestion** — mapping -> normalized business records -> `BusinessRecordPersistenceService` -> CUSTOMER/INVOICE/PAYMENT.
2. **Document-generation import batch** — mapping -> template/generation job -> generated documents.

IntegrationSource production ingestion targets **business ingestion**. It must not require `templateVersionId` or output formats merely to ingest a customer, invoice or payment. Document generation remains a separate workflow.

Required source contract:
- service authentication;
- ACTIVE IntegrationSource binding;
- tenant derived from authenticated service identity, never request body;
- required idempotency key;
- bounded content/media types;
- correlation/request id and optional external event id;
- deterministic accepted/replayed response with ingestion operation id;
- durable status endpoint.

Pipeline:

```
HTTP ingest
 -> authenticate service
 -> validate source/status/scope
 -> reserve transport idempotency
 -> create IngestionOperation(RECEIVED)
 -> persist durable envelope/reference + outbox
 -> COMMIT
 -> worker dispatch
 -> PROCESSING
 -> existing parser/schema/mapping
 -> normalized CUSTOMER / INVOICE / PAYMENT records
 -> existing business persistence seam
 -> durable record diagnostics
 -> terminal operation state
```

Large parsing/mapping/business work is not performed in the request transaction that reserves the operation. The HTTP-to-worker handoff must survive process crash after acceptance.

## Terminal and record semantics

Operation:
- `SUCCEEDED`: all intended records reached CREATED or REUSED and none failed/conflicted.
- `PARTIALLY_SUCCEEDED`: at least one record reached CREATED/REUSED and at least one ended CONFLICT/FAILED.
- `FAILED`: fatal parse/schema/mapping failure before usable records, or no business record succeeded.
- transient infrastructure failure remains retryable/recoverable and is not mislabeled as permanent business failure.

Record:
- `CREATED`: new business entity persisted.
- `REUSED`: same business identity and equivalent canonical business state.
- `CONFLICT`: same business identity but materially different canonical state where update is not explicitly supported.
- `FAILED`: invalid/unpersistable record.

Do not silently classify changed data as REUSED only because `externalId` exists. Before VC-3 is DONE, define a deterministic equivalence/fingerprint policy per canonical type, excluding volatile/internal fields. MVP defaults to conflict rather than silent overwrite unless an explicit update policy is designed and tested.

## Two idempotency layers

**Transport idempotency:** tenant + IntegrationSource + Idempotency-Key plus request equivalence. Same key/same request replays the same operation; same key/different request returns a stable conflict.

**Business idempotency:** tenant + canonical document type + external business identity prevents duplicate CUSTOMER/INVOICE/PAYMENT creation independently of transport replay protection.

Both are mandatory.

## Customer reference rule

INVOICE and PAYMENT resolve customer through normalized customer external identity supported by the existing business persistence seam. External integrations do not need Collectra customer UUIDs for the canonical path. Customer is reused or created according to canonical persistence policy.

## Security and recovery

- content-length and actual-byte limits;
- media/header/field/record-count bounds;
- no arbitrary server-side URL fetching until SSRF/redirect/rebinding controls exist;
- blocked/expired credential denied;
- suspended/archived source denied;
- insufficient scope denied;
- tenant isolation;
- sensitive payload/auth data excluded from logs/metrics;
- bounded retries/backoff;
- stale PROCESSING recovery;
- retry is safe under both idempotency layers.

## VC-3 contract gate

The ingestion endpoint/status/problem contracts are generated into OpenAPI, covered by integration tests, and the compatibility baseline is reviewed in VC-3 itself.

Rolling E2E: service submits canonical CUSTOMER/INVOICE/PAYMENT input and replays the same transport intent; one logical operation and no duplicate business effect result.

---

# VC-4 — Import Execution, Diagnostics and Configuration UI

Normative spec: `frontendweb-fw10-imports.md`. It governs details except where this master explicitly separates business ingestion from document-generation import batches.

Mandatory terminology:
- **Ingestion operation / business import**: source data becomes CUSTOMER/INVOICE/PAYMENT domain records.
- **Document-generation import batch**: mapping plus template/generation produces generated documents.

UI labels, API adapters and diagnostics must not present these as one lifecycle when backend semantics differ.

Routes:
```
/imports
/imports/new
/imports/:importId
/imports/:importId/errors
/imports/source-schemas/*
/imports/mapping-profiles/*
```

Backend closure before DONE:
1. Durable tenant-scoped paged business-ingestion record/field diagnostics, not only generic batch error. Diagnostics identify canonical document type, record identity/order and CREATED/REUSED/CONFLICT/FAILED outcome.
2. Diagnostics have stable ordering, bounded safe detail and masked source values.
3. Retention tied to import/source-file lifecycle.
4. Source schema/mapping definition/version collections are bounded or paged.
5. Routed editors have direct tenant-scoped detail projections; no list scanning to restore deep links.
6. Mutable configuration has explicit revision/ETag/version semantics before claiming stale-write-safe editing.

Important current behavior: HTTP 202 does not imply a worker is still running. Existing import create may finish processing synchronously before returning. Frontend inspects returned status:
- terminal -> render result, no polling;
- explicitly non-terminal -> bounded polling.

Submission:
- one Idempotency-Key per logical intent;
- same key reused only for replay of the same body/file/config;
- changed intent gets a new key;
- ambiguous network result offers explicit replay/reconciliation, never silent new submission.

UI:
- history and detail;
- manual multipart/JSON/XML execution as supported;
- progress only when non-terminal;
- paged diagnostics;
- source-schema lifecycle/editor;
- mapping-profile lifecycle/editor/test;
- published versions remain immutable according to backend rules.

Rolling E2E: source business ingestion -> terminal operation -> CUSTOMER/INVOICE/PAYMENT outcomes and durable diagnostics survive reload/deep link. Document-generation import remains independently tested under its own lifecycle.

---

# VC-5 — Receivables, Payments and Allocations

Normative spec: `frontendweb-fw05-receivables.md`. This VC is not only an invoice list.

Routes:
```
/receivables?view=invoices|payments
/receivables/invoices/new
/receivables/invoices/:invoiceId
/receivables/payments/new
/receivables/payments/:paymentId
```

Backend closure before DONE:
- Invoice list projection includes bounded server-resolved customer display name and optional contract number;
- Payment list projection includes customer display name;
- allocation histories are paged or protected by documented hard bounds;
- preserve the already implemented/tested lossless decimal-string money contract; do not redesign it;
- preserve existing allocation `commandId` idempotency regression coverage;
- create invoice/payment ambiguity has an explicit reconciliation/idempotency policy.

UI:
- server-paged invoices/payments;
- backend-supported filters only;
- create/detail;
- allocation to eligible invoice;
- reversal with reason/confirmation;
- `commandId` remains stable for allocation replay;
- 409 preserves entered values and requires reconciliation;
- successful mutation invalidates all affected payment/invoice/allocation/dashboard projections.

Financial invariants:
- React never computes authoritative outstanding/payment status;
- never combine currencies into one total;
- no optimistic authoritative balance changes;
- backend `businessDate` is distinct from browser date;
- monetary form values remain canonical decimal strings, never floating-point accounting.

Rolling E2E: canonical CUSTOMER/INVOICE/PAYMENT ingestion is discoverable in Receivables; INVOICE and PAYMENT meet at Allocation; supported allocation/reversal changes authoritative state without duplicate effect; Contract is optional context and its absence does not block this path.

---

# VC-6 — Collection Workspace

Normative spec: `frontendweb-fw06-collections.md`.

Routes:
```
/collections
/collections/:caseId/{overview|promises|disputes|actions|timeline}
```

Backend closure before DONE:
- fixed queue filters `nextActionOverdue`, `nextActionDueFrom/To`;
- allow-listed `nextActionDueAt` sort;
- bounded permission-aware assignee lookup;
- promises/disputes/actions/timeline paged or protected by documented hard limits;
- lossless money transport for promise/projected outstanding amounts.

UI:
- server-paged work queue;
- case detail/lifecycle;
- promises to pay;
- disputes;
- actions;
- immutable timeline;
- child panels have independent bounded state;
- lifecycle buttons derive from backend status + permissions;
- versioned commands send current version.

409 flow:
1. preserve unsent form input;
2. fetch authoritative detail/version;
3. show safe summary of changed state;
4. require explicit re-apply/cancel;
5. never automatically repeat close/resolve/complete.

Collection workflow state and receivable finance state remain separate in frontend state.

Rolling E2E: imported receivable -> collection case -> supported lifecycle/action -> reload preserves authoritative result.

---

# VC-7 — Files Registry and UI

Normative spec: `frontendweb-fw11-files.md`.

Current backend is insufficient for a registry UI: upload/detail/content/download-url/delete exist, but tenant-paged `GET /api/v1/files` does not.

Backend closure:
```
GET /api/v1/files
 ?category&status&projectId&filename&createdFrom&createdTo&page&size&sort
```

Requirements:
- tenant predicate in PostgreSQL;
- bounded page size and allow-listed sort;
- dedicated public list/detail DTOs;
- do not expose tenantId, bucket, object/storage key, internal URL or deletion internals;
- indexes follow measured query shapes;
- tenant isolation, paging, authorization and query-count tests.

Routes:
```
/files
/files/:fileId
```

UI:
- URL-owned filters/server paging;
- detail/metadata;
- upload;
- authenticated content or short-lived backend-issued download URL;
- delete only with permission/confirmation;
- explicit deleted/expired/not-downloadable state;
- no constructed RustFS URL;
- presigned URL not persisted beyond short UI lifetime;
- filename is text, never HTML/path authority.

Cross-cutting E2E: FileService is also verified in its real consumers (import source, generated document/template asset/message attachment) where applicable; it is not treated as a fake sequential business step.

---

# VC-8 — Message Delivery Monitoring

Normative spec: `frontendweb-fw08-message-monitoring.md`.

Routes:
```
/campaigns/:campaignId/runs/:runId/messages
/campaigns/:campaignId/runs/:runId/messages/:messageId
```

Use existing run-scoped `MessageController`.

List:
- Slice semantics: next/previous using `hasNext`; do not invent total pages;
- URL-owned status/channel/customer/page/size;
- unknown status/channel fallback;
- destination rendered exactly as backend-masked;
- no per-row customer lookup fan-out;
- polling follows campaign-run terminal policy.

Detail:
- status;
- attempt count;
- processing/retry/sent timestamps;
- safe error;
- attachment readiness;
- providerMessageId as diagnostic text only.

Critical correction: current API does NOT expose delivery-attempt history. Never fabricate an attempt timeline from `attemptCount`. A real attempt timeline is a separate backend enhancement and is not required for FW8 MVP.

Current attachment DTO has no authorized file reference; therefore no attachment download action may be inferred from attachment id. Add such action only after an explicit authorized backend contract exists.

No manual retry/cancel until backend defines eligibility, idempotency, audit, rate limiting and counter impact.

Rolling E2E: campaign run -> message -> deterministic delivery result -> monitoring reflects authoritative state.

---

# VC-9 — Tenant Communication Analytics UI

Normative specs: reporting R11/R12 plus current tenant analytics API.

Do not build a new reporting backend by default. Existing backend already exposes tenant-scoped communication summary, timeseries, channels, users, campaigns, failures, lifecycle, audience, attempts, attachments, operations and documents.

Routes:
```
/analytics
/analytics/communications
/analytics/campaigns
```

UI:
- tenant-scoped date range;
- campaign/run/channel/user filters supported by backend;
- summary/trends;
- channel/campaign/user/failure reports;
- lifecycle/audience/attempt/attachment/operation/document views as product UX requires;
- explicit freshness/as-of semantics when supplied;
- loading/empty/error states;
- server paging for page reports.

Aggregate policy:
- reuse existing daily/aggregate projections;
- current-day/live reconciliation follows backend reporting contract;
- add caching/new aggregate structures only when query/load evidence justifies them;
- never create client-side OLAP over unbounded raw messages.

Platform analytics remains a separate authorization/scope surface.

Rolling E2E: the delivery produced by the golden journey becomes visible in tenant reporting according to the documented freshness contract.

---

# VC-10 — MVP Release Hardening

VC-10 is not the first E2E. It completes the rolling E2E and negative matrix.

Final golden scenario:
1. tenant admin authenticates;
2. Service Client created/prepared;
3. schema/mapping configured;
4. IntegrationSource created;
5. readiness checked/remediated;
6. source activated;
7. service authenticates;
8. idempotent payload submitted;
9. import reaches terminal state;
10. canonical CUSTOMER, INVOICE and PAYMENT business outcomes asserted;
11. Allocation between PAYMENT and INVOICE asserted, including replay safety; Contract is optional context and not a golden-path prerequisite;
12. Collection Case/workflow asserted;
13. Template/Campaign configured;
14. Message materialized;
15. deterministic local/mock provider produces configured terminal delivery;
16. Message monitoring reflects state;
17. tenant analytics reflects the result;
18. resulting records are navigable through UI;
19. relevant FileService consumer path is verified.

Negative matrix:
- duplicate ingestion transport idempotency;
- same transport key with changed request -> stable conflict;
- duplicate business external identity with equivalent state -> REUSED;
- duplicate business external identity with materially changed state -> CONFLICT, never silent REUSED/overwrite;
- invalid schema/mapping payload;
- suspended/archived source;
- blocked/expired service credential;
- insufficient service scope;
- cross-tenant identifiers;
- human user without permission: API 403 + UI forbidden;
- stale optimistic version;
- import validation diagnostics/masking;
- ambiguous create mutation reconciliation;
- collection stale command;
- transient delivery failure -> retry;
- permanent delivery failure;
- required attachment pending/failed;
- network/API failure -> recoverable UI;
- session refresh failure -> cleared session/login;
- large-list pagination boundary;
- special characters/Unicode;
- date/timezone/businessDate boundary;
- clean DB bootstrap/migration.

Avoid sleep-based tests. Poll observable state with bounded deadlines.

Release gate:
- primary routes VC-1..VC-9 are real, not placeholders;
- rolling golden and negative security/idempotency scenarios green;
- backend verify green;
- frontend typecheck/tests/build green;
- no skipped critical tests;
- no production mocks in primary path;
- clean DB bootstrap green;
- migrations reproducible/forward-only;
- OpenAPI compatibility green;
- exact release candidate SHA recorded.

---

# 6. Cross-cutting frontend contract

Every data screen has loading, empty, recoverable error and data states.

Mutations additionally handle pending/disabled, field validation, safe server problem, 401 session recovery, 403, 404/no-longer-owned, 409/version conflict where applicable, and deterministic cache invalidation.

React Query keys include hierarchy/resource IDs and normalized filters. Query cache is cleared on logout/session loss. Business state is never inferred from translated labels.

Network failures are distinguishable from structured HTTP problems where UX depends on retryability. Automatic replay is allowed only for operations whose idempotency semantics make replay safe.

# 7. Cross-cutting backend contract

- ProblemDetail-compatible errors with stable machine-readable codes for UI branching.
- Tenant ownership enforced before/global fetch disclosure.
- Explicit permissions on every sensitive read/mutation.
- Bounded pagination and deterministic ordering.
- `Clock` for deterministic business time.
- DB constraints support uniqueness/idempotency/invariants.
- Locking strategy explicit for races.
- Provider/external calls outside long DB transactions.
- Outbox/durable state for async handoff.
- PII/secrets excluded from logs/metrics.
- Public DTOs do not leak internal tenancy/storage/provider implementation details.

# 8. Definition of Done per vertical

A VC is DONE only when:
- required backend blockers in its normative child spec are closed;
- route/navigation exists;
- permission guard exists where applicable;
- API adapter matches backend/OpenAPI;
- backend authorization/tenant isolation is tested;
- loading/empty/error/data states exist;
- mutations handle pending/error/conflict as applicable;
- critical frontend behavior is tested;
- rolling golden E2E is extended through this VC;
- OpenAPI compatibility remains green and any changed public contract was reviewed in the same VC;
- CI is green on exact SHA;
- no placeholder/mock/hardcoded business data remains in the primary path.

# 9. Execution order

```
VC-0 Foundation
  -> VC-1 Service Clients
  -> VC-2 IntegrationSource/Readiness
  -> VC-3 Source Ingestion
  -> VC-4 Imports/Diagnostics/Configuration
  -> VC-5 Receivables/Payments/Allocations
  -> VC-6 Collection Workspace
  -> VC-7 Files Registry/UI
  -> VC-8 Message Monitoring
  -> VC-9 Tenant Analytics
  -> VC-10 Release Hardening
```

Each increment is merge-sized, independently reviewable and keeps `main` green. Rolling E2E grows continuously from VC-1 onward.

# 10. Explicit non-goals before MVP closure

Unless the golden journey proves they are required, defer:
- visual redesign;
- generic workflow/query DSL;
- connector marketplace;
- arbitrary server-side URL connectors;
- WebSocket migration where bounded polling suffices;
- broad platform analytics redesign;
- speculative caching;
- fabricated delivery attempt history;
- new delivery providers;
- offline editing;
- client-side accounting/ETL;
- unrelated backend domains.

The priority is to close and prove the existing product loop, not increase surface area.
