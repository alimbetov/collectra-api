# Collectra Backend MVP Roadmap

Updated: 2026-09-11

This document fixes the agreed implementation order for the Collectra backend after completion of the first Message persistence slice.

## Current baseline

Completed or already established:

- Campaign Core foundation.
- Message persistence and invariants.
- Liquibase migration for `messages` and CampaignRun delivery counters.
- `MessageStatus` and `CommunicationChannel`.
- `Message` entity constructor invariants.
- State transitions `QUEUED -> PROCESSING -> SENT / RETRY_WAIT / FAILED`.
- Tenant-scoped and paged `MessageRepository`.
- Application `Clock` used instead of entity-level `Instant.now()`.
- Unit, PostgreSQL integration and architecture coverage for the persistence slice.

The next implementation work must continue from this baseline rather than coupling the domain directly to a concrete delivery provider.

---

## Slice 2 — Message processing, claiming and retry semantics

**Priority: NEXT**

Implement the provider-independent processing layer.

### Scope

- Introduce `MessageProcessor` / application service responsible for processing queued messages.
- Atomically claim a message before delivery so that multiple workers cannot process the same message simultaneously.
- Enforce valid state transitions around `QUEUED`, `PROCESSING`, `SENT`, `RETRY_WAIT`, and `FAILED`.
- Track delivery attempts.
- Persist `nextAttemptAt` for retries.
- Define retry/backoff policy.
- Separate retryable and permanent failures.
- Add recovery for messages stuck in `PROCESSING` after worker failure.
- Keep all time calculations driven by application `Clock`.
- Add concurrency, retry, PostgreSQL integration and architecture tests.

### Definition of done

- No two workers can successfully claim the same message.
- A processing failure cannot silently lose a message.
- Retry scheduling is deterministic and testable.
- Terminal failures are persisted with a reason.
- `mvn verify` is green.

---

## Slice 3 — Email delivery adapter for KumoMTA

Implement email delivery behind an internal provider abstraction.

Target dependency direction:

```text
MessageProcessor
    -> EmailDeliveryService
        -> EmailProvider
            -> KumoMtaEmailProvider
```

### Scope

- Define an `EmailProvider` port independent of KumoMTA.
- Implement the KumoMTA adapter.
- Define provider request/response DTOs.
- Configure connection/read timeouts.
- Classify provider failures as retryable or permanent.
- Persist provider message identifier when available.
- Persist sanitized failure information suitable for operations.
- Add contract/integration tests around the adapter boundary.

### Architectural constraint

No KumoMTA-specific type should leak into the domain model or campaign orchestration code.

---

## Slice 4 — CampaignRun to Message materialization

Convert an eligible CampaignRun snapshot into durable messages.

### Scope

- Materialize one or more messages from the CampaignRun recipient snapshot.
- Respect enabled communication channels.
- Guarantee idempotency: repeating materialization must not create duplicate messages.
- Finalize `recipientCount`, `messageCount`, and queued/delivery counters.
- Process large runs in bounded pages/batches.
- Preserve tenant isolation.
- Add integration tests for duplicate execution and partial failure.

---

## Slice 5 — Template rendering

Build a stable rendering boundary between templates and delivery providers.

### Scope

- Resolve channel-specific template variants.
- Resolve canonical and custom placeholders.
- Validate required and unknown placeholders.
- Support locale selection and fallback (`ru`, `kk`, with future locales extensible).
- Escape HTML appropriately.
- Store a rendering snapshot so editing a template later does not mutate historical messages.
- Keep provider-specific formatting outside template domain logic.

---

## Slice 6 — Attachments, PDF and QR pipeline

### Scope

- Render invoice/receivable documents asynchronously.
- Generate PDF details where required.
- Generate QR codes from approved document URLs.
- Store generated artifacts in RustFS through FileService.
- Attach immutable file references to messages.
- Ensure a provider receives only prepared attachments and does not own document generation.

