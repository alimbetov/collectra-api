# A01 — Product Capability Coverage Audit

Baseline SHA: `76e121082d3f339c8440fa2d9f34a092b3048345`
Status: IN_PROGRESS

## Objective

Prove that each product capability is usable as a complete tenant/user outcome, not merely represented by a controller, table, React page, or historical specification.

## Traceability dimensions

Every capability is checked across:

1. accepted product requirement / user process;
2. backend application/domain behavior;
3. REST contract;
4. persistence;
5. frontend route/page/query/mutation;
6. authorization/tenant boundary;
7. automated verification;
8. operational/recovery support where asynchronous.

A capability is VERIFIED only when all mandatory dimensions are evidenced.

## Product capability baseline

Derived from the current README core flow, frontend user processes P1-P15, screen/API matrix, and current source tree.

| ID | Capability | Initial state | Notes |
|---|---|---|---|
| CAP-01 | Tenant registration, login and session lifecycle | VERIFYING | P1/P2 |
| CAP-02 | Customer master data, contacts and segmentation | VERIFYING | P3 |
| CAP-03 | Contract lifecycle | VERIFYING | P4 |
| CAP-04 | Invoice/receivable creation and monitoring | VERIFYING | P5/P6 |
| CAP-05 | Payment registration, allocation and reversal | VERIFYING | P7 |
| CAP-06 | Collection case workflow, promise, dispute, actions, timeline | VERIFYING | P8 |
| CAP-07 | Template authoring, validation, preview and publication | VERIFYING | P9 |
| CAP-08 | Campaign creation, audience, preparation and execution | VERIFYING | P10/P11 |
| CAP-09 | Message delivery and monitoring | VERIFYING | P12 |
| CAP-10 | File/document lifecycle and business linkage | VERIFYING | P13 |
| CAP-11 | Tenant administration/RBAC | VERIFYING | P14 |
| CAP-12 | Profile/security/session management | VERIFYING | P15 |
| CAP-13 | Source schema + mapping configuration | VERIFYING | integration setup |
| CAP-14 | JSON/XML/CSV/XLSX ingestion/import and diagnostics | VERIFYING | synchronous + async paths |
| CAP-15 | Service clients / machine-to-machine integration | VERIFYING | integration setup |
| CAP-16 | Dashboard operational projections | VERIFYING | FW03 |
| CAP-17 | Tenant analytics/reporting | VERIFYING | VC-9 evidence required |
| CAP-18 | Platform administration | VERIFYING_SCOPE | explicitly excluded from tenant frontend MVP in historical matrix |
| CAP-19 | External provider delivery | VERIFYING_SCOPE | simulation/mock and real adapter evidence must be distinguished |
| CAP-20 | Operational recovery/diagnostics visible to authorized users/operators | VERIFYING | async product usability |

## Historical gates that require current-code proof

The current screen/API matrix records post-freeze gates. A01 treats these as open verification questions until code/tests on the baseline prove closure:

- lossless money transport for dashboard/receivables/collections;
- expected-version customer/contact updates and bounded manager selection;
- customer/contract labels and bounded allocation history;
- collection next-action filtering/sorting and bounded child history;
- direct campaign/run detail and idempotent run preparation;
- template direct detail, optimistic revision and builder limits;
- durable record/field import diagnostics;
- paged file registry and safe public DTO;
- paged identity lists, explicit membershipId and role revision.

## Verification method

For every CAP item produce a row-level evidence set:

`requirement -> frontend -> endpoint -> service/domain -> repository/migration -> permission -> test`.

Async capabilities additionally require:

`enqueue/outbox -> worker -> terminal state -> retry/recovery -> user/operator visibility`.

## Exit criteria for A01

A01 can close only when:

- every CAP item is VERIFIED or explicitly OUT_OF_SCOPE;
- every PARTIAL/MISSING/BROKEN item has a defect ledger entry;
- P0/P1 capability gaps are remediated and tested;
- the Golden Journey prerequisites required by A02 are enumerated;
- evidence points to the exact audited SHA.


## CAP-01..CAP-05 deep pass — evidence update

### CAP-01 Auth/session — PARTIAL verification

