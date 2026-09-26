# Codex Execution Brief — Architecture/Business Decision Workshop + Functional Hardening

Status: ACTIVE
Branch: `spec/functional-hardening-user-journeys`
Gate: before VC-9 Analytics

## 1. Objective

Audit, reproduce and harden every existing user journey across VC-0..VC-8. Find all existing smoke/security/E2E tests and all rules in specs/code, map them to personas and routes/APIs, identify contradictions/gaps, and debug the product as a coherent business process.

Do not optimize for making the current test suite green. Optimize for proving the intended product behavior.

## 2. Mandatory discovery pass

Before remediation, inventory:
- every frontend primary route/page/navigation permission;
- every backend controller endpoint and method/class authorization;
- service/repository tenant predicates and foreign-reference validation;
- all permissions/RBAC seed migrations;
- all integration/unit/frontend/security/smoke tests;
- all specs governing identity, integration, customer, contract, receivable, collection, templates, files, campaigns, delivery, messages and reporting;
- placeholders and API-only surfaces;
- existing CI gates and Testcontainers usage.

Search the entire repository. Do not rely only on files already named in docs/qa.

Update the Wave-A inventory and smoke matrix with exact test class/file names. Classify each surface REAL, PARTIAL, PLACEHOLDER, API_ONLY or VC9. Test existence is not PASS.

## 3. Decision workshop backlog

For each item below, gather evidence first. If current desired behavior is not already explicit, create a decision-register entry rather than guessing.

### D01 Business-core RBAC
Question: should all TENANT_USER actors read/mutate Customers, Contracts, Receivables and Collections, or should these surfaces use capability permissions such as READ/MANAGE?

Architect recommendation to present: capability-based server authorization, with read/manage separation and persona composition. Verify whether existing permission vocabulary can be reused before adding permissions.

Required evidence: current controller annotations, navigation gates, RBAC seeds, role tests, service predicates, mutation APIs and a restricted-user adversarial test.

### D02 Collections Workspace
Define the complete `/collections` user journey:
queue -> filters/assignment -> case -> start/hold/resume/close -> promise/dispute/action -> immutable timeline -> concurrency/error states.

Do not accept backend existence while the primary route is PlaceholderPage.

### D03 Auditor and Support/Ops
Define read-only surfaces and prohibited mutations. Support may inspect safe delivery diagnostics but gains no implicit customer/financial/campaign mutation. Auditor mutations must be denied server-side.

### D04 Platform Audit / Operations
Determine whether each is MVP functionality, intentionally deferred, or should be removed from current navigation. A placeholder must never be presented as completed functionality.

### D05 Customer 360
Determine required cross-links/sections from customer detail: identity/contact/segments/contracts/receivables/payments/collection/campaign context. Do not duplicate authoritative state into frontend inference.

### D06 Receivable operations
Determine which personas may allocate/reverse payments and what audit/reason/idempotency/concurrency requirements apply. Invoice/payment state remains authoritative backend state.

### D07 Integration operations
Separate manage vs diagnostic/read permissions for service clients, schemas, mappings, IntegrationSource, imports/operations. Service principals remain scope-based and distinct from humans.

### D08 Campaign / Template / File separation of duties
Verify existing CAMPAIGN_*, TEMPLATE_* and FILE_* permissions cover actual workflows. Identify privilege combinations required for cross-domain references without granting unnecessary mutation.

### D09 Delivery operations
Current recommendation: Support/monitoring stays read-only. No manual retry/cancel unless a separate business contract is explicitly accepted. Preserve VC-8 no-fabricated-attempt-history rule.

### D10 Tenant isolation
Default architecture decision: absolute tenant isolation. Platform admin receives no implicit tenant-business access. Any future impersonation/support access must be a separate explicit, audited, time-bounded mechanism.

Test foreign IDs in path/query/body, foreign object references, cache keys, async/outbox/recovery, DTO/log leakage and enumeration/non-disclosure.

### D11 Smoke personas
Validate Alpha/Beta symmetric fixtures and platform/service principals. Personas are permission compositions, not new hard-coded system roles.

### D12 MVP delivery boundary
Acceptance ends at deterministic local/mock channel adapter boundary. Real provider acceptance is outside this hardening scope.

### D13 Golden Journey
Mandatory E2E chain:
tenant/admin -> integration configuration -> idempotent CUSTOMER/INVOICE/PAYMENT ingestion -> customer/receivable visibility -> allocation -> overdue/collection -> template -> campaign -> eligibility recheck -> run -> message -> attachment gate -> worker/router -> deterministic channel adapter -> monitoring.

### D14 VC-9 prerequisite
Do not start VC-9 while an unwaived P0/P1 exists or Golden Journey/security persona matrix is not proven.

