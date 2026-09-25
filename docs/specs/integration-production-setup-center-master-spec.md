# Unified Integration Production + Setup Center Specification

> Status: implementation-ready master specification
> Baseline: current main
> Purpose: one source of truth for remaining integration backend and frontend work.

## 1. Business outcome

A tenant administrator can connect an external system without adapting it to Collectra internal DTOs, configure and test the input contract, activate it, and observe a production flow from inbound data to persistence, generated content and delivery.

Golden path:

```text
Integration Setup Center
 -> IntegrationSource
 -> Service Client
 -> Source Schema
 -> Mapping Profile
 -> header/resource/routing policy
 -> Test Ingestion
 -> readiness -> Activate

ERP / 1C / CRM
 -> POST /api/v1/integration/sources/{sourceCode}/ingestions
 -> IngestionContext
 -> raw archival
 -> outbox/async worker
 -> parser -> mapping -> NormalizedDocument
 -> persistence
 -> URL REFERENCE/IMPORT -> internal file
 -> rendering
 -> explicit routing
 -> Message + attachments
 -> delivery worker
 -> mock/real provider
 -> reporting/diagnostics
```

Production callers use stable `sourceCode` + credentials + idempotency key; they do not send internal mapping/template UUIDs per request.

## 2. Audit of current main

| Capability | Verified state |
|---|---|
| Service Client backend | Implemented: create/list, rotation/activation, block/unblock, scopes, tenant isolation |
| Source Schema backend | Implemented: definitions/versions, JSON/XML/CSV/EXCEL, fields, row config, validate/publish/reopen/archive |
| Mapping Profile backend | Implemented: definitions/versions/rules, transformations, rule/full-file test, validate/lifecycle |
| Parser | JSON/XML/CSV/XLSX through `DocumentInputParser`; current hard limit 10 MB |
| ImportBatch | Implemented, but processing is synchronous in request thread behind HTTP 202 |
| BusinessImport | Separate mapping -> canonical persistence flow exists |
| File/template/assets/document | Existing reusable capabilities |
| Communication/attachments/delivery | Existing Message/outbox/READY gate/worker/provider pipeline |
| Mock provider | `SimulatedDeliveryGateway` exists |
| Reporting | Tenant-scoped communication/reporting coverage exists |
| IntegrationSource | Missing |
| IngestionContext | Missing |
| Unified NormalizedDocument orchestration | Missing |
| RemoteResourceResolver / SSRF-safe URL import | Missing |
| Integration frontend modules | Missing on current main |

Current `ImportBatchService.create()` reserves and then directly invokes processing before returning. Current `ImportBatchProcessingService` maps directly to generation while `BusinessImportService` is a separate post-mapping flow. These are the principal production orchestration gaps.

## 3. Architectural invariants

1. Keep the modular monolith; PostgreSQL remains source of truth.
2. `ServiceClient` is authentication identity; `IntegrationSource` is processing configuration.
3. `NormalizedDocument` is the common post-mapping boundary.
4. Mapping never implicitly sends; delivery requires explicit routing.
5. Every accepted production ingestion is durably reproducible from metadata + archived raw-source reference/checksum.
6. HTTP 202 means durable acceptance, not completed processing.
7. No DB transaction spans remote HTTP, object streaming, rendering or provider calls.
8. Remote URLs are fetched only by an SSRF-hardened resolver.
9. Tenant comes from authenticated context, never payload/header.
10. Workers are idempotent/recovery-safe.
11. Frontend readiness is derived from backend facts.

## 4. Target domain

### IntegrationSource

```text
id UUID PK
tenant_id UUID NOT NULL
code varchar(100) NOT NULL
name varchar(200) NOT NULL
status DRAFT|ACTIVE|SUSPENDED|ARCHIVED
service_client_id UUID NOT NULL
source_schema_definition_id UUID NOT NULL
mapping_profile_definition_id UUID NOT NULL
processing_mode varchar(30) NOT NULL
header_mapping jsonb NOT NULL
resource_policy jsonb NOT NULL
routing_config jsonb NOT NULL
version bigint NOT NULL
audit fields
UNIQUE(tenant_id, code)
```

Activation returns a readiness checklist and runtime records exact schema/mapping versions actually used.

