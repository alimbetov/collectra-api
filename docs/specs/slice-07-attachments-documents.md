# Slice 7 — Attachments and generated documents

Status: PLANNED  
Depends on: Slice 3, Slice 4, Slice 5, Slice 6, existing Document/FileService pipeline  
Suggested implementation branch: `feat/message-attachments`

## Entry gate — CJK PDF rendering must be fixed first

Slice 7 MUST NOT start until supported-locale PDF rendering is operational at glyph level, not merely able to return bytes with a `%PDF` header.

The current full-suite log contains `Couldn't load font (Noto Sans CJK SC)` for bundled CJK `.otf` resources. This is a separate prerequisite defect and MUST be fixed in a dedicated PR before attachment delivery is implemented.

Required prerequisite work:

1. reproduce and fix CJK font loading for the currently supported `zh-CN` locale;
2. keep bundled font/resource loading compatible with packaged application execution;
3. replace the current `%PDF`/size-only smoke assertion with glyph-level verification;
4. render representative CJK text and extract/assert the expected glyphs/text from the produced PDF;
5. fail the test if renderer/font failure produces a syntactically valid but semantically broken PDF;
6. run the full `mvn verify` suite.

If CJK cannot be fixed safely within the prerequisite PR, the locale MUST be explicitly de-scoped/disabled before Slice 7. A document MUST never become attachment `READY` while required glyphs are missing.

---

## 1. Goal

Connect the existing asynchronous document-generation + generated-output storage pipeline to EMAIL delivery so that:

- `Message` may own durable attachment requirements;
- required attachments are created as durable `PENDING` relations before generation starts;
- required attachments block delivery until they become `READY`;
- terminal attachment generation failure deterministically fails the owning `Message`;
- optional attachments never block delivery;
- the provider adapter receives bounded, validated immutable attachment content;
- document/storage modules remain the source of generated file metadata and bytes;
- duplicate broker delivery, duplicate completion events and concurrent callbacks do not create duplicate delivery requests.

Slice 7 does NOT rebuild the PDF engine, create a second storage subsystem, or move RustFS/MinIO access into the communication provider adapter.

---

## 2. Existing baseline

Already present in `main`:

- `Message` delivery state machine (`QUEUED`, `PROCESSING`, `RETRY_WAIT`, `SENT`, `FAILED`);
- `MessageStateService` and `MessageDeliveryWorker`;
- campaign message materialization;
- KumoMTA adapter without attachments;
- `GenerationJob` state machine;
- async generation request through Outbox/RabbitMQ;
- `DocumentGenerationWorker`;
- `GeneratedDocument` metadata containing format, storage key, media type, size and checksum;
- `GeneratedOutputService` + `DocumentStorage` for generated outputs;
- separate FileService/StoredFile lifecycle for uploaded ordinary files;
- RustFS/MinIO-compatible object storage;
- `Clock` usage in communication flow.

Known gaps relevant to this slice:

- `GenerationJob` and `GeneratedDocument` still contain internal `Instant.now()` in paths that Slice 7 may touch;
- existing generation state APIs are primarily `jobId`-scoped instead of `(tenantId, jobId)`-scoped;
- `DocumentGenerationWorker` currently emits no terminal completion/failure domain event;
- `Message` is materialized directly as `QUEUED`, so attachment readiness must be protected by an explicit delivery gate rather than by adding a new message status in this slice.

---

## 3. Core architectural invariants

The following invariants are normative and MUST hold after Slice 7.

### 3.1 Durable requirement before generation

A required attachment relation MUST exist in `PENDING` state before the corresponding document generation is dispatched.

Correct order inside the materialization transaction:

```text
create Message(QUEUED)
  -> create GenerationJob
  -> create MessageAttachment(PENDING, generationJobId, required=true)
  -> append DOCUMENT_GENERATION_REQUESTED
  -> commit
```

Completion MUST NOT create the attachment row. Completion only resolves an already existing durable requirement:

```text
PENDING
  -> bind generatedDocumentId
  -> READY
```

This prevents the system from losing the fact that a Message is waiting for a document while generation is in flight.

