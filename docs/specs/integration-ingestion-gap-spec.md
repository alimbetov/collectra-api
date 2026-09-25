# Integration Ingestion Gap Specification

> Status: implementation-ready specification
>
> Related reference: [Collectra Integration Journey](./integration-journey.md)

## 1. Purpose

Close the gaps between existing Collectra mapping/import/file/template/communication capabilities and a production B2B flow in which a tenant connects an external system and submits source-specific data without adapting that system to Collectra internal DTOs.

The design must preserve the modular-monolith architecture. PostgreSQL remains the source of truth. Existing FileService/RustFS, mapping, template, message, outbox and reporting capabilities are reused.

## 2. Scope

This specification covers: `IntegrationSource`; normalized `IngestionContext` including variable source headers; unified ingestion orchestration; raw-source archival; row-level diagnostics; secure URL-to-internal-file resolution; and true asynchronous processing for large inputs.

It does not introduce arbitrary scripting, direct renderer access to remote URLs, a generic workflow/BPM engine, Kafka, a new object store, or a second mapping/template/credential implementation.

## 3. Domain model

### 3.1 IntegrationSource

Suggested table `integration_sources`:

```text
id                         uuid PK
tenant_id                  uuid NOT NULL
code                       varchar(100) NOT NULL
name                       varchar(200) NOT NULL
status                     varchar(20) NOT NULL
service_client_id          uuid NOT NULL
source_schema_definition_id uuid NOT NULL
mapping_profile_definition_id uuid NOT NULL
processing_mode            varchar(30) NOT NULL
routing_config             jsonb NULL
header_mapping             jsonb NOT NULL default '{}'
resource_policy            jsonb NOT NULL default '{}'
version                    bigint NOT NULL
created_at / updated_at / created_by / updated_by
```

Unique: `(tenant_id, code)`. Every lookup/update is tenant scoped. References must be verified to belong to the same tenant.

Status: `DRAFT -> ACTIVE -> SUSPENDED -> ARCHIVED`. Activation resolves and records usable published schema/mapping configuration; execution always records the exact version IDs actually used.

`ServiceClient` remains authentication identity. `IntegrationSource` is processing configuration. They must not be collapsed into one entity.

### 3.2 IngestionContext

Immutable application value object:

```text
tenantId                 server-derived
integrationSourceId      server-derived
serviceClientId          server-derived
ingestionId              server-generated
idempotencyKey           platform header
correlationId            validated/generated
receivedAt               server clock
contentType              platform header
sourceEventId            optional mapped source header
businessDate             optional mapped source header
documentTypeHint         optional mapped source header
attributes               normalized allowlisted source-header values
```

No mapped header may set `tenantId`, `serviceClientId`, `integrationSourceId`, authorization/scopes, idempotency key, receivedAt, mapping/template IDs, or security decisions.

### 3.3 IngestionBatch

Unify provenance around an ingestion batch. Required data:

```text
id
tenant_id
integration_source_id
service_client_id
idempotency_key
request_hash
raw_source_file_id
source_schema_version_id
mapping_profile_version_id
mapping_config_sha256
status
content_type
record_count
accepted_count
failed_count
received_at
processing_started_at
completed_at
error_code
safe_error_message
context_json
```

Recommended states:

```text
RECEIVED
 -> QUEUED
 -> PROCESSING
 -> COMPLETED

PROCESSING -> PARTIALLY_COMPLETED
PROCESSING -> FAILED
QUEUED/PROCESSING -> CANCELLED (future/admin only)
```

Transitions must be idempotent and auditable.

### 3.4 IngestionRecordDiagnostic

Suggested table:

```text
id
tenant_id
ingestion_batch_id
record_order
document_key
status                 ACCEPTED | REUSED | FAILED | SKIPPED
target_type
target_id
external_id
error_code
safe_error_message
field_path
created_at
```

Never persist arbitrary exception stacks or unmasked source payload fragments in diagnostics.

## 4. API contract

### 4.1 Source management

Human-admin endpoints:

```http
GET    /api/v1/integration/sources
POST   /api/v1/integration/sources
GET    /api/v1/integration/sources/{id}
PUT    /api/v1/integration/sources/{id}
POST   /api/v1/integration/sources/{id}/activate
POST   /api/v1/integration/sources/{id}/suspend
POST   /api/v1/integration/sources/{id}/archive
POST   /api/v1/integration/sources/{id}/validate
```

The validate response should be a readiness checklist suitable for the frontend step map.

### 4.2 Machine ingestion

Preferred public contract:

```http
POST /api/v1/integration/sources/{sourceCode}/ingestions
Authorization: Bearer <service-token>
Idempotency-Key: <client-stable-key>
Content-Type: application/json
```

Also support configured formats already supported by SourceFormat where appropriate.

Response:

```json
{
  "ingestionId": "uuid",
  "sourceCode": "core-banking-prod",
  "status": "QUEUED",
  "replayed": false,
  "receivedAt": "2026-09-25T03:20:00Z"
}
```

HTTP 202 means accepted for processing, not completed.

Status:

```http
GET /api/v1/integration/sources/{sourceCode}/ingestions/{ingestionId}
```

Must expose aggregate counters and safe diagnostics, with pagination for row diagnostics.

### 4.3 Idempotency

Uniqueness boundary: tenant + integration source + idempotency key.

Same key + same canonical request hash returns the existing ingestion result with `replayed=true`. Same key + different request hash returns 409 conflict.

Hash must cover raw content plus processing-relevant trusted context. Volatile tracing headers must not change idempotency.

## 5. Header mapping

`IntegrationSource.headerMapping` is declarative and constrained. Example:

```json
{
  "X-Source-Event-Id": {"target": "sourceEventId", "required": true, "maxLength": 100},
  "X-Business-Date": {"target": "businessDate", "type": "DATE"},
  "X-Branch-Code": {"target": "attributes.branchCode", "maxLength": 30}
}
```

Rules: case-insensitive HTTP header lookup; canonical target keys are allowlisted; maximum mapped-header count and total byte size; no CR/LF; no Authorization/Cookie/Set-Cookie forwarding; values masked where configured; duplicate/multi-value behavior explicit; missing required header fails before queueing; context snapshot stored with the batch after filtering.

Do not expose arbitrary header names as template placeholders automatically. If business headers must become render data, an explicit mapping rule promotes a normalized context value into the canonical document.

## 6. Unified orchestration

Required application boundary:

```text
IngestionApplicationService
  -> authenticate/authorize source binding
  -> validate source ACTIVE
  -> validate headers -> IngestionContext
  -> enforce idempotency
  -> archive raw input
  -> reserve IngestionBatch
  -> publish processing request through transactional outbox
  -> return 202

IngestionWorker
  -> claim batch
  -> load exact active configuration snapshot
  -> parse
  -> map
  -> resolve declared remote resources
  -> execute configured business persistence
  -> render/generate if configured
  -> invoke explicit communication routing if configured
  -> persist diagnostics/counters
  -> complete batch
```

Mapping must not implicitly send communication. Sending requires an explicit source processing/routing configuration. This prevents an innocent mapping change from causing outbound messages.

Business persistence and document generation consume the same normalized mapped result rather than implementing parallel mapping flows.

## 7. Raw source archival

Every accepted ingestion, including JSON request bodies, must be stored through existing FileService as `IMPORT_SOURCE` before asynchronous processing.

Requirements: tenant-scoped object key; SHA-256; original content type; size; ingestion/source provenance; retention policy; no raw body logging; delete/expiry behavior consistent with FileService; ingestion metadata survives source-file expiry so audit still explains what existed and its checksum.

If object storage fails, do not return a successful 202 for an ingestion that cannot be reproduced.

## 8. Remote URL to internal file

### 8.1 Semantics

A mapped URL must declare one of:

`REFERENCE` — preserve validated URL as business/template data; Collectra does not fetch it.

`IMPORT` — fetch once through `RemoteResourceResolver`, validate it, store via FileService/RustFS, and replace/enrich normalized data with an internal file reference.

Renderer/provider code must never perform arbitrary remote fetches.

### 8.2 RemoteResourceResolver

Boundary:

```java
ResolvedResource resolve(
    UUID tenantId,
    URI sourceUri,
    RemoteResourcePolicy policy,
    IngestionContext context);
```

Result contains internal `fileId`, final validated URI metadata, content type, size and checksum. Credentials must not be embedded in URLs.

### 8.3 SSRF controls

Mandatory controls: HTTPS by default; optional per-source hostname allowlist; reject loopback, private, link-local, multicast, unspecified and cloud-metadata destinations; resolve DNS before connection; validate every resolved address; re-resolve/revalidate every redirect; redirect count limit; do not forward Authorization/cookies to remote hosts; connect/read/overall timeout; maximum compressed and decompressed size where relevant; content-type allowlist; content sniffing where practical; filename sanitization; TLS certificate validation; reject URLs containing user-info; bounded concurrency; per-tenant/source quotas; audit safe host/final status without query-string secrets.

Protection must account for DNS rebinding and redirect-to-private-address attacks.

Remote import failures use stable codes such as `REMOTE_URL_FORBIDDEN`, `REMOTE_DNS_FORBIDDEN`, `REMOTE_TIMEOUT`, `REMOTE_TOO_LARGE`, `REMOTE_CONTENT_TYPE_REJECTED`, `REMOTE_FETCH_FAILED`.

## 9. Async processing and backpressure

Current request-thread processing must be replaced for the source-oriented endpoint by queue/outbox-driven processing.

