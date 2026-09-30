# Local MacBook Functional Assessment

Status: ACTIVE CONTRACT

## Objective

Run an independent local assessment of Collectra on macOS using Codex. Do not assume that CI green, documentation labels, or prior audit conclusions prove runtime functionality. Verify the repository as it exists on this branch and record raw evidence.

Baseline branch: `audit/local-macbook-functional-assessment`
Baseline main SHA: `3a5d2bdcf0933fd4d0f33e8e91fc6338e5d29033`

## Operating rules

1. Work locally on this branch. Do not merge to `main`.
2. Before testing, record:
   - `git rev-parse HEAD`
   - `git status --short`
   - Java, Maven, Node, npm, Docker and Docker Compose versions.
3. Do not declare PASS unless the command was executed successfully on the recorded SHA.
4. On failure preserve the failing command, relevant raw output, root cause and whether the failure is CODE, TEST, CONFIG, DATA, ENVIRONMENT or INFRASTRUCTURE.
5. Do not weaken tests, security checks or assertions merely to obtain green.
6. Keep production code unchanged during the initial assessment. Put findings in an audit report first.
7. Validate tenant isolation and authorization negatively as well as positively.

## Phase 1 — Repository and build

Inspect the repository structure and canonical documentation. Identify backend, frontend, migrations, RabbitMQ/outbox, file/object-storage, analytics and security boundaries.

Execute the repository's documented build gates. At minimum verify formatting, backend unit tests, frontend dependency installation, TypeScript/typecheck, frontend tests and production build.

Then run the full PostgreSQL/Testcontainers integration/security verification used by CI. Run RabbitMQ-dependent tests with a real broker/container where the project expects one.

## Phase 2 — Local runtime

Bring up the dependencies required by the application. Prefer the repository's existing Docker/Compose/Testcontainers configuration rather than inventing an alternative topology.

Start the backend with an appropriate local profile and start the frontend. Record ports, active profile and dependency endpoints. Verify application health/readiness and that Liquibase reaches the expected schema version.

Do not use production credentials.

## Phase 3 — Functional journeys

Exercise the system as business workflows, not merely isolated endpoints.

Verify:

- authentication and tenant context;
- tenant administration, users, memberships and RBAC;
- service clients and integration-source administration;
- source schema, mapping, validation/readiness;
- ingestion/import and diagnostics;
- customer/contact/segment functionality;
- contracts;
- invoices/receivables;
- payments, allocation and reversal;
- collection case/action/promise/dispute flows;
- templates and publishing;
- campaigns, audience/run lifecycle and message materialization;
- generated documents/required attachments where locally executable;
- message monitoring;
- transactional outbox and RabbitMQ hand-off;
- delivery lifecycle using the repository's mock/sandbox path, never a live provider unless explicitly configured by the owner;
- tenant financial analytics;
- tenant communication analytics;
- operations/recovery surfaces;
- profile/password/session management.

For every major workflow capture:
`persona -> UI/command -> API -> persistence/state transition -> observable result`.

## Phase 4 — Security and failure behavior

At minimum verify:

- read-only persona cannot execute MANAGE commands;
- command affordances match backend permissions;
- direct API access still rejects unauthorized commands;
- foreign tenant cannot read or reference another tenant's resources;
- 403 is represented as forbidden;
- tenant-safe 404 does not disclose foreign-resource existence;
- VERSION_CONFLICT causes the expected refresh/recovery behavior;
- duplicate ingestion/business commands remain idempotent where specified;
- RabbitMQ interruption creates recoverable backlog rather than lost business state;
- duplicate broker delivery does not duplicate terminal business effects;
- worker/recovery paths do not leave durable nonterminal state permanently stranded;
- required attachment readiness prevents premature delivery;
- disabled/revoked identities lose access as specified.

Use the existing A02 G01-G18 and F01-F14 evidence as a checklist, but independently verify executable evidence.

## Phase 5 — UI assessment

Run the frontend as an actual user. Evaluate each supported persona:

- Tenant Admin
- Receivables Manager
- Collection Operator
- Campaign Manager
- Template Manager
- Integration Admin
- Auditor/read-only

Check navigation discoverability, permission-aware controls, loading/error/empty states, business terminology, cross-links, raw-ID entry, confirmation of destructive actions, conflict recovery and whether a user can understand the next action without source-code knowledge.

Treat cosmetic improvements separately from functional blockers.

## Phase 6 — Data volume sanity check

This is not a formal capacity benchmark. Populate enough representative data to expose obvious N+1, unbounded-list, pagination, filtering or query-plan problems. Inspect suspicious PostgreSQL queries with `EXPLAIN (ANALYZE, BUFFERS)` where practical.

Do not invent a supported RPS/TPS number without a controlled load test.

## Required report

Create `docs/audit/local-macbook-functional-assessment.md`.

The report must contain:

- exact SHA and environment;
- commands actually executed;
- PASS/FAIL/SKIPPED evidence;
- functional capability matrix;
- persona/RBAC matrix;
- G01-G18 status;
- F01-F14 status;
- UI findings;
- security/tenant-isolation findings;
- runtime/operations findings;
- defects with severity BLOCKER/HIGH/MEDIUM/LOW;
- reproduction steps for every BLOCKER/HIGH defect;
- gaps that require external providers or unavailable infrastructure;
- final distinction between:
  - feature-complete;
  - staging/UAT-ready;
  - release-ready;
  - production-ready.

Do not use an overall numerical score to hide unresolved defects. The conclusion must name the concrete remaining blockers, if any.

## Completion gate

The assessment is complete only when the report is committed on this branch with the exact tested SHA/evidence and all skipped checks have an explicit reason.
