# Import Batch Reliability — Technical Specification

Status: approved for implementation  
Target branch: `feature/import-batch-reliability`  
Base: `main` after PR #16

## 1. Scope

This increment hardens the existing synchronous import boundary without moving parsing to a worker.
It covers exactly four concerns:

1. deterministic idempotency under concurrent requests;
2. durable `FAILED` batches with structured failure details;
3. service-client access guarded by `document:generate` / `document:read` scopes;
4. publish-time validation of row-aware source schemas.

Asynchronous parsing, source-file persistence, retry orchestration and retention are explicitly deferred.

## 2. Import batch state and transaction model

### 2.1 State machine

```text
PROCESSING -> ACCEPTED
           -> FAILED
```

Terminal states are immutable. A replay never starts processing again; it returns the existing batch.

### 2.2 Durable reservation

`ImportBatchReservationService.reserve(...)` runs with `PROPAGATION_REQUIRES_NEW`.

- Normalize and validate `Idempotency-Key`.
- Calculate request SHA-256 before persistence.
- Look up `(tenant_id, idempotency_key)`.
- If found, compare `request_hash` and return `EXISTING`.
- Otherwise insert and `saveAndFlush` a `PROCESSING` batch.
- If the unique constraint wins in another transaction, catch `DataIntegrityViolationException`,
  load the winner and apply the same request-hash comparison.

The unique constraint remains the final concurrency authority; application locking is not introduced.

### 2.3 Processing and terminal updates

`ImportBatchService.create(...)` orchestrates without one outer database transaction:

1. reserve batch;
2. immediately return an existing batch for an identical replay;
3. execute mapping and create all document jobs in `ImportBatchProcessingService.process(...)`
   using one transaction;
4. mark `ACCEPTED` in that transaction;
5. on any runtime failure, update the reserved batch to `FAILED` in `REQUIRES_NEW`, then rethrow.

This preserves all-or-nothing job creation while retaining the batch record after rollback.

### 2.4 Structured error model

`import_batches` gains:

| Column | Type | Rule |
|---|---|---|
| `error_code` | `varchar(60)` | stable machine-readable code |
| `error_message` | `varchar(1000)` | safe client-facing message |
| `failed_at` | `timestamptz` | set only for `FAILED` |

Codes in this increment:

- `MAPPING_VALIDATION_FAILED`;
- `INVALID_IMPORT_INPUT`;
- `IMPORT_PROCESSING_FAILED`.

Messages are truncated to 1000 characters. Stack traces and secrets are never persisted or returned.
`BatchResult` exposes a nullable `Failure(code, message, failedAt)`.

## 3. API authorization

Human and service authorization are expressed by `ImportBatchAuthorization`:

- create: human with `DOCUMENT_GENERATE`, or service with `SCOPE_document:generate`;
- read: human with `DOCUMENT_READ`, or service with `SCOPE_document:read`.

Controller methods use:

```java
@PreAuthorize("@importBatchAuthorization.canCreate(authentication)")
@PreAuthorize("@importBatchAuthorization.canRead(authentication)")
```

Allowed service-client scopes gain `document:generate` and `document:read`. Existing integration scopes
remain supported. Tenant identity continues to come exclusively from the signed JWT claim and
`TenantContext`; request parameters cannot override it.

## 4. Source schema validation

`SourceSchemaManagementService.validate(...)` must not mutate status when errors exist.
It validates the following invariants:

- at least one source field;
- non-blank unique `sourcePath` values;
- exactly one or more document-key fields and every key has `DOCUMENT` scope;
- `ROW_CONTROL` and `IGNORE` fields cannot be document keys;
- configured `rowTypeFieldId` exists in the same version and has `ROW_CONTROL` scope;
- ITEM, TOTAL and IGNORE classifier sets are pairwise disjoint;
- classifier values require `rowTypeFieldId`;
- a schema with `ITEM` fields has either a classifier with non-empty ITEM values or documented
  implicit item detection;
- `recordPath` is forbidden for CSV/EXCEL;
- JSON `recordPath` starts with `$` or `/`;
- XML `recordPath` starts with `/`;
- at least one non-ignored field exists.

Validation returns stable `ValidationIssue.code` values and field-oriented paths. Publishing remains
possible only from `VALIDATED`, so no additional publish bypass is introduced.

## 5. Database migration

Liquibase changeset `015-import-batch-reliability.sql` adds failure columns and constraints:

- terminal failure fields described above;
- check: `FAILED` requires `error_code`, `error_message`, `failed_at`;
- check: non-`FAILED` rows have no failure fields.

The migration is backward compatible with existing `PROCESSING` and `ACCEPTED` rows.

## 6. Tests and acceptance criteria

### Integration

- two concurrent calls with the same tenant/key/body yield one batch and one set of jobs;
- same key with a different hash returns conflict;
- mapping failure leaves a queryable `FAILED` batch and creates no jobs/documents;
- successful import remains `ACCEPTED` and replayable;
- a service JWT with `document:generate` can create;
- a service JWT without the scope receives 403;
- a service JWT with `document:read` can read only its tenant batch.

### Unit/service tests

- every SourceSchema validation invariant has a positive or negative test;
- classifier-set overlap reports deterministic codes;
- invalid record paths are rejected by source format;
- failure-message truncation and error-code mapping are tested.

### Definition of done

- `mvn clean verify` passes;
- Liquibase applies from an empty PostgreSQL database;
- no endpoint accepts tenant ID from the request;
- no duplicate batch, generation job or outbox event is produced for an idempotent replay;
- existing PR #16 happy-path remains green.
