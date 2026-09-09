# FileService Technical Specification — Architecture Review

Review target: `docs/file-service-technical-specification.md`  
Decision: **APPROVED WITH IMPLEMENTATION GUARDRAILS**

## Findings incorporated before implementation

### 1. Cleanup concurrency — critical

A plain `LIMIT 500` cleanup query is unsafe once Collectra runs more than one replica: two schedulers can select the same rows. The implementation must use a claim phase backed by PostgreSQL row locking (`FOR UPDATE SKIP LOCKED`) or an equivalent atomic claim. Network deletion happens after the short claim transaction.

### 2. Database/network transaction boundary — critical

S3/RustFS upload/download/delete calls must not run inside long JPA transactions. Upload is modeled as `UPLOADING -> storage write -> READY`, with failure persisted as `FAILED`. Delete is modeled as `READY -> DELETE_PENDING -> storage delete -> DELETED`, with retry metadata on provider failure.

### 3. Existing document storage — critical

`document.application.DocumentStorage` and `document.infrastructure.MinioDocumentStorage` already own binary storage. Keeping them permanently would violate the single FileService boundary. They are therefore explicitly treated as legacy compatibility code and will be migrated only after the FileService foundation is stable.

### 4. Persistence compatibility — high

The project uses `AuditableEntity`, which adds `created_at`, `updated_at` and optimistic `version`. The original base specification listed only `created_at`; the actual `stored_file` migration must include the full persistence contract or Hibernate validation will fail.

### 5. Production bucket provisioning — high

Runtime auto-creation of buckets is acceptable for local/test setup but should not be a production responsibility. Production storage infrastructure must be provisioned separately and missing buckets must fail visibly rather than silently changing infrastructure.

### 6. Tenant isolation — critical

User-facing lookups must be by `(fileId, tenantId)`, not by `fileId` followed by an application-side tenant comparison. This reduces accidental cross-tenant leakage and makes repository contracts express the security invariant.

### 7. Retention semantics — high

`expires_at` is persisted at creation time. Cleanup does not derive retention from current configuration and `created_at`, because changing configuration later must not unpredictably rewrite the lifecycle of existing objects.

### 8. Provider-neutral boundary — high

S3/RustFS SDK types are confined to `file.infrastructure.storage`. Business and application contracts use Java value objects and streams. This is required for later RustFS -> Ceph/S3 replacement and for eventual FileService extraction.

### 9. Observability cardinality — medium

`tenantId` is allowed as structured log context but not as a Prometheus metric label. Tenant cardinality is unbounded and would create operational cost and instability.

### 10. Migration strategy — high

The work is intentionally incremental:

- 016: FileService foundation only;
- 017: generated-document file reference and migration;
- 018: import source file reference;
- legacy MinIO code removed only after compatibility and data migration tests pass.

This avoids a destructive big-bang migration and keeps `main` deployable at each integration step.

## Review result

The specification is accepted as the implementation baseline. The first implementation increment is restricted to storage foundation, domain lifecycle, repository, key/retention policy, storage port/adapter and application boundary. REST/security integration, concurrent cleanup claiming and migration of existing document/import flows follow as subsequent increments and must not weaken the invariants above.