### 3.2 Defense-in-depth delivery readiness

Avoiding early `MESSAGE_DELIVERY_REQUESTED` creation is necessary but NOT sufficient.

`MessageStateService.begin(tenantId, messageId)` MUST be the final delivery guard and MUST NOT transition `QUEUED -> PROCESSING` when any required attachment is not `READY`.

Normative invariant:

```text
No execution path may transition Message QUEUED -> PROCESSING
while EXISTS required MessageAttachment with status <> READY.
```

This protects against duplicate/stale broker messages, historical outbox entries, manual requeue bugs and future alternative producers.

### 3.3 Required attachment set becomes immutable

All required attachment requirements for a message MUST be created during the message materialization transaction before delivery eligibility can be published.

After a message has become delivery-eligible / `delivery_requested_at` is set, creation of a new `required=true` attachment MUST be rejected.

A required attachment MUST NOT be added after provider delivery has become possible.

### 3.4 Terminal required failure fails the Message

A required attachment that reaches terminal `FAILED` MUST NOT leave the owning Message in `QUEUED` forever.

After the document retry policy is exhausted:

```text
required MessageAttachment -> FAILED
Message -> FAILED
CampaignRun counters -> updated using existing Slice 6 contracts
```

Recommended communication error code:

```text
ATTACHMENT_GENERATION_FAILED
```

The transition MUST use the same message/campaign transactional rules and lock ordering used by the delivery state machine.

### 3.5 Optional attachment is non-blocking

For `required=false`:

```text
PENDING -> does not block delivery
READY   -> include only if available before provider command is resolved
FAILED  -> does not block delivery
```

Optional attachment state MUST NOT prevent `QUEUED -> PROCESSING` and MUST NOT fail the owning Message.

### 3.6 No new WAITING_ATTACHMENTS MessageStatus in Slice 7

Slice 7 MUST keep Message delivery state and attachment readiness as separate dimensions.

`MessageStatus.QUEUED` means the message has not yet been claimed by a provider worker; it does not by itself imply dispatchability.

Dispatchability is:

```text
message.status == QUEUED
AND
NOT EXISTS (
    required attachment
    WHERE status <> READY
)
```

Do not add `WAITING_ATTACHMENTS` in this slice.

---

## 4. Persistence model

Add:

```text
communication.domain.MessageAttachment
communication.infrastructure.MessageAttachmentRepository
communication.application.MessageAttachmentService
```

Current `main` already contains migration `027-campaign-message-materialization.sql`.

Therefore the Slice 7 migration MUST be:

```text
028-message-attachments.sql
```

If another migration reaches `main` before implementation starts, rebase and use the next free migration number.

### 4.1 `message_attachments`

Required schema:

```text
message_attachments
- id UUID PK
- tenant_id UUID NOT NULL
- message_id UUID NOT NULL
- generation_job_id UUID NOT NULL
- generated_document_id UUID NULL
- output_format varchar(20) NOT NULL
- filename varchar(255) NOT NULL
- content_type varchar(150) NOT NULL
- required boolean NOT NULL DEFAULT true
- status varchar(20) NOT NULL          # PENDING, READY, FAILED
- failure_code varchar(80) NULL
- failure_message varchar(1000) NULL
- created_at timestamptz NOT NULL
- ready_at timestamptz NULL
- failed_at timestamptz NULL
```

Do NOT store raw binary or arbitrary storage URLs in `message_attachments`.

`generation_job_id` is the durable correlation while the requirement is unresolved.

`generated_document_id` is populated only after the exact requested output has been durably generated.

`GeneratedDocument` remains the source of storage key, media type, size and checksum. Do not duplicate those fields in the attachment relation.

### 4.2 Output format

Slice 7 generated email attachments are PDF by default.

The attachment requirement MUST identify the expected format explicitly:

```text
output_format = PDF
```

Completion MUST resolve the exact generated output by:

```text
(tenantId, generationJobId, OutputFormat.PDF)
```

Never select an arbitrary `GeneratedDocument` for a job because one job may contain multiple output formats such as HTML and PDF.