### IngestionContext

Immutable: tenantId, integrationSourceId, serviceClientId, ingestionId, idempotencyKey, correlationId, receivedAt, contentType, optional sourceEventId/businessDate/documentTypeHint, and allowlisted attributes. Configurable headers cannot override protected fields.

### IngestionBatch

Evolve/unify provenance with integration source/client, idempotency/request hash, raw_source_file_id, exact schema/mapping versions/hash, content type, counters, timestamps, safe error and context snapshot.

States: `RECEIVED -> QUEUED -> PROCESSING -> COMPLETED | PARTIALLY_COMPLETED | FAILED`.

### IngestionRecordDiagnostic

Persist pageable safe diagnostics with record order/key, stage, status, target identity, stable error code/message and field path.

### NormalizedDocument

Immutable application contract containing document order/key/type, normalized payload, exact mapping/schema versions/hash, context and resolved internal resources. Persistence, rendering and routing consume it.

## 5. Production API

Human setup:

```http
GET/POST /api/v1/integration/sources
GET/PUT  /api/v1/integration/sources/{id}
POST     /api/v1/integration/sources/{id}/validate
POST     /api/v1/integration/sources/{id}/activate
POST     /api/v1/integration/sources/{id}/suspend
POST     /api/v1/integration/sources/{id}/archive
```

Machine:

```http
POST /api/v1/integration/sources/{sourceCode}/ingestions
Authorization: Bearer <service-token>
Idempotency-Key: <stable-key>
X-Correlation-Id: optional
Content-Type: configured JSON/XML/file form
```

After durable acceptance return 202 with ingestionId, sourceCode, QUEUED, replayed and receivedAt.

Observation:

```http
GET /api/v1/integration/sources/{sourceCode}/ingestions/{id}
GET /api/v1/integration/sources/{sourceCode}/ingestions/{id}/diagnostics?page=0&size=50
```

## 6. Headers / IngestionContext

Header mapping is declarative and allowlisted. Enforce case-insensitive lookup, bounded count/value/bytes, CR/LF rejection, explicit multi-value policy, prohibition of Authorization/Cookie/Set-Cookie, protected-target rejection, required-header validation before queueing, filtered/masked context persistence.

## 7. Raw archival

Before successful 202, store body/file via FileService as `IMPORT_SOURCE`; persist file id, SHA-256, MIME, size and provenance. Never log raw payload. Storage failure means no successful acceptance. Retention may delete bytes later while audit metadata/checksum remain.

## 8. True async processing

Request path:

```text
authenticate source/client
 -> validate ACTIVE + headers
 -> idempotency
 -> raw archive
 -> reserve batch
 -> transactional outbox INGESTION_PROCESSING_REQUESTED
 -> 202
```

Worker:

```text
claim
 -> snapshot exact config
 -> parse -> map -> NormalizedDocument[]
 -> resolve resources
 -> persistence
 -> render/generate
 -> explicit route
 -> diagnostics/counters
 -> complete
```

Implement bounded concurrency, lease/claim, stale recovery, transient-only retries, attempts and backpressure.

## 9. Idempotency

Boundary: tenant + IntegrationSource + Idempotency-Key.

Same key + same canonical relevant request/context -> replay existing ingestion. Same key + changed request -> 409. Downstream persistence/generation/message creation must also be duplicate-safe under at-least-once worker execution.

## 10. URL -> internal file / SSRF

`REFERENCE`: validate/preserve; never fetch.

`IMPORT`: only `RemoteResourceResolver` fetches, validates and stores through FileService; result exposes internal fileId/MIME/size/checksum.

Mandatory controls: HTTPS default, optional hostname allowlist, reject loopback/private/link-local/multicast/unspecified/cloud metadata, DNS validation, redirect revalidation/limit, DNS-rebinding protection, no auth/cookie forwarding, no URL user-info, connect/read/overall timeouts, size limits, MIME allowlist/sniffing, filename sanitization, TLS validation, bounded concurrency/quotas, sanitized logs.

Stable error taxonomy: REMOTE_URL_FORBIDDEN, REMOTE_DNS_FORBIDDEN, REMOTE_TIMEOUT, REMOTE_TOO_LARGE, REMOTE_CONTENT_TYPE_REJECTED, REMOTE_FETCH_FAILED.

