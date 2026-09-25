# Integration Setup Center — implementation-ready frontend/backend contract

Status: READY FOR IMPLEMENTATION AFTER CURRENT BRANCH REVIEW  
Target branch after this specification is merged: create implementation branch from current `main`.  
Frontend stack: existing `frontendweb` React application.  
Backend baseline: existing Service Client, Source Schema, Mapping Profile and Import Batch APIs.

## 1. Goal

Turn the current `/integrations` journey page into a tenant self-service **Integration Setup Center** that guides a user through one coherent business process:

```text
Integration Setup Center
  -> Service Client UI
  -> Source Schema Studio
  -> Mapping Studio
  -> Test Ingestion
  -> readiness result / next action
```

The UI must expose the backend capabilities that already exist, without duplicating backend business logic. The user must always see: current state, blocking problem, recommended next step, and a safe link/action to continue.

This slice does **not** pretend that the future IntegrationSource/unified production orchestration already exists. Until that backend contract is implemented, the Setup Center acts as an orchestration/readiness UX over the current APIs and clearly marks unavailable production-source binding as planned.

## 2. Current verified backend baseline

### Service Client

Existing `/api/v1/integration/service-clients` API supports:

- create;
- list;
- secret rotation;
- activation of rotating credential;
- block/unblock;
- tenant isolation and permission checks.

Secrets are write-only. The UI must never expect the backend to return a raw secret.

### Source Schema

Existing `/api/v1/source-schemas` API supports:

- definition create/list/rename;
- version create/list;
- formats `EXCEL | CSV | JSON | XML`;
- fields create/update/delete;
- row configuration;
- validation;
- publish/reopen/archive lifecycle.

### Mapping Profile

Existing `/api/v1/mapping-profiles` API supports:

- definition create/list;
- version create/list;
- source-schema-version binding;
- mapping rules CRUD;
- full mapping validation;
- whole-file test;
- single-rule test;
- publish/reopen/archive lifecycle.

### Test ingestion

Existing `/api/v1/import-batches` supports multipart, JSON and XML submission with `Idempotency-Key`, mapping profile version, template version and output formats. `GET /api/v1/import-batches/{batchId}` restores the batch.

Important current constraint: HTTP 202 does not prove asynchronous execution. Current service may finish processing before the response. Frontend must use returned batch status as authoritative.

Existing `/api/v1/business-imports/mapping-profiles/{versionId}` is a separate business-persistence flow. The Setup Center must not hide this architectural split or invent a unified production flow in the browser.

## 3. Product principles

1. User journey is primary; Java modules and UUIDs are implementation details.
2. Every screen has a visible **Next step**.
3. Existing entities are reused. Do not create frontend-only source/mapping state.
4. Server status is authoritative. React derives presentation/readiness only.
5. Published schema/profile versions are visibly immutable.
6. Destructive/lifecycle actions require explicit confirmation.
7. No secret, raw credential, Authorization header or sensitive source value is persisted in browser storage or logged.
8. Deep links must restore enough state from backend to render safely.
9. Permission denial disables/hides mutation actions but does not fabricate data.
10. No browser-side ETL or parsing of large business files.

## 4. Information architecture

Routes:

```text
/integrations
/integrations/service-clients
/integrations/service-clients/new
/integrations/service-clients/:clientId

/integrations/source-schemas
/integrations/source-schemas/new
/integrations/source-schemas/:definitionId
/integrations/source-schemas/:definitionId/versions/:versionId

/integrations/mapping-profiles
/integrations/mapping-profiles/new
/integrations/mapping-profiles/:definitionId
/integrations/mapping-profiles/:definitionId/versions/:versionId

/integrations/test-ingestion
/integrations/test-ingestion/:batchId
```

The existing `/imports` domain remains valid for operational import history. Integration Setup Center is the onboarding/configuration surface; it may link to import history instead of duplicating it.

## 5. Integration Setup Center

### 5.1 Screen

The root page becomes a stateful setup dashboard.

Each card has:

- title and business explanation;
- status: `NOT_STARTED | IN_PROGRESS | READY | BLOCKED | PLANNED`;
- factual summary from backend;
- blocker/reason;
- primary action;
- optional secondary action;
- permission-aware behavior.

Initial cards:

```text
1 API access / Service Client
2 Source format / Source Schema
3 Field mapping / Mapping Profile
4 Test ingestion
5 Assets
6 Templates
7 Campaign / delivery
8 Production source binding             PLANNED until IntegrationSource exists
```

### 5.2 Readiness semantics

Do not show a fake global percentage based only on route existence.

For the current API generation:

- Service Client READY: at least one non-blocked usable client exists.
- Source Schema READY: at least one PUBLISHED schema version exists.
- Mapping READY: at least one PUBLISHED mapping version exists and references an appropriate published schema.
- Test Ingestion READY: a user-selected test batch reaches an accepted/success terminal state; the UI must not infer this from HTTP 202.
- Assets/Templates/Campaigns link to their existing workspaces and use only data already available through their APIs.
- Production binding remains PLANNED until backend IntegrationSource exists.

A summary endpoint may be introduced later, but this slice may aggregate bounded existing list calls. It must not perform N+1 calls per row.

### 5.3 Next-step engine

Implement a pure frontend selector over fetched authoritative state:

```text
no usable service client -> Create API access
no published schema       -> Describe input format
no published mapping      -> Map fields
no successful test        -> Run test ingestion
otherwise                 -> Continue to template/campaign setup
```

It returns route + translation key, not free-form backend-derived HTML.

## 6. Service Client UI

### 6.1 List

Show:

- name;
- clientId;
- status;
- scopes;
- client expiration;
- credential status/expiration when present in DTO;
- last-used timestamp when exposed by backend.

Actions follow backend permissions exactly.

### 6.2 Create

Fields:

- clientId;
- display name;
- client secret;
- scopes from the backend-supported allowlist;
- optional client expiration;
- optional secret expiration.

Secret requirements must be explained before submit. Do not auto-save the secret.

After successful creation:

- clear secret from React state as soon as practical;
- never place it in URL/query/cache/localStorage/sessionStorage;
- do not display it if the backend does not return it;
- show configuration guidance using the clientId and token endpoint, without fabricating a secret.

### 6.3 Rotation

Flow:

```text
Generate/provide new secret
 -> POST rotate-secret
 -> show ROTATING state
 -> explicit Activate credential
 -> POST credentials/{credentialId}/activate
 -> refresh client
```

Block/unblock is confirmed and immediately invalidates related queries.

### 6.4 Permissions

Map existing backend authorities:

`SERVICE_CLIENT_CREATE`, `SERVICE_CLIENT_READ`, `SERVICE_CLIENT_ROTATE_SECRET`, `SERVICE_CLIENT_BLOCK`.

Frontend guards are UX only; backend remains enforcement point.

## 7. Source Schema Studio

### 7.1 Definition list/detail

List definition code/name and versions. Provide create and rename where permitted.

Definition detail must show version timeline and clear status badges.

### 7.2 Version editor

Header:

- definition name/code;
- version;
- source format;
- lifecycle status;
- validation state.

Editor sections:

**Fields**

Columns:

- source path;
- detected type;
- sample value;
- required;
- scope;
- document key;
- value policy;
- position.

CRUD maps one-to-one to current controller.

**Row configuration**

Expose:

- recordPath;
- rowTypeFieldId;
- itemValues;
- totalValues;
- ignoredValues.

Explain this section primarily for repeated/tabular records.

### 7.3 Lifecycle

```text
DRAFT -> validate -> publish
PUBLISHED -> reopen/archive where backend permits
```

Published version editor is read-only unless an allowed lifecycle command changes it.

Validation result must be rendered as actionable errors/warnings and never converted into a frontend-only validity flag.

### 7.4 UX constraints

Large lists are not parsed client-side. Because current backend configuration endpoints are list-based, enforce/document bounded UI behavior and do not advertise arbitrary scale until paging/bounds are closed.

## 8. Mapping Studio

### 8.1 Purpose

Mapping Studio is a visual configuration surface:

```text
SOURCE FIELD            TRANSFORMATION             TARGET FIELD
customer.email    ->    TRIM / VALIDATE_EMAIL ->   customer.email
amount            ->    DECIMAL_PARSE          ->   invoice.amount
phone             ->    NORMALIZE_PHONE        ->   customer.phone
```

### 8.2 Definition/version flow

