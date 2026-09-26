# WP-by-WP Architecture Review and High-Value Improvements

Status: NORMATIVE REVIEW ADDENDUM
Scope: WP-01..WP-12
Principle: only improvements with high expected value are mandatory here. Avoid speculative framework/refactor work.

## Review summary

The 12-WP decomposition is sound. The main residual risks are not missing domain names but ambiguous ownership at boundaries: permission dependency, cross-tenant reference validation, authoritative financial state, async durable state, broker evidence, frontend cache invalidation, and composite-gate diagnostics.

Each WP below records Review -> High-value improvement -> Practical implementation -> Proof.

## WP-01 Business Core RBAC

Review: ACCEPT WITH IMPROVEMENT.

Risk: adding controller annotations alone can leave selectors/options/deep-linked child APIs as capability bypasses. Also MANAGE-only roles can become unusable if UI/backend assumes READ separately.

High-value improvement:
1. Build an endpoint-permission ledger before annotations.
2. Define permission implication policy explicitly: role composition normally grants READ + MANAGE to operators; do not implement hidden MANAGE=>READ magic unless already canonical.
3. Include dependent option/select endpoints (manager/segment/customer lookup) in least-privilege review.
4. Verify permission changes invalidate effective authorization according to current auth-version/session design.

Practical target:
```text
Customer READ: list/detail/contacts/segment membership visibility
Customer MANAGE: create/update/status/contact/segment mutations
Contract READ: list/detail
Contract MANAGE: create/update/lifecycle
Receivable READ: invoice/payment/allocation reads
Receivable MANAGE: invoice/payment/allocation/reversal mutations
Collection READ: queue/detail/history
Collection MANAGE: case/lifecycle/promise/dispute/action mutations
```

Proof: matrix must include READ-only, READ+MANAGE, unrelated permission, USER_READ-only, service principal and foreign tenant.

## WP-02 Collections Workspace

Review: ACCEPT WITH IMPROVEMENT.

Risk: frontend can become a second state machine, infer allowed transitions, or reconstruct timeline.

High-value improvement:
1. Treat backend state as authoritative; frontend only exposes commands allowed by documented state matrix.
2. Centralize frontend command-availability mapping in one pure function with unit tests.
3. Keep timeline from timeline endpoint only.
4. Add route URL state for queue filters/page/sort so deep links are reproducible.
5. Define post-mutation invalidation table instead of ad-hoc invalidation.

Example:
```ts
type CaseCommand = 'START'|'HOLD'|'CLOSE'|'EDIT';
export function allowedCaseCommands(status: CollectionCaseStatus): readonly CaseCommand[] { ... }
```
This function is UX only; backend remains authoritative.

Mutation invalidation:
```text
case update/start/hold/close -> case detail + queue + timeline
promise command -> promises + case detail + queue + timeline
dispute command -> disputes + case detail + timeline
action command -> actions + case detail + queue(nextAction) + timeline
```

Proof: page tests assert command visibility plus API 409/403/404 behavior.

## WP-03 Tenant Isolation Spine

Review: ACCEPT WITH IMPROVEMENT.

Risk: exhaustive matrix becomes enormous and brittle, while nested ownership edges remain missed.

High-value improvement:
1. Maintain a machine-readable/resource table in test code describing resource, owner tenant, path attacks, query attacks and body references.
2. Separate non-disclosure assertion from status-code assertion.
3. Add "parent Alpha + child Beta" and "parent Beta + child Alpha" nested-resource attacks.
4. Verify async-created records/outbox payload tenant ownership.
5. Verify list filters cannot act as existence oracle through counts/metadata.

Practical test abstraction:
```java
record IsolationCase(
 String name,
 Supplier<UUID> betaId,
 Function<UUID, ResultActions> alphaPathRead,
 Function<UUID, ResultActions> alphaBodyMutation,
 Runnable assertBetaUnchanged) {}
```

Proof: parameter display name contains resource + attack vector; CI output identifies exact failed boundary.

## WP-04 Financial Correctness

Review: ACCEPT WITH IMPROVEMENT.

Risk: balance invariants can be correct sequentially but fail under concurrent allocations/reversals.

High-value improvement:
1. Define invariant equations explicitly and assert them after every command.
2. Add concurrent allocation race where two valid requests individually fit but combined exceed balance.
3. Test currency scale/rounding boundaries using currencies with different conventional scales only if domain supports them; otherwise enforce project scale contract rather than inventing currency rules.
4. Test reversal vs concurrent new allocation.
5. Make idempotency uniqueness durable at DB level where current model permits.

Invariant:
```text
invoice.originalAmount = invoice.paidAmount + invoice.outstandingAmount
payment.amount = activeAllocatedAmount + unallocatedAmount
all monetary persisted values exact BigDecimal/DecimalString semantics
```