The schema keeps `output_format` so later XLSX/CSV generated attachments can be added without changing correlation semantics.

---

## 5. Database constraints

Minimum constraints:

```text
FK message_id -> messages(id)
FK generation_job_id -> generation_jobs(id)
FK generated_document_id -> generated_documents(id)

UNIQUE(message_id, generation_job_id, output_format)
UNIQUE(message_id, generated_document_id)
  WHERE generated_document_id IS NOT NULL

CHECK(status IN ('PENDING', 'READY', 'FAILED'))
```

State consistency MUST be enforced by DB checks equivalent to:

```text
PENDING:
  generated_document_id IS NULL
  ready_at IS NULL
  failed_at IS NULL

READY:
  generated_document_id IS NOT NULL
  ready_at IS NOT NULL
  failed_at IS NULL
  failure_code IS NULL
  failure_message IS NULL

FAILED:
  generated_document_id IS NULL
  ready_at IS NULL
  failed_at IS NOT NULL
```

Tenant consistency MUST be enforced in application lookups and, where practical, by composite uniqueness/FK design. No Slice 7 lookup may trust an ID without tenant scope when crossing aggregate/module boundaries.

Recommended query index:

```text
index message_attachments(tenant_id, message_id)
```

Add further indexes only for demonstrated query paths.

---

## 6. Attachment domain state model

`MessageAttachment` represents relation readiness, not the full document-generation lifecycle.

Allowed transitions:

```text
PENDING -> READY
PENDING -> FAILED
```

No transition out of `READY` or `FAILED` in Slice 7.

Intent methods SHOULD be explicit, for example:

```text
markReady(generatedDocumentId, Instant now)
markFailed(errorCode, errorMessage, Instant now)
```

Illegal transitions are rejected.

### Readiness semantics

For required attachments:

```text
PENDING -> delivery BLOCKED
READY   -> delivery allowed
FAILED  -> owning Message terminal FAILED
```

For optional attachments:

```text
PENDING -> non-blocking
READY   -> attach if present when delivery snapshot is resolved
FAILED  -> non-blocking
```

---

## 7. Materialization integration

`CampaignMessageMaterializer` MUST determine the complete required attachment set for each Message before committing the materialized Message.

### No required attachment

```text
create Message
  -> set durable delivery-request marker
  -> append MESSAGE_DELIVERY_REQUESTED
```

### Required generated attachment

```text
create Message(QUEUED)
  -> create GenerationJob
  -> create MessageAttachment(
         status=PENDING,
         required=true,
         generationJobId=job.id,
         outputFormat=PDF
     )
  -> append DOCUMENT_GENERATION_REQUESTED
  -> do NOT append MESSAGE_DELIVERY_REQUESTED
```

Message creation, attachment requirement creation, generation request creation and any delivery eligibility marker changes that belong to materialization MUST be committed atomically in PostgreSQL.

The generation outbox event may execute only after the durable attachment requirement exists.

---

## 8. Document generation terminal events

The existing generation worker does not currently publish terminal events. Slice 7 MUST add explicit terminal outcome events through the existing Outbox.

Required events:

```text
DOCUMENT_GENERATION_COMPLETED
DOCUMENT_GENERATION_FAILED
```

Recommended payloads:

```json
{
  "tenantId": "...",
  "jobId": "..."
}
```

and:

```json
{
  "tenantId": "...",
  "jobId": "...",
  "errorCode": "..."
}
```

The document module MUST NOT directly import communication application services.

The terminal event is the module boundary. Communication owns the MessageAttachment reaction.

### 8.1 Success ordering

Successful generation MUST have durable metadata before completion is emitted:

```text
store generated object
  -> persist GeneratedDocument metadata
  -> GenerationJob COMPLETED
  -> append DOCUMENT_GENERATION_COMPLETED
  -> commit DB transaction containing state/event changes
```

Because object storage and PostgreSQL are not one ACID resource, attachment `READY` MUST require durable `GeneratedDocument` metadata. Actual object readability is verified again before provider invocation.

### 8.2 Failure ordering

Only terminal document failure emits:

```text
DOCUMENT_GENERATION_FAILED
```