Create definition with:

- code;
- name;
- documentType.

Create version by selecting a Source Schema version.

Prefer published schema versions for normal setup. If backend permits a draft schema technically, UI must clearly warn and must not present the integration as READY.

### 8.3 Rule editor

Each rule exposes:

- source field;
- target field;
- transformation;
- default value;
- required.

Target fields come from the existing field catalogue, not hardcoded duplicate frontend metadata.

Supported transformations must reflect backend capabilities. Do not invent transformations in UI.

### 8.4 Test one rule

Provide an inline test drawer:

```text
Input value -> POST rule/test -> transformed value / validation error
```

No rule is persisted merely because it was tested.

### 8.5 Test whole mapping

Allow bounded test-file upload to current `POST .../versions/{versionId}/test`.

Display:

- parsed/normalized result;
- mapping errors;
- document boundaries when returned;
- safe diagnostics.

Raw sensitive payload must not be written to browser logs.

### 8.6 Validation/lifecycle

Validate before publish. Render backend result directly. Published version is read-only.

## 9. Test Ingestion

### 9.1 Wizard

```text
Step 1 Mapping
  choose PUBLISHED mapping version

Step 2 Template
  choose compatible published template version
  output formats

Step 3 Input
  FILE | JSON | XML

Step 4 Review
  mapping
  template
  formats
  input metadata
  generated Idempotency-Key

Step 5 Execute
  submit
  -> batch result
```

For multipart, JSON and XML use the corresponding current endpoints.

### 9.2 Idempotency

Generate one UUID idempotency key per logical submission.

Rules:

- same logical retry after ambiguous network failure reuses the same key;
- changing payload/file/mapping/template/formats creates a new intent and new key;
- never silently replay with a different key;
- expose `replayed` if backend response supplies it.

### 9.3 Result screen

Show authoritative batch fields:

- batch id;
- status;
- replay state;
- counts/result summary returned by API;
- safe failure code/message;
- generated document references when available.

Polling only starts for an explicitly non-terminal returned status. Stop on terminal status, component unmount and hidden-tab policy defined in FW10.

### 9.4 Known diagnostics limitation

Current durable row/field diagnostics are not sufficient for the final operational UX. The test screen may show diagnostics returned by the immediate mapping/import response, but must label batch-level errors correctly.

Do not synthesize row errors from one batch error.

Durable paged row diagnostics remain a backend prerequisite for full FW10 operational import history.

## 10. Frontend architecture

Create/reuse feature-sliced modules:

```text
entities/integration-client/
entities/source-schema/
entities/mapping-profile/
entities/import-batch/

features/integration-client-management/
features/source-schema-management/
features/mapping-profile-management/
features/test-ingestion/

pages/integrations/
```

Rules:

- API calls live in entity/feature API modules, never directly in page JSX;
- DTO types mirror public API, not JPA entities;
- query keys are domain-specific;
- mutations invalidate the smallest required query set;
- pages compose features and own navigation only;
- no cross-import that violates current architecture test.

## 11. API contract tests

For every frontend API function add tests that assert:

- HTTP method;
- encoded path;
- query params;
- request body/form-data;
- required header (`Idempotency-Key`);
- DTO parsing assumptions.

Add page/feature tests for success, loading, empty, validation failure, 403, conflict and network ambiguity where applicable.

## 12. Security requirements

- never persist or log client secrets;
- no Authorization header display in diagnostics;
- source sample values are treated as potentially sensitive;
- tenantId is never user-selectable in tenant workspace;
- permission checks mirror backend but never replace backend authorization;
- rendered server error text must be treated as text, not HTML;
- file input accepts only formats supported by selected schema/test endpoint;
- no arbitrary URL fetching is introduced by this slice;
- remote URL import/SSRF remains a separate backend slice.

## 13. Accessibility and usability

Every wizard/editor must provide:

- visible page title and current step;
- labels independent of placeholders;
- keyboard-accessible actions;
- focus to first validation error after failed submit;
- loading state that prevents duplicate mutations;
- empty-state next action;
- confirmation for lifecycle/destructive operations;
- unsaved-change navigation warning in editors;
- responsive layout without hiding required controls.

Do not use status color as the only status signal.

## 14. Backend changes allowed in this implementation