Target flow:

```text
Message preparation
    -> document rendering
    -> RustFS/FileService
    -> attachment metadata
    -> delivery
```

---

## Slice 7 — RabbitMQ and Outbox hardening

RabbitMQ is the preferred worker queue for the MVP. Database state remains authoritative.

### Scope

- Publish work through the transactional Outbox pattern.
- Avoid direct broker publication inside business transactions.
- Add publisher retry and observability.
- Make consumers idempotent.
- Define dead-letter handling where it adds operational value.
- Verify broker outage does not lose accepted work.

---

## Slice 8 — Campaign stabilization completion

If not already merged into `main`, finish the outstanding stabilization work before broadening the delivery surface.

### Scope

- Integration flow: `prepare -> payment/allocation -> eligibility recheck -> SKIPPED/PAID`.
- Replace remaining direct system-date usage with `Clock`.
- Remove N+1 access for email and segment data.
- Move invoice candidate selection into PostgreSQL.
- Process large selections with paging rather than a universal query DSL.
- Keep CI green before proceeding.

---

## Slice 9 — Import pipeline completion

Target flow:

```text
upload
  -> detect format
  -> parse CSV / Excel / XML / JSON
  -> map headers
  -> canonical/custom fields
  -> validate
  -> persist valid data
  -> produce row-level error report
```

### Scope

- Mapping metadata by project/source.
- Header aliases and normalization.
- Canonical/custom field conversion.
- Row-level validation errors.
- Partial/atomic import policy explicitly defined.
- Idempotency for repeated uploads.
- Large-file streaming/batching.
- Import statistics and audit metadata.

---

## Slice 10 — Delivery API and observability

### API

- List messages by CampaignRun.
- Filter by tenant, status, channel and recipient where permitted.
- Expose attempts, failure category and provider identifier to authorized operational users.
- Keep paging mandatory for message history.

### Metrics

At minimum:

- queued messages;
- processing messages;
- sent messages;
- failed messages;
- retry-wait messages;
- delivery latency;
- throughput;
- stuck processing count;
- provider error rate.

Add structured logging and correlation identifiers for CampaignRun -> Message -> provider delivery.

---

## File lifecycle hardening

Continue the existing FileService direction:

- PostgreSQL stores file metadata.
- RustFS stores object bytes.
- Expiration is represented by `expires_at`.
- Cleanup deletes the RustFS object and marks metadata `DELETED`.
- Tenant ownership is validated on every access path.
- Verify object size/hash where applicable.
- Initial operational target for generated files: approximately 90 days unless a product policy overrides it.

---

## Later channel expansion

Only after the provider-independent message pipeline is stable:

- SMS;
- WhatsApp;
- Telegram;
- In-App / push;
- OTP flows;
- Customer Care reminders and important-date campaigns.

Every new channel should implement the same delivery-port pattern rather than adding channel-specific branching throughout campaign orchestration.

---

## Implementation order

```text
DONE  Campaign Core
  |
DONE  Message persistence and invariants (Slice 1)
  |
NEXT  Message processing, claiming and retry semantics (Slice 2)
  |
      KumoMTA EmailProvider (Slice 3)
  |
      CampaignRun -> Message materialization (Slice 4)
  |
      Template rendering (Slice 5)
  |
      PDF / QR / attachments (Slice 6)
  |
      RabbitMQ / Outbox hardening (Slice 7)
  |
      Campaign stabilization completion (if still outstanding)
  |
      Import pipeline completion
  |
      Delivery API, observability and production hardening
```

## Working rule

Each slice should remain a narrow pull request with:

- one explicit responsibility;
- database migration where required;
- unit and PostgreSQL integration tests;
- architecture tests for important boundaries;
- `mvn verify` green before merge;
- no automatic merge unless explicitly requested.

The immediate next development PR is **Slice 2: Message processing, claiming and retry semantics**.