A retryable generation failure that returns the job to a retryable/pending state MUST NOT mark MessageAttachment `FAILED`.

When generation retries are exhausted:

```text
GenerationJob FAILED
  -> DOCUMENT_GENERATION_FAILED
  -> required attachment FAILED
  -> Message FAILED
```

---

## 9. Tenant-scoped callback APIs

All new Slice 7 generation-completion/failure paths MUST be tenant-scoped.

Do not introduce new callback usage based only on:

```text
begin(jobId)
complete(jobId)
findById(jobId)
```

New/changed paths MUST use contracts equivalent to:

```text
begin(tenantId, jobId)
complete(tenantId, jobId)
fail(tenantId, jobId, ...)
findByIdAndTenantId(jobId, tenantId)
findGeneratedDocument(tenantId, jobId, format)
```

If existing legacy APIs remain temporarily for unrelated callers, Slice 7 MUST NOT use them for cross-module attachment orchestration.

When touching `GenerationJob` / `GeneratedDocument` transition paths, replace internal `Instant.now()` with timestamps supplied from application-level `Clock`.

---

## 10. Completion orchestration

### 10.1 Success callback

On `DOCUMENT_GENERATION_COMPLETED {tenantId, jobId}`:

```text
resolve affected MessageAttachment requirement(s)
  -> lock owning Message using canonical lock order
  -> find tenant-scoped PENDING attachment by generationJobId + outputFormat
  -> resolve exact tenant-scoped GeneratedDocument
  -> mark attachment READY
  -> evaluate all required attachments
  -> if all required READY and delivery not yet requested:
         set durable delivery-request marker
         append MESSAGE_DELIVERY_REQUESTED
  -> commit
```

Completion MUST update the existing attachment row; it MUST NOT create a new one.

Duplicate completion delivery MUST become an idempotent no-op after the attachment is already `READY`.

### 10.2 Failure callback

On terminal `DOCUMENT_GENERATION_FAILED {tenantId, jobId}`:

```text
lock owning Message
  -> resolve PENDING attachment(s)
  -> mark FAILED
  -> if attachment.required:
         fail Message with ATTACHMENT_GENERATION_FAILED
         update CampaignRun counters using existing state-service contract
  -> if attachment.optional:
         do not block/fail Message
         if Message is otherwise eligible and delivery not requested:
             request delivery
  -> commit
```

Duplicate failure events MUST be idempotent.

A stale failure event MUST NOT downgrade a `READY` attachment.

---

## 11. Canonical lock ordering

Slice 7 MUST preserve one lock order across delivery, completion and failure flows.

Canonical order when locks are needed:

```text
Message
  -> MessageAttachment
  -> CampaignRun
```

Rules:

- never lock `MessageAttachment` first and then lock `Message` in another transaction path;
- `MessageStateService.begin()` locks Message before checking delivery readiness;
- completion/failure callbacks lock Message before mutating attachment state;
- when a Message terminal transition requires CampaignRun counter updates, lock CampaignRun only after Message/attachment work consistent with the existing state-service contract;
- prefer tenant-scoped existence/readiness queries over unnecessary row locks when a lock is not required for correctness.

Any implementation that introduces reverse ordering MUST be rejected during review because concurrent delivery/completion/recovery can otherwise deadlock.

---

## 12. DB-level exactly-one delivery request

Application `exists()` checks are insufficient under concurrent completion callbacks.

Slice 7 MUST introduce a durable DB-level marker on `Message`, preferably:

```text
delivery_requested_at timestamptz NULL
```

Semantics:

```text
NULL     -> delivery has not yet been published
NOT NULL -> eligibility already published
```

The transition is monotonic.

The same PostgreSQL transaction that changes:

```text
delivery_requested_at: NULL -> now
```

MUST append exactly one logical `MESSAGE_DELIVERY_REQUESTED` outbox event.

For a Message with multiple required attachments, only the callback that observes the final required attachment becoming `READY` may perform this transition.

If the project instead implements an Outbox logical idempotency key, it MUST provide an equivalent DB unique guarantee for `MESSAGE_DELIVERY_REQUESTED + messageId`. A non-atomic `exists()`/insert pattern is forbidden.

