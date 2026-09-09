# Collectra FileService — Technical Specification

Status: **Implementation baseline**  
Branch: `feature/file-service`  
Target: Collectra modular monolith, extraction-ready boundary

## 1. Purpose and architectural decision

`FileService` is the single application boundary for binary objects in Collectra. PostgreSQL owns identity, metadata, lifecycle and audit; RustFS owns binary payloads. All business modules reference files only by `fileId` and must not persist or exchange bucket names, object keys, RustFS endpoints or storage credentials.

The first implementation remains inside `collectra-api`, but its public application contract is intentionally transport-neutral so that a later extraction to HTTP/gRPC does not change domain contracts in importing, document generation, reporting, export, template/assets or delivery.

### 1.1 Invariants

1. `fileId` is the only stable cross-module identifier.
2. `bucket` + `objectKey` are infrastructure coordinates and never part of business API/event contracts.
3. A `stored_file` row is created before/while an object is written and is never physically deleted as part of normal lifecycle.
4. `READY` means storage object is expected to exist and metadata is complete.
5. `DELETED` means physical deletion succeeded (or the object was already absent) and `deleted_at` is set.
6. Tenant isolation is mandatory for every user-facing read/write/delete/download operation.
7. File content is streamed; application code must not call `MultipartFile#getBytes()` for ordinary uploads.
8. Original filename is metadata only and never influences object-key uniqueness.
9. Retention is represented by persisted `expires_at`, not recalculated from `created_at` during cleanup.
10. RustFS-specific SDK types are restricted to `file.infrastructure.storage`.

## 2. Existing code impact

The repository already contains `document.application.DocumentStorage` and `document.infrastructure.MinioDocumentStorage`. This is duplicate storage ownership and must be retired after FileService becomes usable.

Migration sequence:

1. introduce FileService without breaking `document`;
2. adapt generated output persistence to store `fileId` (compatibility migration may temporarily keep `storage_key`);
3. replace `GeneratedOutputService -> DocumentStorage` with `GeneratedOutputService -> FileService`;
4. remove `DocumentStorage` and `MinioDocumentStorage` only after tests and data migration are complete;
5. integrate `ImportBatch.sourceFileId` as a separate migration after the storage foundation is stable.

No new module may add another S3/MinIO/RustFS client outside FileService.

## 3. Package boundary

```text
io.collectra.api.file
├── api
│   ├── FileController
│   ├── InternalFileController
│   └── dto
├── application
│   ├── FileService
│   ├── FileCleanupService
│   ├── FileKeyGenerator
│   ├── FileRetentionPolicy
│   └── command/query records
├── domain
│   ├── StoredFile
│   ├── FileCategory
│   ├── FileStatus
│   └── File exceptions
└── infrastructure
    ├── persistence
    │   └── StoredFileRepository
    ├── storage
    │   ├── ObjectStorage
    │   ├── RustFsObjectStorage
    │   ├── StorageConfiguration
    │   └── StorageObject records
    └── scheduler
        └── FileCleanupScheduler
```

Dependencies must point inward: API -> application -> domain. Infrastructure implements application/storage ports. `file.domain` must not depend on Spring MVC or AWS SDK.

## 4. Domain model

### 4.1 FileCategory

```java
public enum FileCategory {
    IMPORT_SOURCE,
    REPORT,
    EXPORT,
    ASSET,
    TEMP
}
```

### 4.2 FileStatus

```java
public enum FileStatus {
    UPLOADING,
    READY,
    DELETE_PENDING,
    DELETED,
    FAILED,
    QUARANTINED
}
```

Allowed state transitions for MVP:

```text
UPLOADING -> READY
UPLOADING -> FAILED
READY -> DELETE_PENDING
DELETE_PENDING -> DELETED
DELETE_PENDING -> DELETE_PENDING   // retry with counters
READY -> DELETED                   // only via service operation that physically deletes first
```

`QUARANTINED` is reserved. No transition into it is required for MVP.

### 4.3 StoredFile table

```sql
stored_file
-----------
id uuid primary key
tenant_id uuid not null
project_id uuid null
category varchar(40) not null
storage_provider varchar(30) not null
bucket varchar(128) not null
object_key varchar(1024) not null
original_filename varchar(512) not null
content_type varchar(255) null
size_bytes bigint null
checksum_sha256 char(64) null
status varchar(40) not null
created_at timestamptz not null
expires_at timestamptz null
deleted_at timestamptz null
created_by uuid null
delete_attempts int not null default 0
last_delete_attempt_at timestamptz null
last_error varchar(2000) null
version bigint not null default 0
```