Frontend contains the slug-login client/context and authenticated route guards. Full registration/session-security coverage remains to be traced in the next auth-specific evidence pass; no defect is asserted from file presence alone.

### CAP-02 Customer master data — VERIFIED implementation gates, test evidence pending exact-SHA run

Current code proves the previously documented implementation gates:
- customer update/status commands carry optimistic `version`;
- email/phone patch carries optimistic `version`;
- manager assignment is validated against an active tenant membership;
- list projection resolves manager/contacts/segments server-side rather than requiring row fan-out.

No A01 defect is opened for those historical gates.

### CAP-03 Contract lifecycle — implementation present, executable verification pending

Backend controller/service/domain and frontend list/detail/form/lifecycle modules are present. Final VERIFIED state requires exact-SHA tests and route-level capability evidence.

### CAP-04 Invoice/receivable — implementation gates VERIFIED, executable verification pending

Backend exposes tenant-scoped invoice list/detail/create/allocation history. Money is represented at the public API boundary with `DecimalString`; frontend models also use `DecimalString`. Customer/contract labels exist in list projection. No lossless-money defect is opened.

### CAP-05 Payments/allocation — PARTIAL / DEFECT A01-004

Backend capability is materially implemented:
- payment create/list/detail;
- allocation history;
- client-supplied `commandId` idempotency;
- payment then invoice locking;
- customer/currency/balance invariants;
- versioned reversal.

However, the tenant React product does not expose the documented P7 workflow. The current router contains invoice routes only under `/receivables`, and the receivable frontend entity exposes invoice operations plus invoice allocation history, but no payment list/detail/create/allocation/reversal client surface. There are no Payment pages in `frontendweb/src/pages/receivables`.

**Impact:** an ordinary tenant user cannot complete payment registration/allocation/reversal through the product UI even though the backend supports it. This breaks the documented receivable lifecycle and prevents a complete UI Golden Journey.

**Classification:** P1 product capability gap.

**Required remediation:**
1. add payment DTO/API/query/mutation frontend boundary;
2. add payment list, create and detail routes/pages;
3. implement allocation command with one stable UUID `commandId` per user intent;
4. implement versioned reversal with conflict reload;
5. link payment allocation rows to invoice detail and invoice allocation rows back to payment detail;
6. permission-gate read/manage actions;
7. add API contract, route and page tests;
8. include P7 in the A02 Golden Journey.


## CAP-06..CAP-10 deep pass — evidence update

### CAP-06 Collections — implementation coverage VERIFIED, executable verification pending

Backend and tenant UI both expose the material collection workspace: case list/detail and lifecycle, promises, disputes, actions and authoritative timeline. Mutating lifecycle operations use version-bearing commands where the domain object is versioned, and read/manage permissions are separated. No A01 capability omission was found in this pass. Exact-SHA tests remain required before final VERIFIED closure.

### CAP-07 Campaigns — implementation coverage VERIFIED, executable verification pending

Campaign list/detail/edit, validation, preview, activation, run preparation and run navigation exist in the tenant UI and backend. Run preparation sends an explicit client-generated `X-Command-Id`. Generated PDF attachment configuration is exposed for DRAFT campaigns. No A01 capability omission was found in this pass; delivery/provider truth remains tracked separately by A01-003.

### CAP-08 Templates — implementation coverage VERIFIED, executable verification pending

Template lifecycle, version creation, builder editing, field/asset catalogs, validation, preview, PDF preview and publish/reopen/archive transitions are represented in frontend and backend. The editor handles optimistic revision conflicts explicitly. No A01 capability omission was found in this pass.

### CAP-09 Imports — implementation coverage VERIFIED, executable verification pending

Tenant UI exposes import create/list/detail and masked diagnostics with bounded terminal-aware polling. The create boundary sends an idempotency key. No A01 capability omission was found in this pass.

### CAP-10 Files — PARTIAL / A01-005 REMEDIATED, exact-SHA verification pending

Historical audit evidence found that the general tenant `FileUploadPage` exposed `TEMPLATE_ASSET` and `ATTACHMENT` while backend `FileCategory` accepted only `IMPORT_SOURCE | REPORT | EXPORT | ASSET | TEMP`.

