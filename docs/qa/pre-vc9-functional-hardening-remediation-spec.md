# Technical Specification — Pre-VC9 Functional Hardening Remediation

Status: IMPLEMENTATION CONTRACT
Branch: `fix/pre-vc9-functional-hardening-remediation`
Baseline: `spec/functional-hardening-user-journeys@fae4e1ef2a30a716325fc4bbd1822694243ef498`
VC-9 Analytics: OUT OF SCOPE / BLOCKED

## 1. Goal

Turn the accepted pre-VC9 architecture/business baseline into an executable, production-ready candidate for VC-0..VC-8. Fix known defects and any additional defects discovered by mandatory full-stack reconciliation. Do not optimize for test green by weakening contracts.

Success is not "known tests pass". Success is traceable P1-P15 + J01-J10 + T30-01..30 coverage, zero unmapped primary product surface, Golden Journey proof and exact-SHA CI evidence.

## 2. Normative inputs

Implementation companion: `docs/qa/pre-vc9-remediation-code-level-guide.md` is mandatory for code-level patterns, current-code anchors and forbidden shortcuts.



In precedence order:
1. root `AGENTS.md`;
2. `docs/qa/codex-pre-vc9-completion-contract.md`;
3. accepted `docs/qa/architecture-business-decision-register.md` D01-D15;
4. `docs/qa/full-stack-consistency-contract.md`;
5. `docs/qa/codex-autonomous-execution-runbook.md`;
6. T30 contracts/weak zones/implementation plan;
7. security-isolation matrix;
8. P1-P15 frontend processes, J01-J10/master plan and domain specs;
9. current code/tests as current-state evidence only.

D01-D15 are ACCEPTED. Implementation must not ask for approval again.

## 3. Mandatory discovery before fixes

Regenerate from code:
- every frontend route, page, mutation/action and permission gate;
- every REST controller/endpoint/method/security annotation;
- permission catalog, role seeds/migrations and service scopes;
- every tenant-owned aggregate/resource and foreign-reference edge;
- async/outbox/AMQP publishers/listeners/recovery jobs;
- Liquibase migrations and public OpenAPI contracts;
- backend/frontend tests and CI workflows.

Produce/update concrete rows in `pre-vc9-completion-ledger.md`. Every primary route/API/resource must map to process/persona/risk/test or be explicitly classified API_ONLY/DEFERRED with normative basis.

## 4. Remediation work packages

### WP-01 — Business-core RBAC (FH-005 / D01/D03/D06)

Implement capability authorization for Customer, Contract, Receivable and Collection. ROLE_HUMAN/TENANT_USER alone must not grant business access.

Required capability model:
- CUSTOMER_READ / CUSTOMER_MANAGE
- CONTRACT_READ / CONTRACT_MANAGE
- RECEIVABLE_READ / RECEIVABLE_MANAGE
- COLLECTION_READ / COLLECTION_MANAGE

If equivalent canonical permissions already exist, reuse rather than duplicate.

Backend:
- forward-only Liquibase/seed changes;
- method/endpoint guards;
- dependent selectors/lookups obey least privilege;
- no tenant bypass.

Frontend:
- navigation visibility;
- read-only vs manage actions;
- direct URL still handled safely on 403;
- no role-name hard coding.

Tests:
- positive read/manage persona;
- read-only mutation -> 403 + zero state change;
- unrelated capability -> 403;
- service JWT -> human business endpoint denied;
- tenant A foreign IDs remain non-disclosing.

Acceptance: T30-03 and relevant SM-040/041/042/050/121 pass.

### WP-02 — Collections Workspace (FH-001 / D02)

Replace `/collections` placeholder with production MVP workspace.

Queue:
- server paging/filter/sort;
- status, priority, assignee, overdue/next-action filters supported by canonical API;
- customer/invoice/outstanding/payment/assignee/next-action projection;
- loading/empty/error/permission states.

Case workspace:
- authoritative case detail;
- customer + invoice deep links;
- start/hold/update/close according to backend lifecycle;
- optimistic version/conflict handling.

Promise:
- create;
- fulfill/break/cancel;
- no browser auto-transition of overdue promise.

Dispute:
- create;
- resolve/cancel.

Action:
- create;
- complete/cancel;
- dueAt/priority and next-action refresh.

Timeline:
- authoritative chronological timeline; do not reconstruct from independent lists.

Tests:
- page direct load/deep link;
- queue filters/paging;
- lifecycle happy/negative paths;
- 403/read-only;
- 404 foreign case/children;
- 409 stale version;
- refresh after mutations.

Acceptance: FH-001 VERIFIED; T30-06 PASS; P8 proven.