### D15 Ambiguous provider outcome
Define the adapter/worker contract for accept-then-timeout or otherwise ambiguous acceptance. Architect recommendation: stable delivery/idempotency key, explicit ambiguous state/handling, and no unsafe blind resend.

## 4. Security test model

Provision two symmetric tenants per isolated test run:
`smoke-alpha` and `smoke-beta`.

Use personas:
admin, operator, collections, content, campaign, support, auditor, restricted plus service-ingest. Add platform-smoke-admin outside tenant scope.

For every tenant-owned resource test where applicable:
1. Alpha permitted operation on Alpha resource.
2. Alpha same operation using Beta path ID.
3. Alpha query filtered by Beta ID.
4. Alpha mutation body referencing Beta object.
5. Alpha restricted persona direct API call despite hidden UI.
6. service JWT against human endpoint.
7. human JWT against service-only endpoint.
8. stale user authorization after role/status/session change.
9. stale service credential/JWT after rotate/block.
10. zero cross-tenant state change after every denied mutation.

Resources include identity membership/roles where applicable, service clients, schemas, mappings, sources, ingestion operations, imports, files, customers, contacts, segments, contracts, invoices, payments, allocations, collection cases and children, templates/versions/assets, campaigns/runs/recipients, messages/attachments and reporting projections.

## 5. Smoke/E2E discovery and repair rules

For each existing test:
- map it to persona, journey, route/API, business invariant and security invariant;
- identify duplicate tests versus genuinely missing behavior;
- preserve useful tests;
- repair stale tests only when the intended contract is established;
- if test and implementation disagree, inspect spec/business decision before changing either;
- add a regression test for every confirmed defect;
- avoid sleep-based async tests; use bounded observable polling;
- Testcontainers must exercise real PostgreSQL and RabbitMQ paths where the behavior depends on them.

Frontend smoke must prove route wiring, permission-aware navigation/actions, deep links, error/409 recovery, server paging/filter state and read-only restrictions. Backend smoke remains authoritative for authorization.

## 6. CI configuration

Development may use one environment-scoped secret `COLLECTRA_SMOKE_CONFIG` for external/browser smoke orchestration. It may contain the complete ephemeral fixture configuration, but identities retain distinct generated credentials.

Rules:
- no secret values in repository;
- never print config, password, JWT, refresh token or client secret;
- do not use one common credential for all personas;
- integration tests should prefer provisioning via public/admin APIs and Testcontainers rather than persistent credentials;
- production must never consume development smoke config.

## 7. Defect loop

For each failure:
1. capture exact scenario/test and raw relevant error;
2. reproduce deterministically;
3. classify P0/P1/P2/P3;
4. determine whether it is implementation defect, stale test, missing test, ambiguous business rule or environment flake;
5. for ambiguous business rule: decision register and stop that remediation;
6. otherwise fix root cause, not symptom;
7. add/adjust regression coverage;
8. rerun focused suite;
9. rerun affected journey;
10. update defect/smoke matrices with evidence.

Never downgrade severity just to unblock VC-9.

## 8. Execution order

The single authoritative execution order is `docs/qa/codex-pre-vc9-completion-contract.md` Phase A through G. This brief supplies discovery and decision semantics only; it does not define a competing sequence. Surface unresolved D01-D15 decisions with evidence and recommendation at the earliest phase that depends on them. Persona J01-J10 evidence is accumulated throughout the phases and reconciled before the final gate.

## 9. Required artifacts kept current

- `docs/qa/wave-a-route-controller-permission-inventory.md`
- `docs/qa/functional-hardening-smoke-matrix.md`
- `docs/qa/functional-hardening-defects.md`
- `docs/qa/security-isolation-matrix.md`
- `docs/qa/architecture-business-decision-register.md`
- `docs/qa/role-process-diagrams.md`
- `docs/qa/wave-b-reproducible-smoke-fixture.md`

## 10. Reporting format

At each meaningful checkpoint report:
- exact branch/SHA;
- what was inventoried/executed;
- raw failing scenario/test if any;
- root cause or BUSINESS_DECISION_REQUIRED;
- defect IDs/severity;
- files changed;
- tests actually executed and result;
- next single action.

Never say green/verified/closed unless the stated tests were actually executed successfully on that exact SHA.

## 11. Exit gate

VC-9 is allowed only when:
- every VC-0..VC-8 primary surface is classified and covered;
- no unwaived P0/P1;
- P2 closed or explicitly waived with rationale;
- Collections user journey is implemented or explicitly removed from MVP;
- persona positive/negative authorization matrix passes;
- exhaustive tenant-isolation matrix passes;
- Golden Journey passes to deterministic channel boundary;
- delivery/recovery/concurrency matrix passes;
- frontend typecheck/test/build passes;
- backend formatting/verify passes;
- PostgreSQL + RabbitMQ Testcontainers pass;
- OpenAPI regression passes;
- clean DB bootstrap passes;
- exact verification SHA is recorded.