## 11. Downstream orchestration

Refactor the new production path so `BusinessImportService` and generation/routing consume the common NormalizedDocument boundary. Legacy endpoints may remain compatibility facades.

Rendering uses normalized data/internal files/assets only. `routing_config` explicitly selects persistence/generation/communication actions. Reuse Message, attachment readiness, outbox/listener, MessageDeliveryWorker, retry/recovery and provider gateways.

## 12. Diagnostics / observability

Stages: PARSING, MAPPING, RESOURCE, PERSISTENCE, RENDERING, ROUTING, DELIVERY_HANDOFF.

Expose safe batch counters and pageable diagnostics. No stack traces/raw PII. Metrics cover received/completed/partial/failed, queue age/duration, record outcomes, remote fetch, stale recovery and replay using bounded-cardinality labels.

## 13. Integration Setup Center

`/integrations` becomes a stateful readiness dashboard, not a static page.

Flow:

```text
Integration Setup Center
 -> Integration Source
 -> Service Client UI
 -> Source Schema Studio
 -> Mapping Studio
 -> header/resource/routing policy
 -> Test Ingestion
 -> Readiness
 -> Activate
 -> Activity / delivery analytics
```

Each step shows NOT_STARTED/IN_PROGRESS/READY/BLOCKED/PLANNED, factual backend summary, blocker and exact next action.

Routes include sources, service-clients, source-schemas, mapping-profiles and test-ingestion detail/edit paths. Existing assets/templates/campaign/reporting screens are linked, not duplicated.

## 14. Frontend waves ISC-1..ISC-7

### ISC-1 API/domain adapters
Feature-sliced DTO/API/query modules for IntegrationSource/readiness, ServiceClient, SourceSchema, MappingProfile, ingestion/diagnostics. Contract tests assert method/path/query/body/form-data/headers.

### ISC-2 Service Client UI
List/create/detail, scopes/expiry, rotate, activate rotating credential, block/unblock. Never persist/log secrets or put them in URL/browser storage/query cache.

### ISC-3 Source Schema Studio
Definition/version lifecycle, JSON/XML/CSV/XLSX format, fields, row configuration, validate/publish/reopen/archive. Published versions read-only until legal lifecycle transition.

### ISC-4 Mapping Studio
Definition/version, source schema selection, visual rules, target catalogue, supported transformations, default/required, single-rule test, full-file test, validate/publish/reopen/archive. Do not duplicate divergent transformation/catalogue metadata.

### ISC-5 Test Ingestion
Wizard: source/mapping -> FILE/JSON/XML -> optional legacy template/output test policy -> review -> Idempotency-Key -> submit -> status/diagnostics. Clearly distinguish legacy ImportBatch test mode from new source-oriented production test. Ambiguous retry reuses the logical key.

### ISC-6 Real Setup Center
Replace static cards with backend-derived readiness. Next-action order: source -> usable client -> published schema -> published mapping -> policies -> successful source test -> readiness blockers -> activate -> observe. Backend validate/readiness is authoritative.

### ISC-7 Hardening
Accessibility, responsive/loading/empty/error/409/403 states, unsaved-change guards, permission matrix, deep links/reload, tenant isolation, architecture/API contract tests and full CI.

## 15. Backend waves I0..I7

### I0 Existing-foundation smoke gate
Executable JSON/XML/CSV/XLSX fixture smoke through real parser + published mapping + normalized result + generation + Message/attachment + delivery worker + SimulatedDeliveryGateway. Assert actual destination/channel/subject/body and attachment bytes/name/MIME where applicable.

### I1 IntegrationSource + IngestionContext
Liquibase/domain/repository/service/controller, lifecycle/readiness, bindings, header/resource/routing config, optimistic versioning, tenant/auth tests.

### I2 Source-oriented durable ingestion
Stable sourceCode endpoint, idempotency, raw archive, provenance, outbox and 202 after durable reservation.

### I3 NormalizedDocument + diagnostics
Common post-mapping contract and durable pageable diagnostics; production path consumes it without breaking legacy APIs.

### I4 RemoteResourceResolver
REFERENCE/IMPORT, FileService storage, SSRF controls, quotas, error taxonomy and malicious redirect/private-address tests.