### WP-03 — Exhaustive tenant-isolation spine (FH-006 / D10)

Create reusable Alpha/Beta fixtures and a data-driven matrix for all tenant-owned resources.

For each applicable resource test:
- foreign ID in path;
- foreign ID in query/filter;
- foreign ID/reference in mutation body;
- update/delete/command;
- nested child under foreign parent;
- existence non-disclosure;
- zero state change.

Minimum resource families:
identity membership/roles/sessions, service clients, schemas, mappings, sources, imports, files, customers/contacts/segments, contracts, invoices/payments/allocations, collection cases/promises/disputes/actions, templates/versions/assets, campaigns/runs/recipients, messages/attachments/documents.

Do not assert 404 mechanically if an existing public contract intentionally uses another non-disclosing response; prove non-disclosure and no mutation.

Acceptance: T30-01/02/04/05 and SM-120 evidence complete.

### WP-04 — Financial correctness spine (D06)

Prove and repair:
- payment/allocation/reversal exact decimal invariants;
- same customer/currency;
- cannot exceed payment/invoice balance;
- commandId replay same intent idempotent;
- same id different payload conflicts;
- concurrent allocations cannot over-allocate;
- reversal idempotency/version/audit reason;
- authoritative invoice/payment projections refresh after mutation.

Frontend uses lossless decimal transport and never computes authoritative balances.

Acceptance: T30-07..10 and P7 pass.

### WP-05 — Ingestion/import correctness

Prove:
- service authentication/scopes;
- human/service trust-zone separation;
- source/schema/mapping readiness;
- idempotency same-key/same-body;
- same-key/different-body conflict;
- CUSTOMER/INVOICE/PAYMENT canonical persistence;
- durable bounded diagnostics;
- malformed/unsafe CSV/XLSX/JSON/XML behavior;
- no cross-tenant mapping/source references.

Acceptance: T30-11..14, P5 and SM-020/021/030/090 pass.

### WP-06 — Customer 360 and cross-domain coherence (D05)

Customer detail must be the operational anchor with authoritative/deep-linked contacts, segments, contracts, receivables, collections and campaign/message context where current APIs support it.

Rules:
- no per-row N+1 fan-out;
- no client recomputation of finance;
- missing bounded projection is fixed backend-first;
- mutation invalidation refreshes affected authoritative queries.

Acceptance: T30-15 and P3/P4/P6 relevant paths pass.

### WP-07 — Content/Campaign/File separation (D08)

Preserve independent capability families and prove cross-domain reference reads without privilege escalation.

Template: create/version/edit/validate/preview/publish/assets.
Campaign: create/activate/prepare/recheck/run/recipient/message.
Files: upload/metadata/download/delete and safe owning-domain references.

Negative tests:
- campaign manager cannot publish templates unless separately granted;
- content manager cannot manage campaigns;
- read does not imply manage/delete;
- foreign template/file/customer references rejected/non-disclosing.

Acceptance: T30-16..20, P9/P10/P13 pass.

### WP-08 — Eligibility and delivery correctness (D09/D12/D15)

Prove payment/eligibility recheck can prevent stale delivery.

Delivery scenario matrix:
SUCCESS, PERMANENT_FAILURE, TRANSIENT_FAILURE_THEN_SUCCESS, RETRY_EXHAUSTED, TIMEOUT_BEFORE_ACCEPT, ACCEPT_THEN_TIMEOUT/AMBIGUOUS, INVALID_DESTINATION, ATTACHMENT_PENDING, ATTACHMENT_FAILED, DUPLICATE_QUEUE_EVENT.

Ambiguous acceptance:
- stable delivery/idempotency identity;
- no blind resend;
- explicit safe state/handling;
- no double run counters.

Monitoring:
- read-only;
- masked destination;
- safe errors;
- providerMessageId only where safe;
- no raw provider body/credentials;
- no fabricated attempt history.

Acceptance: T30-21..25 and P11/P12 pass.

### WP-09 — Recovery/outbox/concurrency

Prove:
- stale PROCESSING recovery;
- terminal transitions idempotent;
- message/run counters atomic;
- concurrent claims single-owner;
- retry dispatch produces one event;
- duplicate consumption one business effect;
- tenant identity survives durable event path.

Acceptance: T30-26/27/28 pass.

### WP-10 — Real RabbitMQ broker smoke

Current in-process listener tests are insufficient.

Add real RabbitMQ Testcontainers coverage:
PostgreSQL/outbox -> publisher -> exchange -> binding -> queue -> serializer -> listener -> worker -> deterministic adapter -> persisted Message/CampaignRun result.