Constraints/indexes:

```sql
unique (bucket, object_key)
check (size_bytes is null or size_bytes >= 0)
check (delete_attempts >= 0)
index (tenant_id, id)
index (status, expires_at)
index (tenant_id, project_id, category, created_at desc)
```

`@Version` must be used to prevent silent lost updates in lifecycle transitions.

### 4.4 Lifecycle methods on entity

State changes must be encapsulated; controller/repository code must not set status directly.

```java
void markReady(long size, String contentType, String checksum)
void markFailed(String error)
void markDeletePending()
void registerDeleteFailure(Instant attemptedAt, String error)
void markDeleted(Instant deletedAt)
```

Each method validates the current state and throws `IllegalFileStateException` for invalid transitions.

## 5. Storage abstraction

No AWS/MinIO classes leak through the port.

```java
public interface ObjectStorage {
    StoredObject upload(UploadObject command);
    InputStream download(StorageLocation location);
    ObjectMetadata stat(StorageLocation location);
    void delete(StorageLocation location);
    boolean exists(StorageLocation location);
    URI generatePresignedGetUrl(StorageLocation location, Duration ttl);
    URI generatePresignedPutUrl(StorageLocation location, String contentType, Duration ttl);
}
```

Recommended value objects:

```java
record StorageLocation(String bucket, String objectKey) {}
record UploadObject(StorageLocation location, InputStream content,
                    long contentLength, String contentType,
                    Map<String,String> metadata) {}
record StoredObject(long sizeBytes, String eTag) {}
record ObjectMetadata(long sizeBytes, String contentType, String eTag) {}
```

`checksum_sha256` is calculated in FileService while streaming into storage. Because a one-pass SHA-256 plus SDK upload requires a stream wrapper, use `DigestInputStream`; the caller must provide a known content length for MVP multipart upload. For unknown lengths, spool to bounded temp storage or use multipart upload in a later phase — do not buffer unbounded data in heap.

## 6. RustFsObjectStorage implementation

Use AWS SDK v2 S3 client because RustFS is S3-compatible. Configure:

- endpoint override;
- static credentials from environment;
- region (`us-east-1` default);
- path-style access enabled for local/RustFS compatibility;
- connect/read timeouts;
- `S3Presigner` with the same endpoint/credentials/region.

Buckets are configuration, not input supplied by clients. FileService resolves category -> bucket:

```text
IMPORT_SOURCE -> collectra-source
REPORT        -> collectra-generated
EXPORT        -> collectra-generated
ASSET         -> collectra-assets
TEMP          -> collectra-temp
```

Bucket auto-creation is allowed only in `local`/test environment. Production startup must fail health/readiness or operations must fail explicitly when required buckets are absent; production code must not silently create infrastructure.

## 7. Object key generation

`FileKeyGenerator` owns object keys.

MVP format:

```text
{category-prefix}/{tenantId}/{projectId-or-global}/{yyyy}/{MM}/{fileId}
```

Prefixes:

```text
IMPORT_SOURCE -> imports
REPORT -> reports
EXPORT -> exports
ASSET -> assets
TEMP -> temp
```

Use UTC for date partitioning. Do not include original filename. Validate that the generated key never starts with `/`, never contains `..`, and is deterministic for a given `(category, tenantId, projectId, fileId, createdAt)`.

## 8. Retention policy

`FileRetentionPolicy` maps category to `Duration`/no-expiry. Persist `expires_at` at file creation.

Defaults:

```text
IMPORT_SOURCE 90d
REPORT        90d
EXPORT        90d
TEMP          3d
ASSET         no expiry
```

Configuration:

```yaml
collectra:
  file:
    retention:
      import-source: 90d
      report: 90d
      export: 90d
      temp: 3d
```

Future tenant/project overrides belong behind the same port; do not spread retention rules across callers.

## 9. Application service contracts

### 9.1 Direct upload

```java
StoredFileView upload(UploadFileCommand command)
```

`UploadFileCommand`:

```text
tenantId           required; resolved from TenantContext for external API
projectId          optional
category           required
originalFilename   required, sanitized for metadata length/control chars
contentType        optional
contentLength      required, >= 0 and <= configured direct-upload limit
content            InputStream
createdBy          optional
```