Requirements: bounded worker concurrency; database claim/lease; stale-processing recovery; retry only for transient infrastructure failures; no blind retry of mapping/business validation errors; maximum records and bytes per ingestion; rate limit per service client/source/tenant; pagination/streaming parser strategy for large payloads rather than retaining an unbounded document set in memory.

Small payloads still use the same asynchronous semantics so behavior does not change at an arbitrary size threshold.

## 10. Row-level diagnostics

One diagnostic contract must cover mapping, business persistence, resource resolution and rendering.

Example:

```json
{
  "recordOrder": 17,
  "documentKey": "INV-10017",
  "status": "FAILED",
  "stage": "MAPPING",
  "errorCode": "REQUIRED_FIELD_MISSING",
  "fieldPath": "invoice.dueDate",
  "message": "Required field is missing"
}
```

Errors are safe for tenant users. Internal exception detail belongs in protected operational logs with correlation/ingestion ID, never in the public diagnostic.

Support CSV error export later without changing the diagnostic domain model.

## 11. Template assets and remote resources

Reuse `TemplateAsset` for persistent tenant-owned visual assets such as `company_logo`, `header`, `signature` and `footer`.

Do not automatically promote every imported remote image into TemplateAsset. Remote resources attached to an ingestion/document are transient/provenance-linked files. Promotion into the tenant asset catalog is an explicit human/admin action.

## 12. Security and tenancy

Every new table carries `tenant_id` where operationally useful and every repository query is tenant scoped. Tenant is derived from authenticated service identity, never request body/header. A ServiceClient can ingest only through sources bound to the same tenant and permitted scope. Recommended new scopes: `integration:sources:read`/manage for humans as authorities and existing `integration:imports:create/read` for service clients unless a finer source-specific authorization becomes necessary.

PII/raw source content must not be logged. Audit security-relevant configuration changes, source activation/suspension, credential binding, resource-policy changes and processing replays.

## 13. Transaction boundaries

Do not hold a database transaction while performing remote HTTP, object-store streaming, document rendering or provider calls.

Reserve/transition state in short transactions. External side effects occur outside DB transactions. Persist resulting state in a new short transaction. Use outbox for durable handoff. Workers must be idempotent because delivery is at-least-once.

## 14. Observability

Metrics should include ingestion received/completed/partial/failed, processing duration, queue age, records accepted/failed, mapping failures by code, remote fetch attempts/failures/latency/bytes, stale recoveries and idempotent replays. All labels must be bounded-cardinality; do not use tenantId, URL, document key or ingestionId as Prometheus labels.

Structured logs include correlationId, ingestionId, source code where safe, stage and stable error code. URLs should be logged as sanitized host/path policy permits, never raw query strings.

## 15. Acceptance scenarios

1. Active source + valid service token + valid JSON -> 202, raw source archived, exact mapping version recorded, worker completes.
2. Same idempotency key and same request -> replay without duplicate business records/documents/messages.
3. Same idempotency key with changed body -> 409.
4. ServiceClient from tenant A cannot invoke tenant B source.
5. Suspended/archived source rejects ingestion.
6. Required configured source header missing -> deterministic 4xx before queueing.
7. Custom source header maps into IngestionContext but cannot override tenant/security fields.
8. Mapping failure produces row diagnostic and no accidental send.
9. Existing customer/invoice/payment idempotency remains intact.
10. Remote HTTPS public asset IMPORT -> FileService READY file with checksum/provenance.
11. localhost/private/link-local/cloud-metadata URL -> rejected without connection.
12. public URL redirecting to private address -> rejected.
13. oversized/unsupported remote content -> rejected and diagnosed.
14. REFERENCE URL is not downloaded.
15. Template uses persistent `asset.company_logo` independently of ingestion-scoped resources.
16. Object-store archival failure -> ingestion is not acknowledged as durable.
17. Worker crash after claim -> stale recovery can safely retry without duplicate side effects.
18. 100k-record input does not require one request thread to perform mapping/rendering and respects configured limits/backpressure.
19. partial record failures produce consistent counters and pageable diagnostics.
20. communication occurs only when an explicit processing/routing configuration enables it.

## 16. Implementation slices

| Slice | Scope | Exit condition |
|---|---|---|
| I1 | IntegrationSource + readiness + IngestionContext/header mapping | source can be configured/activated safely |
| I2 | unified source-oriented ingestion reservation + normalized orchestration boundary | JSON request becomes durable queued ingestion |
| I3 | raw FileService archival + provenance + unified diagnostics | every accepted ingestion is reproducible/auditable |
| I4 | RemoteResourceResolver + SSRF controls + REFERENCE/IMPORT | URL resources safely become internal files |
| I5 | outbox worker + claim/recovery/backpressure | request thread no longer performs large processing |
| I6 | business persistence/render/routing consumers | one normalized result drives configured downstream actions |
| I7 | guided frontend integration journey | user can configure/test/activate/observe source from one map |