---

## 13. Final delivery readiness gate in MessageStateService

`MessageStateService.begin(tenantId, messageId)` MUST perform the final readiness check while the Message is locked.

Required logic:

```text
lock Message by tenantId + messageId
  -> if status != QUEUED: idempotent no-op
  -> if EXISTS required attachment status <> READY: do not begin attempt
  -> otherwise QUEUED -> PROCESSING
```

A stale or duplicate `MESSAGE_DELIVERY_REQUESTED` event MUST NOT bypass attachment readiness.

The service should return an empty/not-claimed result rather than convert a still-waiting attachment into a provider failure.

A required terminal `FAILED` relation should normally already have failed the Message via the failure callback; the begin guard remains defense in depth.

---

## 14. Delivery command and content resolution

Extend the provider-neutral delivery command with immutable attachment descriptors.

Example:

```java
public record DeliveryAttachment(
        String filename,
        String contentType,
        byte[] content) {}
```

A small `AttachmentContent` abstraction is acceptable if it materially reduces memory retention, but Slice 7 MUST NOT introduce a generic streaming framework without a demonstrated requirement.

The KumoMTA adapter MUST receive already resolved provider-neutral attachment content. It MUST NOT access RustFS/MinIO/FileService directly.

Correct dependency direction:

```text
Message attachment application/resolver
  -> GeneratedOutputService / DocumentStorage
  -> immutable DeliveryAttachment
  -> DeliveryCommand
  -> KumoMtaEmailDeliveryGateway
```

For future uploaded attachments, FileService is the source boundary. Generated and uploaded file sources MUST NOT be inferred from arbitrary URLs.

---

## 15. Size/count limits and validation order

Configuration is mandatory:

```yaml
collectra:
  communication:
    attachments:
      max-file-size: 10MB
      max-total-size: 20MB
      max-count: 10
```

Exact defaults may be adjusted to deployment/provider policy.

Validation MUST happen before provider invocation.

For generated documents, validate metadata BEFORE downloading object bytes:

```text
1. attachment count
2. GeneratedDocument.sizeBytes per file
3. aggregate GeneratedDocument.sizeBytes
4. expected output format
5. media type present/allowed
6. filename validation/sanitization
7. only then storage read
8. object existence/read result
```

Do not download a known 50MB object only to discover that `max-file-size` is 10MB.

Classification:

```text
ATTACHMENT_TOO_LARGE / TOO_MANY_ATTACHMENTS
  -> permanent configuration/business delivery failure
  -> no infinite retry

ATTACHMENT_OBJECT_MISSING
  -> permanent storage/data integrity failure
  -> do not send incomplete email

Temporary storage read failure
  -> retryable technical delivery failure
```

---

## 16. KumoMTA mapping

Use KumoMTA `POST /api/inject/v1` structured attachment contract.

For each attachment map the provider-required representation of:

```text
filename
content type
content/base64 or equivalent according to the actual installed KumoMTA schema
```

Before implementation, verify the exact `Attachment` wire schema against the KumoMTA version that will be deployed. Do not hard-code assumptions from a different release.

Template substitution MUST NOT be used for attachment binary content.

---

## 17. File/storage consistency and retention

### 17.1 Readiness definition

Attachment `READY` means:

```text
GenerationJob terminal COMPLETED
AND
exact GeneratedDocument metadata exists for required output format
```

It does NOT imply that RustFS and PostgreSQL participated in one distributed ACID transaction.

Immediately before provider invocation, the attachment resolver MUST read the object through the existing storage abstraction. If metadata exists but the object is missing, fail deterministically and do not send the email.

### 17.2 Retention

Cleanup MUST NOT delete a generated object while it is referenced by a non-terminal Message that may still be delivered/retried.

Required retention guard:

```text
Message terminal (SENT or FAILED)
AND
retention policy elapsed
```

or an equivalent existing policy that provably keeps the generated output alive for the complete delivery/retry lifecycle.

Current generated outputs use `GeneratedDocument`/`DocumentStorage`, not `StoredFile`. Slice 7 MUST NOT perform a half-migration between the two models.