Algorithm:

1. validate tenant/category/size and policy;
2. allocate `fileId` and `expires_at`;
3. choose bucket and key internally;
4. persist row `UPLOADING` in a short DB transaction;
5. stream object to RustFS outside a long database transaction while calculating SHA-256;
6. verify uploaded object size (and optionally stat);
7. in a new transaction lock/update row and transition `UPLOADING -> READY` with size/checksum/content type;
8. if storage write fails, in a new transaction transition to `FAILED`, persist a sanitized error, then propagate `FileStorageException`;
9. if storage succeeds but DB finalization fails, log/metric orphan risk and schedule reconciliation. The cleanup/reconciliation design must be idempotent.

Do **not** hold a JPA transaction open during network streaming.

### 9.2 Download

```java
FileDownload openContent(UUID tenantId, UUID fileId)
```

Rules:

- query by `id + tenantId`, never by `id` only for user-facing operations;
- allowed only in `READY`;
- `DELETE_PENDING`, `DELETED`, `FAILED`, `UPLOADING`, `QUARANTINED` are not downloadable;
- return stream + metadata; controller uses `StreamingResponseBody` or `InputStreamResource` without reading all bytes;
- set `Content-Disposition` safely; CR/LF must be rejected/removed from filename metadata.

### 9.3 Presigned download

```java
PresignedDownload generateDownloadUrl(UUID tenantId, UUID fileId)
```

Only `READY`; TTL configured and capped server-side. Never log the URL. The URL is capability-bearing and must not be persisted.

### 9.4 Delete

```java
void delete(UUID tenantId, UUID fileId)
```

Deletion is idempotent from API perspective:

- `DELETED`: return success;
- `READY`: mark `DELETE_PENDING` in DB, then call object storage delete;
- object missing is treated as successful physical deletion;
- success -> `DELETED`, `deleted_at=now`;
- failure -> remain `DELETE_PENDING`, increment retry fields;
- a concurrent delete must not resurrect status or duplicate business effects.

Explicit delete may ignore retention, but authorization must be stronger than read/download.

## 10. REST API

All endpoints require authentication and tenant resolution.

### POST `/api/files`

`multipart/form-data` fields:

```text
file              required
category          required
projectId         optional
```

The controller must not accept tenantId, bucket, objectKey or expiresAt from an ordinary tenant user. Tenant comes from `TenantContext`; expiration comes from policy.

Response `201`:

```json
{
  "fileId": "uuid",
  "category": "IMPORT_SOURCE",
  "originalFilename": "source.xlsx",
  "contentType": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  "sizeBytes": 12345,
  "checksumSha256": "...",
  "status": "READY",
  "createdAt": "...",
  "expiresAt": "..."
}
```

### GET `/api/files/{fileId}`
Metadata only. `200/404`; cross-tenant access must resolve as 404 where practical to reduce enumeration leakage.

### GET `/api/files/{fileId}/content`
Stream body, `200`; `404` unknown/other tenant; `409` non-downloadable lifecycle state.

### GET `/api/files/{fileId}/download-url`
Returns `{fileId,url,expiresInSeconds}`. TTL chosen by server configuration.

### DELETE `/api/files/{fileId}`
Idempotent `204` when deletion is complete or already deleted. Storage failure returns `503` while metadata remains retryable as `DELETE_PENDING`.

### POST `/internal/files/cleanup`
Administrative/recovery endpoint. Must invoke exactly the same `FileCleanupService` method as scheduler; never duplicate cleanup logic.

## 11. Authorization

Minimum permissions to seed later:

```text
file:read
file:upload
file:delete
file:admin
```

Until permission catalog migration is intentionally introduced, controller must at minimum require authenticated tenant context and reuse existing authorization conventions. Service-client endpoints must additionally enforce scope claims when introduced.

Every repository method backing a user operation must include `tenantId`:

```java
Optional<StoredFile> findByIdAndTenantId(UUID id, UUID tenantId);
```

Internal cleanup is allowed to work cross-tenant because it is system-owned; it must not accept tenant-controlled storage coordinates.

## 12. Cleanup concurrency and retry

The baseline specification's `LIMIT 500` is insufficient for multiple application replicas. Cleanup must be concurrency-safe.

Recommended PostgreSQL selection:

```sql
select id
from stored_file
where status in ('READY','DELETE_PENDING')
  and expires_at is not null
  and expires_at <= :now
  and delete_attempts < :maxAttempts
order by expires_at, id
for update skip locked
limit :batchSize;
```