### I5 Async worker/recovery/backpressure
Claim/lease, worker, stale recovery, transient retry, concurrency/record/byte limits and metrics.

### I6 Persistence/render/routing consumers
One normalized result drives configured downstream actions; reuse existing delivery subsystem and prove no duplicate effects.

### I7 Production activation/observation
Readiness/activity/status/diagnostic APIs, operational metrics and complete golden-path smoke.

## 16. Delivery order

```text
I0
 -> I1 + ISC-1
 -> ISC-2
 -> ISC-3
 -> ISC-4
 -> I2 + I3
 -> ISC-5
 -> I4
 -> I5
 -> I6
 -> I7 + ISC-6
 -> ISC-7
```

This allows UI for existing ServiceClient/Schema/Mapping APIs early while source-oriented ingestion UI waits for stable I1-I3 contracts.

## 17. Acceptance matrix

| ID | Scenario | Gate |
|---|---|---|
| A01 | Service Client create/rotate/activate/block via UI | reload-safe; no secret persistence |
| A02 | JSON schema create/validate/publish | deep-link restores published state |
| A03 | CSV/XLSX row config | schema/parser semantics preserved |
| A04 | Mapping single-rule test | exact backend transformation shown |
| A05 | Whole mapping test | normalized result/safe errors visible |
| A06 | DRAFT IntegrationSource | tenant-bound refs validated |
| A07 | Missing configuration | deterministic readiness blockers |
| A08 | Ready source activation | ACTIVE + optimistic concurrency |
| A09 | Valid production ingestion | raw archived, 202 QUEUED, worker completes |
| A10 | Same key/same request | replay; no duplicate effects |
| A11 | Same key/changed request | 409 |
| A12 | Required mapped header absent | deterministic pre-queue 4xx |
| A13 | Header security override | cannot alter protected context |
| A14 | Public HTTPS IMPORT | internal READY file/checksum |
| A15 | private/localhost/link-local/metadata URL | rejected without unsafe connection |
| A16 | public redirect to private | rejected |
| A17 | REFERENCE URL | no resolver fetch |
| A18 | worker crash after claim | stale recovery without duplicates |
| A19 | mapping/business validation failure | safe diagnostic; no accidental send |
| A20 | required attachment not READY | provider not invoked |
| A21 | routed EMAIL | subject/body/attachment reaches simulated gateway |
| A22 | every supported channel | explicit channel semantics |
| A23 | cross-tenant IDs/source | no leakage |
| A24 | suspended/archived source | ingestion rejected |
| A25 | input above configured bound | deterministic bounded failure/backpressure |
| A26 | Setup Center reload | exact readiness/next step reconstructed |
| A27 | ACTIVE activity | ingestion + downstream delivery observable |

## 18. Security / operations release gates

Release is blocked without tenant isolation for every new repository/API; tenant-safe client/source binding; no secrets/raw payload logs; complete SSRF redirect/IP tests; no arbitrary URL fetch outside resolver; internal-file-only render/provider paths; duplicate-safe replay/recovery; safe diagnostics; backend permission enforcement; bounded inputs/workers; short DB transactions; pageable diagnostics; bounded metrics; stale recovery; retry classification; retention documentation and configuration audit.

The current 10 MB parser limit remains a safety boundary until a streaming/large-input strategy is explicitly implemented/tested.

## 19. Definition of Done

A tenant admin can, without Postman/database edits: create/select source; configure credentials; publish schema; test/publish mapping; configure headers/resource/routing policy; run source test and inspect diagnostics; resolve readiness and activate; send production JSON/XML/file using sourceCode + credentials + idempotency key; observe raw provenance through delivery; safely import allowed remote files; replay/recover without duplicates; and see the complete state in Integration Setup Center.

CI includes acceptance matrix, tenant/security tests, parser fixture matrix, simulated-provider smoke, frontend contract tests/build, backend Testcontainers verify and OpenAPI verification.

## 20. Explicit non-goals

No generic BPM engine, arbitrary scripts, mandatory Kafka, new object store, duplicate mapping/template/delivery system, renderer-side remote fetch or premature microservice split. Legacy APIs remain compatible until the source-oriented path has tested parity and consumers migrate.