The current implementation no longer contains that runtime mismatch. Backend `FileCategory`, frontend `FILE_CATEGORIES`, and the options rendered by `FileUploadPage` use the same five canonical values.

**Current risk:** without executable contract guards, a future frontend/backend enum change could reintroduce the drift.

**Remediation evidence added in `fix/audit-defects-and-release-gate-alignment`:**
1. frontend model regression asserts the canonical `FILE_CATEGORIES`;
2. `FileUploadPage.test.tsx` asserts the rendered selectable options come only from that canonical set and exclude the historical invalid values;
3. `OpenApiCompatibilityIntegrationTest.fileUploadCategoryContractMatchesCanonicalRegistryCategories` asserts the actual generated public OpenAPI upload parameter exposes exactly the same five values.

**A01 state:** remains `PARTIAL` until these executable guards pass on the candidate exact SHA. The historical P1 defect must not be reported as currently reproducible after that verification.

## CAP-11..CAP-15 deep pass — evidence update

### CAP-11 Tenant administration / RBAC — PARTIAL / DEFECT A01-006

Backend exposes tenant-scoped membership listing, role listing/create/update/delete, role assignment, membership block/unblock and administrative session revocation with explicit permissions. The tenant frontend router has no user/membership or role administration route/page, and no tenant RBAC management surface was found in the current frontend tree.

**Impact:** tenant administrators cannot perform the documented P14 administration workflow through the product UI despite the backend capability being available.

**Classification:** P1 product capability gap.

**Required remediation:** add tenant Administration routes/pages for users/memberships and roles; expose bounded user list, role CRUD, membership-role assignment, membership status and session revocation; gate each action with the matching backend permission; add route/page/API contract tests and tenant-isolation tests.

### CAP-12 Profile/security/session management — PARTIAL / DEFECT A01-007

Backend `/api/v1/identity/me` supports profile read/update, password change, own-session listing and own-session revocation. No tenant profile/security/session route or page is registered in the frontend router, and no matching frontend workflow was found.

**Impact:** an authenticated user cannot self-service the documented P15 profile/security/session lifecycle from the product UI.

**Classification:** P1 product capability gap.

**Required remediation:** add Profile/Security UI for display name/locale/timezone, password change, active sessions and session revocation; preserve current-session safety semantics; add API and page tests.

### CAP-13 Source schema + mapping configuration — implementation coverage VERIFIED, executable verification pending

Integration Setup Center links to Source Schema Studio and Mapping Studio. The UI supports schema version creation, fields, validation and publish/reopen lifecycle; mapping supports version creation against a published schema, source-to-target rules, validation and publish/reopen lifecycle. Routes are permission-gated. Exact-SHA executable evidence remains required.

### CAP-14 JSON/XML/CSV/XLSX ingestion/import and diagnostics — implementation coverage VERIFIED, executable verification pending

The human import path and diagnostics were verified in CAP-09. The machine ingestion boundary additionally requires `ROLE_SERVICE`, scoped create/read authorities, tenant context, source code and `Idempotency-Key`, and returns an asynchronous reservation/status contract. Current source tree includes worker, listener and recovery scheduler components. No A01 capability omission is asserted in this pass; A06/A08 will audit crash/retry/outbox correctness.

### CAP-15 Service clients / machine-to-machine integration — implementation coverage VERIFIED, executable verification pending

Tenant UI exposes service-client list/create/detail, allowed scopes, one-time secret presentation, staged secret rotation/activation and block/unblock. Backend enforces separate create/read/rotate/block permissions. Integration Source UI exposes readiness checks and prevents UI activation until readiness is true; lifecycle commands carry source version. Integration Setup Center provides navigable entry points for clients, sources, schemas and mappings.

No manual SQL/curl dependency was found for the configuration lifecycle itself. Exact-SHA tests and A07 tenant/security review remain required.


## CAP-16..CAP-20 deep pass — evidence update

### CAP-16 Dashboard operational projections — implementation coverage VERIFIED, executable verification pending

Tenant dashboard has a real UI and backend endpoints for summary, receivables, delivery and collections. Financial values cross the public boundary through `DecimalString`. The page independently handles the four query surfaces, refresh and forbidden behavior. No A01 capability omission was found.