Implementation should claim rows in a short transaction by changing `READY -> DELETE_PENDING`; storage calls happen after transaction release. Existing `DELETE_PENDING` rows are retry candidates. Retry pacing must prevent hammering a failed RustFS; add `last_delete_attempt_at <= now - retryDelay` condition.

Delete error text is sanitized/truncated to 2000 chars. Never persist credentials, signed URLs or authorization headers in `last_error`.

After `maxDeleteAttempts`, keep `DELETE_PENDING` for administrative diagnosis and emit a metric/alert; do not silently mark `FAILED` or `DELETED`.

## 13. Presigned upload (post-MVP contract reserved now)

Two-step protocol:

1. `POST /api/files/upload-sessions` creates `UPLOADING` row and returns presigned PUT URL.
2. Client uploads directly to RustFS.
3. `POST /api/files/{fileId}/complete` performs HEAD/stat, validates expected size/content type/checksum if supplied, then transitions to `READY`.

An abandoned `UPLOADING` record must have a separate upload-session timeout and reconciliation cleanup. It must not stay forever.

## 14. Import integration

Add nullable `source_file_id uuid` to `import_batches` first, then make it mandatory only when all producers use FileService.

New import request flow:

```text
HTTP upload -> FileService (IMPORT_SOURCE) -> fileId
          -> ImportBatch(sourceFileId)
          -> parser opens stream by fileId
```

For asynchronous processing/events publish:

```json
{"batchId":"...","fileId":"..."}
```

Never publish `bucket`/`objectKey`.

Import parser responsibilities stay unchanged: FileService does not parse Excel/CSV/JSON/XML.

## 15. Document generation integration

Current `DocumentStorage`/`MinioDocumentStorage` must become an adapter/migration target, not a permanent parallel subsystem.

Target:

```text
DocumentGenerationWorker
 -> GeneratedOutputService
 -> FileService.upload(category=REPORT or TEMP)
 -> generated_documents.file_id
```

HTML intermediate output should be `TEMP` unless it is a user-visible retained artifact. PDF final output is `REPORT` (or a later dedicated GENERATED_DOCUMENT category if domain semantics require it).

Generated document reading resolves `fileId` through FileService. `storage_key` must be removed from business-facing code after data migration.

## 16. Error model

Define typed exceptions:

```text
FileNotFoundException          -> 404
FileNotReadyException          -> 409
FileTooLargeException          -> 413
UnsupportedFileCategory...     -> 400
FileStorageException           -> 503
IllegalFileStateException      -> 409
```

Return existing project error envelope via `ApiExceptionHandler`; do not introduce a second global error format.

Storage/provider exception messages must be sanitized before returning to clients.

## 17. Configuration

```yaml
collectra:
  file:
    direct-upload-max-size: 50MB
    storage:
      provider: rustfs
      endpoint: ${RUSTFS_ENDPOINT:http://localhost:9000}
      access-key: ${RUSTFS_ACCESS_KEY:rustfsadmin}
      secret-key: ${RUSTFS_SECRET_KEY:rustfsadmin}
      region: ${RUSTFS_REGION:us-east-1}
      path-style-access: true
      buckets:
        source: collectra-source
        generated: collectra-generated
        assets: collectra-assets
        temp: collectra-temp
    retention:
      import-source: 90d
      report: 90d
      export: 90d
      temp: 3d
    cleanup:
      cron: "0 0 3 * * *"
      batch-size: 500
      max-delete-attempts: 10
      retry-delay: 15m
    presigned-url:
      download-ttl: 10m
      upload-ttl: 30m
```

Production credentials have no hard-coded defaults. Local development may override via `application-local.yml`/compose.

Spring multipart limits must be aligned with direct upload limits; otherwise controller rejects before FileService policy executes.

## 18. Observability

Structured log fields:

```text
fileId tenantId projectId category operation result durationMs
```

Never log object content, access/secret keys, Authorization headers or presigned URLs.

Micrometer metrics:

```text
collectra.files.uploaded{category}
collectra.files.downloaded{category}
collectra.files.deleted{category}
collectra.files.upload.errors{category,reason}
collectra.files.delete.errors{category,reason}
collectra.files.cleanup.processed{result}
collectra.files.cleanup.duration
collectra.files.expired.pending
```

Avoid an unbounded `tenantId` metric label.