Requirements:
- add official Testcontainers RabbitMQ module if absent;
- use production-equivalent Spring AMQP topology/config;
- bounded Awaitility/observable polling, no Thread.sleep;
- deterministic local adapter, no external provider;
- include in CI, not local-only.

Acceptance: broker smoke green on exact SHA and linked to T30-27/T30-30.

### WP-11 — Frontend route/product coherence (FH-007 / D04)

For every real MVP route:
- direct route load;
- loading/empty/success/error;
- permission/read-only state;
- deep-link behavior;
- mutation success and invalidation where applicable;
- 401/403/404/409 semantics.

Explicitly reconcile campaigns, files, imports, integrations, messages, receivables, templates, login, admin/profile and Collections.

Platform:
- Analytics remains VC-9;
- Audit/Operations must not appear as completed placeholder features.

Acceptance: FH-007 VERIFIED; T30-29 and P1-P15 frontend-relevant rows complete.

### WP-12 — Golden Journey

Clean environment mandatory composite:
1 tenant bootstrap/admin;
2 permissions/personas;
3 service client;
4 source schema;
5 mapping;
6 source activation/readiness;
7 service auth;
8 ingest customer;
9 ingest invoice;
10 ingest payment;
11 allocation/financial state;
12 overdue/collection case/action or promise;
13 template validate/preview/publish;
14 campaign create/activate/prepare;
15 eligibility recheck;
16 recipient/message/required attachment;
17 outbox -> real RabbitMQ -> listener/worker;
18 deterministic adapter;
19 monitoring safe state;
20 Alpha/Beta isolation negative checks around critical IDs.

No external provider.

Acceptance: T30-30 + D13.

## 5. Cross-cutting quality requirements

Security: no secret/token/provider credential/raw destination leakage.
Transactions: financial/delivery multi-aggregate changes atomic where contract requires.
Concurrency: use DB constraints/locking/versioning/idempotency; do not rely on timing.
Performance: bounded paging/input; avoid N+1 and unbounded polling.
Observability: safe structured diagnostics/correlation; no sensitive payload logs.
Schema: forward-only Liquibase and clean bootstrap.
API: OpenAPI compatibility or deliberate same-change contract update.
Frontend: TypeScript strict/typecheck, query invalidation, no authoritative state duplication.

## 6. Test evidence model

A test file existing is NOT PASS.
Each ledger row records:
requirement, persona, route/API, permission, tenant invariant, exact test method/class, result, exact SHA/run, linked FH/D/T30/P/J.

A parameterized matrix may cover many rows only if each case is independently named/reportable.

## 7. Execution order

Use the normative Phase A-G sequence:
A T30-01..05 security.
B T30-07..14 finance/ingestion.
C T30-06 Collections.
D T30-15..28 customer/content/campaign/delivery/recovery.
E T30-29 frontend.
F T30-30 Golden Journey.
G full gate + anti-omission reconciliation.

P1-P15 and J01-J10 are accumulated throughout and reconciled before G.

## 8. Required verification

Final HEAD must execute:
- backend formatting;
- backend clean verify with coverage profile;
- PostgreSQL integration suites;
- real RabbitMQ Testcontainers broker smoke;
- frontend npm ci/typecheck/test:ci/build;
- OpenAPI compatibility;
- clean Liquibase/bootstrap;
- T30-01..30;
- P1-P15;
- J01-J10;
- Golden Journey;
- regenerated route/controller/permission/resource/test inventory.

CI must run on exact final SHA. A later commit invalidates final evidence.

## 9. Defect workflow

New finding -> append FH-008+ with severity/evidence.
OPEN -> FIXED requires fix SHA.
FIXED -> VERIFIED requires executed regression evidence on verification SHA.
P0/P1 cannot be waived by Codex.
P2 waiver requires explicit product-owner approval.
Do not close by inspection.

## 10. Non-goals

- VC-9 analytics implementation;
- live email/SMS/WhatsApp/Telegram provider acceptance;
- new manual delivery retry/cancel product commands;
- platform impersonation;
- unrelated refactoring/replatforming.

## 11. Definition of Done

Only PRE-VC9_GATE_PASS when:
- D01-D15 implemented;
- no unresolved P0/P1 and no unapproved P2;
- FH known/discovered defects VERIFIED or explicitly out-of-scope by normative decision;
- T30-01..30 PASS;
- P1-P15 and J01-J10 traced/proven;
- zero orphan primary routes/endpoints/tenant resources;
- Golden Journey PASS;
- exact final SHA CI PASS;
- ledger and defect register match actual repository state.

Otherwise final state is BLOCKED with exhaustive evidence.
