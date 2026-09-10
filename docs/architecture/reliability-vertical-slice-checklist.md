# Reliability Vertical Slice — Implementation Checklist

This checklist operationalizes `reliability-vertical-slice-spec.md` into implementation units.

## Phase 1 — Outbox reliability

- [ ] Normalize `OutboxEventStatus`: `PENDING`, `PROCESSING`, `RETRY_WAIT`, `PUBLISHED`, `DEAD`.
- [ ] Add `attempt_count`, `next_attempt_at`, `locked_at`, `locked_by`, `published_at`, `last_error_code`, `last_error_message` where missing.
- [ ] Add polling index aligned to status/next-attempt/created-at query.
- [ ] Introduce explicit `OutboxEventHandler` registry.
- [ ] Fail closed on unknown event type.
- [ ] Implement atomic claim using PostgreSQL `FOR UPDATE SKIP LOCKED` or equivalent atomic `UPDATE ... RETURNING`.
- [ ] Enable Rabbit publisher confirms and returns.
- [ ] Mark `PUBLISHED` only after positive broker acknowledgement.
- [ ] Persist bounded retry schedule.
- [ ] Implement stale `PROCESSING` recovery.
- [ ] Persist terminal `DEAD` state.
- [ ] Add concurrent publisher test.
- [ ] Add nack/timeout/unknown-event tests.
- [ ] Add restart recovery test.

## Phase 2 — Communication module

- [ ] Add `Communication` aggregate.
- [ ] Add `CommunicationAttempt` entity.
- [ ] Add DB unique `(tenant_id, idempotency_key)`.
- [ ] Add unique `(communication_id, attempt_number)`.
- [ ] Add `@Version` to mutable communication state.
- [ ] Define `ChannelGateway` port.
- [ ] Define provider-neutral `OutboundMessage`.
- [ ] Define provider-neutral `DeliveryResult`.
- [ ] Implement deterministic mock gateway under local/test profile.
- [ ] Implement dispatcher state transitions.
- [ ] Make duplicate delivery after `SENT` a no-op.
- [ ] Protect concurrent dispatch from duplicate gateway invocation.
- [ ] Persist every attempt including transient/permanent failures.

## Phase 3 — Minimal business domain

- [ ] Add minimal tenant-owned `Customer`.
- [ ] Add minimal tenant-owned `Receivable`.
- [ ] Add amount/outstanding DB/domain validation.
- [ ] Add minimal `CollectionCase`.
- [ ] Prevent duplicate active case for same receivable.
- [ ] Add minimal `NextAction`.
- [ ] Add unique tenant-scoped NextAction idempotency key.
- [ ] Implement `PAYMENT_OVERDUE_NOTIFICATION` action type.
- [ ] Implement deterministic overdue eligibility rule.
- [ ] Keep generic rules engine out of scope.

## Phase 4 — E2E overdue -> SENT

- [ ] Add PostgreSQL Testcontainer.
- [ ] Add RabbitMQ Testcontainer.
- [ ] Use real Spring transactions.
- [ ] Use mock `ChannelGateway`; no external network.
- [ ] Create tenant and locale configuration.
- [ ] Prepare/publish `PAYMENT_OVERDUE` template.
- [ ] Import customer + overdue invoice/receivable.
- [ ] Create/reuse CollectionCase.
- [ ] Create exactly one NextAction.
- [ ] Create exactly one Communication.
- [ ] Resolve locale/template version.
- [ ] Render content.
- [ ] Persist Outbox in same business transaction.
- [ ] Publish through RabbitMQ with confirm.
- [ ] Dispatch through mock gateway.
- [ ] Assert one SUCCESS attempt.
- [ ] Assert `Communication.SENT`.
- [ ] Assert `NextAction.COMPLETED`.
- [ ] Add transient failure -> retry -> SENT E2E scenario.

## Phase 5 — Template concurrency

- [ ] Serialize template version allocation on stable parent lock or equivalent.
- [ ] Keep DB uniqueness on `(template_id, template_version)`.
- [ ] Add optimistic locking for mutable template/version state.
- [ ] Map stale update to HTTP 409.
- [ ] Add partial unique index for one `PUBLISHED` version per template/locale/channel.
- [ ] Archive old + publish new atomically.
- [ ] Add concurrent version creation test.
- [ ] Add concurrent publish test.
- [ ] Add publish rollback test.

## Phase 6 — Import + document idempotency

- [ ] Document import idempotency contract.
- [ ] Verify/add tenant-scoped import idempotency DB uniqueness.
- [ ] Add sequential duplicate import test.
- [ ] Add concurrent duplicate import test.
- [ ] Verify failed import keeps structured error.
- [ ] Verify retry cannot duplicate target domain rows.
- [ ] Define deterministic document-generation idempotency key.
- [ ] Add DB uniqueness for generation jobs.
- [ ] Make duplicate Rabbit delivery reuse existing generation job.
- [ ] Add restart-after-upload-before-status-update recovery test.
- [ ] Prevent duplicate object-storage output where possible.

## Phase 7 — Tenant isolation

- [ ] Inventory every tenant-owned aggregate and repository.
- [ ] Replace unsafe business-layer `findById(id)` calls with tenant-scoped lookup.
- [ ] Add ArchUnit/static regression tests.
- [ ] Add tenant A token + tenant B resource -> 404 integration tests.
- [ ] Confirm child entities are scoped through tenant-owned parent where direct tenant column is absent.

## Phase 8 — Reliability test suite

- [ ] Add deterministic barriers/latches to concurrency tests.
- [ ] Test outbox concurrent claim.
- [ ] Test communication concurrent dispatch.
- [ ] Test template concurrent version allocation.
- [ ] Test template concurrent publication.
- [ ] Test import concurrent idempotency.
- [ ] Test document-generation concurrent idempotency.
- [ ] Test NextAction concurrent creation.
- [ ] Test stale outbox processing recovery.
- [ ] Test communication left in `SENDING` recovery policy.
- [ ] Test import/generation intermediate-state recovery.
- [ ] Deliver same async event ID twice and assert one logical side effect.

## Phase 9 — Deferred provider adapters

Do not start until Phases 1-8 are green in CI.

- [ ] `EmailChannelGateway`.
- [ ] `SmsChannelGateway`.
- [ ] `WhatsAppChannelGateway`.
- [ ] `TelegramChannelGateway`.
- [ ] Provider callback/delivery receipt processing.

## Merge gate

Before merging implementation to `main`:

- [ ] Unit tests green.
- [ ] PostgreSQL integration tests green.
- [ ] RabbitMQ integration tests green.
- [ ] Happy-path E2E green.
- [ ] Retry-path E2E green.
- [ ] Duplicate-message tests green.
- [ ] Concurrency tests green.
- [ ] Tenant isolation tests green.
- [ ] Unknown outbox event test proves no silent `PUBLISHED` transition.
- [ ] CI requires no real external channel provider credentials.
