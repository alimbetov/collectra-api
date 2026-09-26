# AGENTS.md — Collectra pre-VC9 functional hardening

## Mission

When working on branch `spec/functional-hardening-user-journeys`, treat `docs/qa/codex-functional-hardening-execution-brief.md` as the execution contract.

Goal: prove and harden VC-0..VC-8 as one coherent multi-role product before VC-9 Analytics. Do not start VC-9 implementation.

## Required source order

Read before changing code:
1. `docs/qa/codex-functional-hardening-execution-brief.md`
2. `docs/qa/functional-hardening-master-plan.md`
3. `docs/qa/wave-a-route-controller-permission-inventory.md`
4. `docs/qa/security-isolation-matrix.md`
5. `docs/qa/smoke-test-technical-identities.md`
6. `docs/qa/functional-hardening-smoke-matrix.md`
7. `docs/qa/functional-hardening-defects.md`
8. `docs/qa/role-process-diagrams.md`
9. `docs/qa/wave-b-reproducible-smoke-fixture.md`
10. relevant `docs/specs/**`, controller/service/repository code, frontend routes/pages and existing tests.

Repository code is evidence of current behavior, not automatically the desired business policy.

## Non-negotiable rules

- Never invent a business decision to make a test green.
- Never weaken/delete a valid security assertion merely because current code fails it.
- Never mark PASS from code inspection or test presence. Execute it and record evidence.
- Tenant isolation is absolute unless a future explicit audited support/impersonation contract says otherwise.
- UI hiding is not authorization. Server-side authorization is authoritative.
- Test path, query and body foreign references, not only GET-by-id.
- Human, platform and service principals are distinct trust zones.
- No secrets/JWT/client secrets/passwords in Git or logs.
- Delivery acceptance currently ends at deterministic local/mock channel adapter boundary.
- VC-8 monitoring remains read-only; do not fabricate attempt history or add retry/cancel.
- Do not start VC-9 until the hardening exit gate is satisfied.
- P0/P1 defects block progression; P2 requires closure or explicit documented waiver.
- Preserve existing VC-0..VC-8 contracts unless an accepted decision explicitly changes them.

## Decision stop rule

If desired product behavior is ambiguous, add/update a row in
`docs/qa/architecture-business-decision-register.md` using:
Problem -> evidence -> user example -> recommendation -> consequences -> alternatives -> decision required.

Do not implement that policy-dependent remediation until the decision is accepted.

Technical implementation choices that do not change business semantics are Codex's responsibility and do not require a workshop decision.

## Verification

For every remediation:
reproducer -> root cause -> focused fix -> regression test -> affected suites -> full required gate.

Do not claim green without executed evidence on the exact SHA.


## Top-30 risk-based smoke gate

Before VC-9, also read and execute:
- `docs/qa/top-30-weak-zones.md`
- `docs/qa/top-30-smoke-contracts.md`
- `docs/qa/top-30-smoke-implementation-plan.md`

These T30 scenarios are the prioritized cross-domain smoke layer. Do not replace existing focused regression suites with them. Implement shared fixtures/parameterized matrices, link every T30 to exact automated evidence, and keep its result NOT_RUN/BLOCKED until executed. T30-30 is the final composite Golden Journey gate.


## Fail-closed completion contract

Codex MUST read and obey `docs/qa/codex-pre-vc9-completion-contract.md` and maintain `docs/qa/pre-vc9-completion-ledger.md`.

Mandatory code execution order:
1. T30-01..05 — Alpha/Beta security spine.
2. T30-07..14 — financial/ingestion spine.
3. Resolve and implement Collections closure (T30-06/FH-001/D02).
4. T30-15..28 — customer/campaign/content/delivery/recovery.
5. T30-29 — frontend route/product coherence.
6. T30-30 — clean-environment Golden Journey.
7. Full production-readiness verification and anti-omission reconciliation.

Do not stop merely because currently failing tests are green. Re-enumerate routes/controllers/permissions/tenant-owned resources/tests/defects/decisions and prove there are no unmapped items. Final verdict is exactly PRE-VC9_GATE_PASS or BLOCKED. PRE-VC9_GATE_PASS is forbidden unless all completion-contract conditions have executed evidence on exact HEAD.


## Final handoff audit

Read `docs/qa/final-handoff-audit.md` before execution. It defines document precedence and records resolved handoff inconsistencies. If any instruction appears contradictory, do not choose the easier path: apply the precedence order and record any still-unresolvable conflict as BLOCKED.