Proof: API response + DB authoritative projection agree after race/replay/reversal.

## WP-05 Ingestion / Import

Review: ACCEPT WITH IMPROVEMENT.

Risk: batch status SUCCESS can hide wrong canonical business data or partial duplicate effects.

High-value improvement:
1. Assert source operation -> canonical aggregate IDs/fields, not just operation state.
2. Introduce deterministic fixture payloads containing optional/null/date/decimal/custom fields.
3. Test retry after crash boundary: reservation created but worker completion absent.
4. Ensure idempotency identity is tenant-scoped; same key in Alpha and Beta must be independent.
5. Verify diagnostic truncation/masking and maximum retained size.

Proof: same source fixture has expected canonical snapshot and exact domain-row counts.

## WP-06 Customer 360

Review: ACCEPT WITH IMPROVEMENT.

Risk: "360" can turn into an unbounded aggregate endpoint or N+1 browser fan-out.

High-value improvement:
1. Set a request-budget expectation per tab/page and measure in frontend tests where practical.
2. Use domain-specific bounded queries, not one mega DTO.
3. Cross-link by stable IDs and preserve back-navigation/filter context.
4. Permission-gate each tab independently; CUSTOMER_READ must not automatically expose RECEIVABLE/COLLECTION/CAMPAIGN data.
5. Define stale-data rule: after financial/collection mutation invalidate corresponding Customer 360 query keys.

Proof: persona with CUSTOMER_READ but no RECEIVABLE_READ sees customer without receivable leakage.

## WP-07 Content / Campaign / File SoD

Review: ACCEPT WITH IMPROVEMENT.

Risk: reference validation may require read access yet accidentally confer manage/publish/delete authority.

High-value improvement:
1. Distinguish "can reference" from "can administer"; server validates reference tenant/state regardless of UI selector.
2. Published template version used by a run is immutable; campaign stores/binds stable version identity.
3. File reference validates tenant + READY/allowed category/ownership semantics.
4. Preview and materialization execute same content safety policy where applicable.
5. Add permission cross-product tests: campaign-only, template-only, file-read-only.

Proof: successful cross-domain workflow with minimal permissions plus negative escalation matrix.

## WP-08 Eligibility / Delivery

Review: ACCEPT WITH CRITICAL IMPROVEMENT.

Risk: an adapter-only AcceptanceUnknown result is not crash-safe. Recovery could later treat it as retryable and physically resend.

High-value improvement:
1. Delivery identity must be generated before provider call and persisted.
2. Ambiguous acceptance must be durable in Message/attempt state or another durable delivery record before automatic recovery can act.
3. Retry policy must distinguish DEFINITELY_NOT_ACCEPTED from ACCEPTANCE_UNKNOWN.
4. Recovery must never convert ACCEPTANCE_UNKNOWN into blind resend.
5. If deterministic mock cannot reconcile unknown acceptance, terminal/manual-investigation safe state is preferable to duplicate delivery; no manual retry UI is added.
6. CampaignRun counters must define whether unknown contributes to sent/failed (normally neither until resolved); encode this once in state transition service.

Required state-machine review before coding:
```text
READY -> PROCESSING -> SENT
                   -> RETRY_WAIT   only known pre-accept transient failure
                   -> FAILED       permanent/exhausted known failure
                   -> ACCEPTANCE_UNKNOWN (durable safe state)
```
Names may differ, semantics may not.

Proof: crash/recovery after ambiguous result yields provider call count still 1.

## WP-09 Recovery / Outbox / Concurrency

Review: ACCEPT WITH IMPROVEMENT.

Risk: multiple services can become competing state-transition authorities.

High-value improvement:
1. Inventory every write to Message.status, retryCount, processing ownership and CampaignRun counters.
2. Route normal worker, retry and recovery through common state-transition service/transactional primitive.
3. Define legal transition table and test terminal monotonicity.
4. Add fencing/ownership assertion for stale worker completion after recovery reclaimed work.
5. Outbox event identity and consumer idempotency identity must be stable and separately testable.

Proof: concurrent worker/recovery/duplicate event scenarios end in one legal terminal business effect.

## WP-10 Real RabbitMQ Broker Smoke

Review: ACCEPT WITH IMPROVEMENT.

Risk: a single happy-path broker test proves topology but not retry/DLX serialization drift.

High-value improvement:
1. One mandatory happy chain DB outbox -> broker -> worker.
2. One malformed/unroutable/failure-path topology assertion only where production configuration defines behavior; do not invent retry topology.
3. Assert message conversion/serialization headers needed by listener.
4. Assert broker test uses production `CommunicationMessagingConfig`, not test-created exchange/queue duplicates.
5. Keep RabbitMQ container shared per class/suite for runtime efficiency, with unique business IDs rather than unique topology.