If retention integration requires changes, modify the actual generated-output cleanup path or add a reference/use check there. Do not add a second cleanup engine.

---

## 18. Security

Mandatory:

- every attachment/message/job/document lookup is tenant-scoped;
- attachment tenant == Message tenant;
- GenerationJob tenant == attachment tenant;
- GeneratedDocument tenant/job relationship is validated;
- filenames are sanitized before Content-Disposition/provider mapping;
- reject path traversal/path separators where inappropriate;
- never expose local filesystem paths or storage keys to provider/user-facing output unless explicitly part of an internal contract;
- content type comes from validated metadata/source, not only a filename extension;
- storage credentials remain inside infrastructure;
- no arbitrary remote URL fetching;
- no provider adapter direct storage credentials.

---

## 19. Idempotency and concurrency

Required cases:

### Duplicate generation request

Must not generate duplicate durable output for the same business job without an explicit regeneration reason.

### Duplicate completion event

Must not:

- create another MessageAttachment;
- replace a terminal READY relation;
- emit another logical delivery request.

### Duplicate failure event

Must not:

- fail Message twice;
- double-count CampaignRun failure counters;
- overwrite READY with FAILED.

### Concurrent completion of multiple attachments

If two required attachments become READY concurrently, DB serialization + durable `delivery_requested_at` (or equivalent unique key) MUST guarantee one logical delivery signal.

### Stale delivery broker event

`MessageStateService.begin()` readiness gate prevents provider execution while required attachment is not READY.

---

## 20. Failure scenarios

### Generation retryable failure

```text
GenerationJob remains/re-enters retryable state
MessageAttachment remains PENDING
Message delivery remains blocked only if required=true
```

### Generation terminal failure, required attachment

```text
GenerationJob FAILED
MessageAttachment FAILED
Message FAILED
CampaignRun failure counters updated
```

### Generation terminal failure, optional attachment

```text
MessageAttachment FAILED
Message remains eligible based only on required attachments
```

### GeneratedDocument metadata missing on COMPLETED event

Treat as deterministic orchestration/data integrity failure. Do not mark attachment READY and do not publish delivery.

Recommended code:

```text
GENERATED_DOCUMENT_NOT_FOUND
```

### Object missing at provider-read time

```text
ATTACHMENT_OBJECT_MISSING
```

Do not send incomplete email.

### Temporary object-storage read failure

Classify as retryable technical delivery failure if metadata states the object should exist.

### Oversized/count violation

Permanent failure with explicit error code. Do not retry indefinitely.

---

## 21. Tests

### 21.1 CJK prerequisite tests

Before Slice 7 implementation:

- bundled CJK font loads without renderer failure;
- render representative Chinese text;
- extract text/glyph content from resulting PDF;
- assert expected CJK text/glyphs are present;
- `%PDF` header and file-size-only assertions are insufficient;
- full `mvn verify` green.

### 21.2 Domain tests

- `PENDING -> READY` valid;
- `PENDING -> FAILED` valid;
- terminal attachment cannot transition again;
- READY requires generated document id;
- FAILED records failure metadata/timestamp;
- optional attachment semantics are non-blocking.

### 21.3 Materialization integration

- message with no required attachment gets one delivery request;
- required attachment row is created as PENDING before generation request becomes publishable;
- no delivery request exists while required attachment is PENDING;
- adding required attachment after delivery eligibility is rejected;
- cross-tenant attachment/job linkage rejected.

### 21.4 PostgreSQL completion/idempotency integration

- document completion updates existing PENDING row rather than creating one;
- completion resolves exact PDF output;
- duplicate completion is no-op;
- duplicate failure is no-op;
- stale failure cannot downgrade READY;
- final required attachment READY emits exactly one logical delivery event;
- two concurrent final completions still emit exactly one delivery event;
- transaction rollback leaves no attachment/outbox/marker divergence.

### 21.5 Delivery gate integration

- stale delivery event while required PENDING -> `begin()` does not claim Message;
- required READY -> `begin()` may claim Message;
- required FAILED -> Message already terminal FAILED and cannot be claimed;
- optional PENDING/FAILED does not block `begin()`.