The implementation should first reuse current APIs. Backend changes are allowed only where the UI cannot safely restore or scale a workflow.

Priority candidates already identified by FW10:

1. direct tenant-scoped detail projection for schema/profile definition/version if deep-link restoration otherwise requires list scanning;
2. explicit bounded limits or paging for configuration lists;
3. revision/ETag optimistic concurrency for mutable configuration editors;
4. durable paged row/field import diagnostics.

These changes must preserve existing endpoints or provide a documented migration path.

The future `IntegrationSource`, header mapping, unified ingestion orchestration, raw source archival, remote-resource import and true async large ingestion are **not** silently folded into this frontend slice. They remain their own backend implementation contract.

## 15. End-to-end acceptance scenarios

### A. API access

Tenant admin creates service client -> sees it in list -> rotates secret -> activates new credential -> blocks/unblocks -> state survives reload.

### B. JSON schema

User creates JSON source schema -> adds fields -> validates -> publishes -> reload/deep link reproduces published state.

### C. Tabular schema

User creates CSV/XLSX schema -> configures fields and row configuration -> validates -> publishes.

### D. Mapping

User creates mapping definition/version -> maps source fields to target catalogue -> tests one transformation -> tests whole file -> validates -> publishes.

### E. Test ingestion — file

Published mapping + template -> upload file -> one Idempotency-Key -> batch result -> generated outputs/status displayed.

### F. Test ingestion — JSON/XML

Same flow through dedicated body endpoints. Content type and payload mode must match backend endpoint.

### G. Safe replay

Simulated ambiguous response -> user explicitly retries same logical submission -> same Idempotency-Key -> replay is displayed, not duplicated.

### H. Permissions

Read-only user can inspect allowed configuration but cannot mutate. Direct mutation attempt remains rejected by backend.

### I. Tenant isolation

IDs from another tenant never expose resource existence or data through list/detail/editor/test flows.

## 16. Implementation waves

### ISC-1 — API/domain adapters

Implement DTOs, API clients, query keys and contract tests for Service Client, Source Schema, Mapping Profile and Import Batch.

### ISC-2 — Service Client UI

List/create/detail/rotate/activate/block/unblock plus secret-safety tests.

### ISC-3 — Source Schema Studio

Definitions, versions, fields, row config, validation and lifecycle.

### ISC-4 — Mapping Studio

Definition/version/rules, field catalogue integration, rule test, full test, validation and lifecycle.

### ISC-5 — Test Ingestion

File/JSON/XML wizard, idempotency semantics, result/replay/polling.

### ISC-6 — Setup Center orchestration UX

Replace static journey statuses with factual readiness derived from the implemented modules and wire all next-step actions.

### ISC-7 — hardening

Accessibility, responsive states, permission matrix, error mapping, deep-link/reload, architecture tests and full frontend CI.

Do not implement all waves as one uncontrolled component. They may live in one release branch, but commits/PR review should preserve these boundaries.

## 17. Definition of Done

The slice is complete only when:

- a tenant user can manage a Service Client through UI;
- a tenant user can create, edit, validate and publish Source Schema versions;
- a tenant user can create, edit, test, validate and publish Mapping Profile versions;
- a tenant user can perform file/JSON/XML test ingestion without curl/Postman;
- idempotency and ambiguous retry semantics are covered by tests;
- Setup Center derives real readiness and links to the exact next action;
- no planned IntegrationSource/unified-orchestration capability is represented as already working;
- permission, tenant isolation and secret-handling requirements are preserved;
- deep links/reload work for editors/results;
- frontend architecture tests pass;
- frontend unit/integration tests pass;
- backend/OpenAPI tests pass for any API change;
- full repository CI is green;
- implementation PR contains an explicit acceptance matrix for scenarios A-I.

## 18. Out of scope / next backend track

After this frontend/self-service slice, implement the production integration architecture:

```text
IntegrationSource
 -> normalized IngestionContext / configurable headers
 -> raw source archival
 -> async ingestion
 -> NormalizedDocument
 -> business persistence
 -> remote resource resolution with SSRF protection
 -> template/render
 -> communication routing
 -> delivery
 -> durable row diagnostics / reporting
```

That track will convert the Setup Center from configuration + test ingestion into complete source-to-delivery production onboarding.