## 19. Security hardening

1. Validate original filename length and remove control characters; do not build filesystem paths from it.
2. Do not trust client content type for authorization/safety decisions.
3. Presigned URLs are short-lived and not persisted/logged.
4. API never accepts bucket/key.
5. All external fetch/upload delivery endpoints are registered entities, not arbitrary URLs.
6. Future HTTP delivery must reject loopback, link-local, private/metadata networks after DNS resolution and protect against DNS rebinding/redirect bypass.
7. Bucket policies should deny public access; application/presigner credentials follow least privilege.
8. Checksum is integrity metadata, not malware detection.

## 20. Database migration plan

`016-file-service-foundation.sql`:

- create `stored_file`;
- constraints/indexes;
- no destructive changes to document tables.

`017-document-file-reference.sql` (separate follow-up):

- add `file_id` to generated documents;
- backfill/migrate existing object coordinates where possible;
- switch application reads/writes;
- remove old storage key only after verified migration.

`018-import-source-file-reference.sql` (separate follow-up):

- add `source_file_id` to import batches;
- update creation/API workflow;
- enforce FK only if operational lifecycle semantics permit it. Prefer logical reference without cascading delete because `stored_file` metadata must remain auditable.

Do not use `ON DELETE CASCADE` from business tables to `stored_file`.

## 21. Test strategy

### Unit tests

- state machine rejects invalid transitions;
- retention per category;
- object key deterministic and contains no filename;
- bucket resolver per category;
- filename sanitizer;
- cleanup retry/max attempts;
- checksum calculation while streaming.

### Repository/integration tests (PostgreSQL Testcontainers)

- Liquibase creates constraints/indexes;
- tenant-scoped lookup cannot cross tenant;
- optimistic version prevents lost transition;
- cleanup claim logic does not return same row to concurrent workers (`SKIP LOCKED` path);
- metadata remains after delete.

### Storage adapter tests

Use an S3-compatible test service where CI supports it, or a strict fake `ObjectStorage` for application tests plus a separately runnable RustFS/MinIO compatibility test.

Required application scenarios:

1. upload -> READY -> download content matches;
2. upload failure -> FAILED metadata survives;
3. explicit delete -> object gone + metadata DELETED;
4. delete provider failure -> DELETE_PENDING + attempts increment;
5. retry -> DELETED;
6. expired ASSET is never selected because it has no `expires_at`;
7. cross-tenant metadata/download/delete rejected;
8. presigned URL only for READY;
9. content is streamed, not accumulated in a `byte[]` by FileService.

### Architecture tests

Extend ArchUnit to forbid dependencies on `software.amazon.awssdk.services.s3` and `io.minio` outside `io.collectra.api.file.infrastructure.storage` (legacy document adapter temporarily excluded only while migration is open).

## 22. Delivery sequence / implementation increments

### Increment A — foundation (start now)

- detailed specification;
- AWS S3 dependency/configuration;
- `stored_file` migration;
- domain enums/entity/state machine;
- repository;
- `ObjectStorage` port;
- RustFS configuration skeleton;
- key generator + retention policy.

### Increment B — direct file operations

- streaming upload with SHA-256;
- metadata get/download;
- explicit delete;
- presigned GET;
- API DTO/controller;
- error mapping;
- metrics.

### Increment C — lifecycle reliability

- cleanup claim query with `SKIP LOCKED`;
- scheduler;
- retry delay/max attempts;
- internal cleanup API;
- concurrency tests.

### Increment D — integrations

- `ImportBatch.sourceFileId`;
- generated document `fileId` migration;
- switch `GeneratedOutputService` to FileService;
- remove legacy MinIO storage layer after compatibility migration.

### Increment E — large upload/delivery

- presigned PUT completion protocol;
- multipart upload;
- upload-session reconciliation;
- delivery endpoints/SSRF policy;
- antivirus/quarantine.

## 23. Definition of Done for MVP

MVP is complete only when:

- no new business module knows RustFS coordinates;
- stored metadata survives object deletion;
- direct upload/download/delete and presigned GET work through FileService;
- tenant isolation is tested;
- SHA-256 and persisted expiry are present;
- cleanup is batch-bounded, retryable and safe for multiple replicas;
- generated/import integration uses fileId or has an explicitly tracked compatibility migration;
- `mvn test` and formatting checks pass;
- secrets are absent from Git;
- operational configuration and failure semantics are documented.
