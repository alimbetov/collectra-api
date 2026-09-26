# AGENTS.md — Collectra pre-VC9 functional hardening

## Mission
On branch `fix/pre-vc9-functional-hardening-remediation`, implement and verify the accepted pre-VC9 hardening specification for VC-0..VC-8 as one coherent production-ready candidate. `spec/functional-hardening-user-journeys` is the accepted specification baseline, not the implementation target. Do not modify that spec branch as part of remediation and do not implement VC-9.

## Normative source order
Read before code changes:
0. `docs/qa/CODEX-START-HERE.md` — remediation-branch entry point and execution bootstrap.
1. `docs/qa/codex-pre-vc9-completion-contract.md` — ONLY execution order and final gate.
2. `docs/qa/architecture-business-decision-register.md` — ACCEPTED D01-D15 product/architecture semantics.
3. `docs/qa/full-stack-consistency-contract.md` — frontend/backend/data/broker traceability.
4. `docs/qa/codex-autonomous-execution-runbook.md` — autonomous work/verification loop.
5. `docs/qa/top-30-smoke-contracts.md`, `top-30-weak-zones.md`, `top-30-smoke-implementation-plan.md`.
6. `docs/qa/security-isolation-matrix.md`.
7. `docs/qa/functional-hardening-master-plan.md` and P1-P15 frontend specs.
8. inventories/registers/diagrams and relevant domain specs.
9. current implementation/tests as evidence of CURRENT behavior, never automatic desired policy.

If lower-priority text conflicts with higher-priority text, follow higher priority and update stale lower-priority documentation in the same change. Do not maintain competing instructions.

## Accepted decisions
D01-D15 are already accepted. Codex MUST implement them; do not stop asking for their approval. A newly discovered semantic conflict requires D16+ and BLOCKED. Technical implementation choices do not require product approval.

## Non-negotiable invariants
- Absolute tenant isolation; authenticated tenant context is authoritative.
- Server authorization is authoritative; UI hiding is UX.
- Path/query/body foreign references and zero-state-change denial are tested.
- Human/platform/service principals are separate trust zones.
- No committed/logged passwords, JWTs, refresh tokens, client secrets or provider credentials.
- Financial/eligibility/workflow/delivery state is backend authoritative.
- No blind overwrite on 409; no blind resend on ambiguous provider acceptance.
- VC-8 monitoring is read-only; no fabricated attempt history/manual retry/cancel.
- External provider network is outside this gate; deterministic adapter is the boundary.
- P0/P1 block; P2 closes unless explicitly product-owner waived.
- Never weaken a valid test merely to obtain green.
- PASS requires executed evidence on exact SHA.

## Mandatory execution
Follow completion contract Phase A→G exactly:
A T30-01..05 security.
B T30-07..14 finance/ingestion.
C T30-06 Collections closure.
D T30-15..28 customer/content/campaign/delivery/recovery.
E T30-29 frontend coherence.
F T30-30 clean Golden Journey.
G full production-readiness + anti-omission reconciliation.

Throughout phases also accumulate/reconcile P1-P15 and J01-J10 evidence. T30 is risk coverage, not a substitute for business-process coverage.

## Required final inventories
Regenerate and reconcile frontend routes/actions, backend endpoints, authorities/scopes, tenant-owned resources, D01-D15, P1-P15, J01-J10, T30-01..30, FH defects, migrations/OpenAPI and all relevant tests. Zero unmapped primary surface is required.

## Evidence ledger
Maintain `docs/qa/pre-vc9-completion-ledger.md` as individual traceable rows. Range placeholders are not sufficient at completion.

## Verification
Every fix: reproducer -> root cause -> coherent full-stack fix -> regression -> focused run -> affected journey/security run -> ledger -> commit.
Final exact HEAD must pass repository CI plus the required real RabbitMQ Testcontainers broker smoke, frontend typecheck/tests/build, backend verify/coverage, OpenAPI, clean Liquibase/bootstrap, T30/personas/P1-P15 and Golden Journey.

## Stop condition
Only:
- `PRE-VC9_GATE_PASS` with exact-SHA/run evidence; or
- `BLOCKED` with exhaustive blockers/evidence.

Never say DONE/GREEN/PRODUCTION-READY and never start VC-9 while the completion contract is unsatisfied.