Each slice requires Liquibase migrations where applicable, tenant-isolation tests, authorization tests, integration tests with PostgreSQL/Testcontainers, idempotency tests, failure/recovery tests, OpenAPI update and CI green.

## 17. Definition of Done

The reference golden path is executable end-to-end without supplying internal mapping/template UUIDs from the external system; no arbitrary remote URL is fetched by template/provider code; accepted requests are durable and auditable; large ingestion is asynchronous; diagnostics identify failed record/stage/code safely; tenant isolation is proven by tests; retries cannot duplicate durable business or communication effects; and the frontend can derive a deterministic activation checklist from backend readiness APIs.


## 18. Pre-implementation verification matrix: receive -> parse -> render -> mock delivery

Before I1 implementation starts, CI must prove the existing reusable foundation with an executable fixture matrix. This is a release gate, not documentation-only confidence.

### 18.1 Inbound format matrix

The same logical invoice dataset must be available as fixtures in all currently supported source formats and parsed through the real `DocumentInputParser`:

| Input | Required proof |
|---|---|
| JSON | multiple documents, nested item arrays, null/blank values, recipient/channel fields preserved |
| XML | repeated document/item nodes, ordering preserved, XXE/DOCTYPE rejected |
| CSV | multiple flat detail rows, delimiters/blank cells/order preserved |
| XLSX | first-sheet header mapping, numeric/string/null values and row order preserved |

The fixture contract must assert semantic equivalence where formats represent the same logical data. Existing parser/fixture tests are the baseline and must remain green.

### 18.2 End-to-end smoke fixture

Add a deterministic integration smoke that exercises the real boundaries, with infrastructure adapters replaced only at the final provider boundary when necessary:

```text
fixture JSON/CSV/XLSX/XML
 -> parser
 -> published mapping
 -> normalized document
 -> business persistence (when configured)
 -> template render
 -> generated output
 -> Message + required attachment
 -> attachment READY gate
 -> delivery request/outbox/listener
 -> MessageDeliveryWorker
 -> SimulatedDeliveryGateway
 -> SENT / RETRY_WAIT / FAILED
```

The test must inspect the actual `DeliveryCommand`/simulation boundary and prove destination, channel, subject/body and attachment bytes/filename/content-type. A test that only asserts a Message row was created is insufficient.

### 18.3 Channel matrix

Run the smoke for every `CommunicationChannel` supported by the domain. EMAIL must prove subject + body + attachment handling. Non-email channels must prove channel/destination/body and must not inherit email-only assumptions. If a channel does not support binary attachments at the provider contract, the routing policy must reject or transform that configuration explicitly rather than silently discard files.

The simulated provider remains provider-neutral and deterministic. CI should use explicit simulation rates/configuration so success/failure expectations do not depend on randomness.

### 18.4 File/attachment lifecycle matrix

Required scenarios:

1. generated PDF becomes READY and is read from object storage into `DeliveryAttachment`;
2. required PENDING attachment blocks delivery;
3. required FAILED generation prevents provider invocation;
4. optional failed/pending attachment does not block when policy permits;
5. missing object is a permanent failure;
6. transient object-storage read error follows retry policy;
7. file-count, per-file size and total-size limits are enforced before provider invocation;
8. unsafe filenames are rejected;
9. metadata/object size mismatch is rejected;
10. tenant A cannot resolve tenant B generated document/file.

After I4, extend the same matrix with remote URL `IMPORT` -> internal FileService object -> attachment -> mock delivery and `REFERENCE` -> no fetch.

### 18.5 Orchestration proof

I2/I6 are not complete until at least one test starts at the source-oriented ingestion API and reaches the simulated provider without direct test calls that bypass orchestration. It must prove:

```text
HTTP ingestion
 -> durable batch/raw source
 -> worker
 -> parse/map
 -> downstream action
 -> render/generate
 -> message
 -> attachment resolution
 -> simulated provider
 -> terminal/reportable state
```

For asynchronous execution the test may drive/wait for workers deterministically, but must not manually manufacture intermediate database state.

### 18.6 Evidence retained by CI

For each golden fixture the test/report should make these values observable in assertions or diagnostic output: ingestionId, source format, mapping version/hash, normalized document key, generated document ID/checksum/size, message ID/channel, attachment metadata, provider result ID or stable failure code, and final ingestion/message state.

Raw PII payloads and secrets must not be printed to CI logs.

### 18.7 Completion rule

A capability is marked **implemented end-to-end** only when both production code exists and the corresponding executable smoke path is green. Unit tests for parser, mapping, attachment resolver, worker, or simulated gateway independently do not qualify as end-to-end proof.