### CAP-17 Tenant analytics/reporting — PARTIAL / DEFECT A01-008

Backend reporting is substantially implemented: tenant financial summary/timeseries and communication summary/timeseries/channels/users/campaigns/failures/lifecycle/audience/attempts/attachments/operations/documents exist, backed by financial and communication projection/rebuild/state/scheduler services.

The tenant frontend has no analytics/reporting page directory or router entry. The only analytics route in the current router is the platform placeholder.

**Impact:** tenant users cannot consume the implemented tenant-scoped analytics/reporting product through the UI. This breaks the intended VC-9 tenant-reporting outcome even though the backend projection layer exists.

**Classification:** P1 product capability gap.

**Required remediation:** implement tenant Analytics routes/pages for financial and communication reporting, date/bucket/filter controls, projection/as-of visibility where applicable, deep links to campaign/run/user context, permission gates and API/page tests.

### CAP-18 Platform administration — PARTIAL but product surface exists; scope decision required

Contrary to the older tenant-MVP exclusion note, the current product now contains a distinct protected `/platform` route tree and pages for overview, tenants, tenant detail, users, user detail and platform administrators. Backend platform overview/tenant/user services also exist.

The platform `/platform/analytics` route is explicitly a placeholder. This does not invalidate tenant MVP capability, but it means platform administration is not fully complete if platform analytics is part of the release scope. A01-002 remains a scope-verification finding rather than being promoted to a P1 defect in this pass.

### CAP-19 External provider delivery — PARTIAL / certification evidence required

A real KumoMTA EMAIL adapter exists and is selected explicitly with `collectra.communication.delivery.provider=kumomta`; simulation is a separate provider selected with `provider=simulated`. The KumoMTA gateway validates email commands/attachments, submits an inject request and distinguishes accepted, permanent/retryable rejection and ambiguous timeout outcomes.

Repository implementation therefore proves that real-provider code exists; it does **not** prove live provider certification, network/TLS/credential correctness or production delivery. A01-003 remains VERIFYING until a controlled live-provider acceptance gate supplies evidence. No claim of production-certified delivery is made.

### CAP-20 Operational recovery/diagnostics — PARTIAL / DEFECT A01-009

Recovery and observability mechanisms exist in backend code: stale message PROCESSING recovery, due-retry dispatch, delivery stuck/retry/queue-age gauges, dead-letter depth/arrival metrics, ingestion recovery, generation-job recovery and projection rebuild/state machinery. Human ingestion diagnostics and message-level delivery errors are visible in existing tenant UI surfaces.

However, no consolidated authorized operational/recovery surface exists in the tenant frontend for ingestion batches, stuck/recovered delivery, DLQ health, generation recovery or projection health. `IngestionOperationsController` provides a human-readable operational API, but no matching frontend page/route was found.

**Impact:** operators must rely on metrics/API/manual tooling for several asynchronous recovery states. For a self-service product this leaves recovery visibility fragmented and prevents CAP-20 from being considered complete.

**Classification:** P2 operational product gap (not P1 because automatic recovery mechanisms exist and core tenant workflows are not necessarily blocked).

**Required remediation:** add an authorized Operations/Diagnostics surface that aggregates ingestion batch diagnostics and safe async health/recovery state; provide deep links to existing message/import/document entities; never expose raw secrets/payloads; define which metrics remain infrastructure-only.

## A01 discovery summary

The first full capability discovery pass over CAP-01..CAP-20 is complete. Final closure still requires remediation and executable exact-SHA verification.

Confirmed remediation defects:
- A01-004 P1 — missing tenant Payments/Allocation/Reversal UI;
- A01-005 P1 — invalid general Files upload categories;
- A01-006 P1 — missing tenant RBAC administration UI;
- A01-007 P1 — missing Profile/Security/Sessions UI;
- A01-008 P1 — missing tenant Analytics/Reporting UI;
- A01-009 P2 — fragmented operational/recovery visibility.

Scope/certification findings still requiring an explicit decision/evidence:
- A01-002 — platform administration release scope, especially platform analytics placeholder;
- A01-003 — real KumoMTA live-provider certification.

A01 must remain IN_PROGRESS until P1 defects are remediated and exact-SHA verification gates are green.