Proof: persisted result and adapter count; optional queue depth is diagnostic only, not business assertion.

## WP-11 Frontend Product Coherence

Review: ACCEPT WITH IMPROVEMENT.

Risk: page tests can pass with mocked API shapes that have drifted from OpenAPI/backend DTOs.

High-value improvement:
1. Prefer generated/shared contract types if current architecture supports them; otherwise add a contract-drift check rather than hand-maintaining duplicate DTO assumptions.
2. Build a route coverage table from router.tsx and fail review when REAL/PARTIAL route lacks named page test or explicit API_ONLY/DEFERRED classification.
3. Test navigation visibility and direct URL separately.
4. Test 401 session flow separately from 403 capability flow.
5. Remove/hide D04 platform Audit/Operations navigation rather than leaving credible-looking product placeholders.

Proof: route inventory count == classified route count; no orphan primary route.

## WP-12 Golden Journey

Review: ACCEPT WITH CRITICAL IMPROVEMENT.

Risk: one long test fails late with poor diagnostics and becomes flaky/re-run expensive.

High-value improvement:
1. Implement checkpointed orchestration with named phases and durable IDs captured in a JourneyContext.
2. Each checkpoint asserts only cross-domain contract; focused suites own edge cases.
3. Do not continue after a failed checkpoint.
4. Record correlation IDs/business IDs sufficient to diagnose CI failure without secrets.
5. Golden Journey must use real PostgreSQL + RabbitMQ but deterministic local adapter.
6. Add a second lightweight Beta tenant only for critical isolation probes, not duplicate entire journey.
7. Clean bootstrap is part of fixture creation, not assumed from developer DB.

Concept:
```java
record JourneyContext(
 UUID tenantId, UUID serviceClientId, UUID sourceId,
 UUID customerId, UUID invoiceId, UUID paymentId,
 UUID collectionCaseId, UUID templateId, UUID campaignId,
 UUID runId, UUID messageId) {}
```

Named checkpoints:
BOOTSTRAP -> INTEGRATION_READY -> CANONICAL_DATA -> FINANCE -> COLLECTION -> CONTENT -> CAMPAIGN -> DELIVERY -> MONITORING.

Proof: exact failing checkpoint printed; final monitoring state reconciles message/run counters.

## Cross-WP dependency graph

Mandatory execution dependencies:
```text
WP-01 RBAC ---------> WP-02 Collections UI
      |-------------> WP-06 Customer 360
      |-------------> WP-11 Frontend coherence

WP-03 Isolation ----> every domain WP

WP-04 Finance ------> WP-06 Customer 360
      |-------------> WP-08 eligibility

WP-05 Ingestion ----> WP-12 Golden Journey

WP-07 Content ------> WP-08 Delivery

WP-08 Delivery -----> WP-09 Recovery
      |-------------> WP-10 RabbitMQ

WP-01..11 ----------> WP-12 Golden Journey
```

Do not implement WP-12 as a workaround for missing lower-WP tests.

## Shared test-fixture ownership

High-value rule: fixtures are infrastructure, not a hidden second product API.

Create/reuse a small test DSL with explicit identities:
```text
SmokeTenant alpha
SmokeTenant beta
HumanPersona admin/operator/collections/content/campaign/support/auditor/restricted
ServicePrincipal ingest
```

Fixture operations should prefer public API for security/journey tests. Repository builders are allowed for focused domain/concurrency setup when the API itself is not under test.

Never share mutable fixture state between test methods.

## Evidence and review gates

For each WP, Codex must append to completion ledger:
- code files changed;
- tests added/changed;
- exact commands executed;
- result;
- linked T30/P/J/FH/D IDs;
- unresolved risk.

A WP may be CODE_COMPLETE but not VERIFIED. VERIFIED requires executed evidence on the current verification SHA.

## Final high-value review checklist

Before PRE-VC9_GATE_PASS:
1. endpoint-permission ledger has no ROLE_HUMAN-only business-core orphan;
2. route coverage ledger has no REAL/PARTIAL orphan;
3. tenant resource/reference matrix has no untested primary foreign edge;
4. finance invariants survive concurrency;
5. ingestion replay proves canonical row counts;
6. Customer 360 does not leak domains without their READ permission;
7. published content/file references remain tenant-safe and immutable;
8. ambiguous provider acceptance is durable and recovery-safe;
9. all Message/CampaignRun transitions use one semantic authority;
10. real broker chain crosses production topology;
11. Golden Journey reports named checkpoints;
12. exact final SHA has full gate evidence.