### 21.6 Failure/counter integration

- terminal required generation failure fails Message exactly once;
- CampaignRun failure counters updated exactly once;
- optional generation failure does not increment message-failed counters unless delivery later fails for another reason.

### 21.7 Storage integration

- metadata size limit is checked before storage read;
- total size/count limit checked before storage read;
- object read success;
- object missing classified as permanent data/storage failure;
- temporary storage error classified retryable.

### 21.8 Kumo adapter

- attachment mapped to inject request;
- filename/content type preserved;
- multiple attachments supported within configured limits;
- subject/body unchanged;
- adapter has no direct RustFS/FileService dependency.

### 21.9 Retention

- referenced non-terminal generated attachment is not deleted;
- terminal + expired output follows retention policy.

---

## 22. Implementation order

### PR 0 — prerequisite

`fix/pdf-cjk-font-loading`

1. reproduce CJK renderer problem;
2. fix renderer/font compatibility;
3. add glyph-level PDF smoke test;
4. full `mvn verify`.

Do not merge Slice 7 implementation before this prerequisite is green.

### Slice 7 implementation

1. rebase from current `main` and confirm migration number;
2. add `028-message-attachments.sql` (or next free after rebase);
3. add `delivery_requested_at` DB marker to Message;
4. add `MessageAttachment` entity/status/repository/intent methods;
5. add tenant-scoped document/generation lookup APIs used by this flow;
6. move touched document timestamps to application `Clock`;
7. materialization creates GenerationJob + PENDING MessageAttachment before generation outbox dispatch;
8. add terminal document COMPLETED/FAILED outbox events;
9. implement completion/failure orchestration with canonical lock order;
10. implement exactly-one delivery marker/event transition;
11. add final readiness guard to `MessageStateService.begin()`;
12. add attachment resolver and metadata-first limits validation;
13. extend `DeliveryCommand` / provider-neutral attachment descriptor;
14. extend KumoMTA inject mapping after verifying actual deployed API schema;
15. integrate generated-output retention guard;
16. add concurrency/idempotency/PostgreSQL/storage/provider tests;
17. run full `mvn verify`.

---

## 23. Out of scope

- new generic PDF engine;
- adding `WAITING_ATTACHMENTS` to MessageStatus;
- direct RustFS/MinIO use from Kumo adapter;
- full migration of GeneratedDocument into StoredFile;
- inline CID/image rendering unless explicitly required by an existing template use case;
- public download API redesign;
- antivirus/DLP subsystem;
- arbitrary remote URL attachment fetching;
- generic streaming framework without measured need;
- regeneration/versioning UI.

---

## 24. Definition of Done

Slice 7 is complete only when all of the following are true:

- CJK prerequisite PR is merged or the affected locale was explicitly disabled before Slice 7;
- enabled attachment locales pass glyph-level PDF verification;
- `MessageAttachment` is created as durable `PENDING` before generation dispatch;
- completion updates the existing attachment to READY and binds the exact generated PDF;
- terminal generation failure updates attachment to FAILED;
- required FAILED deterministically fails Message and updates CampaignRun counters exactly once;
- optional PENDING/FAILED is explicitly non-blocking;
- required attachment creation is forbidden after delivery eligibility;
- `MessageStateService.begin()` independently prevents delivery while required attachments are not READY;
- all Slice 7 document callbacks and lookups are tenant-scoped;
- touched generation/document timestamps are supplied through `Clock`;
- exact-one logical delivery request is guaranteed at DB level by `delivery_requested_at` or an equivalent unique outbox key;
- concurrent/duplicate completion/failure events are idempotent;
- canonical lock order is preserved;
- size/count limits are validated from metadata before object download;
- missing generated metadata/object has deterministic failure semantics;
- provider receives validated immutable attachment content within configured limits;
- Kumo adapter has no direct storage dependency;
- generated-output retention cannot delete a required attachment while Message is still deliverable/retryable;
- migration is the correct next free migration from current `main`;
- full `mvn verify` is green.
