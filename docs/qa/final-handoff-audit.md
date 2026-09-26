# Final Handoff Audit — Functional Hardening

Audit baseline: branch `spec/functional-hardening-user-journeys`
Purpose: prevent contradictory instructions or silent coverage omissions before Codex execution.

## Source-of-truth precedence

If documents appear to conflict, Codex follows this order:
1. `AGENTS.md` non-negotiable rules.
2. `docs/qa/codex-pre-vc9-completion-contract.md` for execution order and final gate.
3. accepted rows in `architecture-business-decision-register.md` for product semantics.
4. `top-30-smoke-contracts.md` for risk scenarios.
5. `security-isolation-matrix.md` and master plan for invariant detail.
6. existing specs for slice-specific behavior.
7. current implementation/tests as evidence of current behavior, never as automatic desired policy.

## Audit findings closed before handoff

1. Competing execution sequences existed between the older execution brief and completion contract. Fixed: completion contract Phase A-G is the sole authoritative sequence.
2. T30-25 referenced an ambiguous provider decision without a real decision ID (and implementation plan said D25). Fixed: D15 now owns ambiguous provider outcome semantics.
3. CI push triggers excluded `spec/**`. Fixed: CI now runs on pushes to the hardening branch family.
4. Top-30 scenarios are all numbered T30-01..30 with no numeric gaps.
5. Current known FH register is FH-001..007; Codex must append newly discovered defects rather than overwrite this baseline.

## Handoff inventory observed at audit time

Repository contains a substantial existing test/spec surface (151 backend Java test files, 49 frontend test files, 62 spec documents, 49 controller files at the audited HEAD). These counts are audit observations, not completion evidence; Codex must regenerate counts because implementation work changes them.

Known hard blockers at handoff remain intentional:
- D01-D15 are accepted and no longer approval blockers.
- FH-001 Collections frontend placeholder.
- T30 scenarios are not executed/verified by this documentation audit.
- final Golden Journey and exact-SHA full gate are not yet proven.

Therefore handoff status is READY_FOR_CODEX_EXECUTION, not PRE-VC9_GATE_PASS.

## Mandatory final reconciliation

At the end, Codex must regenerate route/controller/permission/resource/test inventories from repository state rather than trusting these docs. Differences become new ledger rows or defects. Zero orphan primary surfaces is required.

## CI evidence rule

A GitHub Actions run on exact HEAD is useful evidence but does not replace T30/persona/Golden Journey reconciliation. Conversely, local green without the required exact-SHA CI run does not satisfy the final production-readiness gate when GitHub CI is available.


## Full-stack clarification addendum

D01-D15 are accepted. P1-P15 business-process reconciliation and real RabbitMQ broker wiring are mandatory additions to the original Top-30-only view. The completion contract remains the sole execution order. This audit is a handoff snapshot, not an alternate instruction source.
