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
