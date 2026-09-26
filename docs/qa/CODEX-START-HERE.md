# CODEX START HERE — Pre-VC9 Remediation

Status: MANDATORY ENTRY POINT

## Target

Work only on:
`fix/pre-vc9-functional-hardening-remediation`

Specification baseline:
`spec/functional-hardening-user-journeys@fae4e1ef2a30a716325fc4bbd1822694243ef498`

Do not implement on the specification branch. Do not start VC-9.

## Bootstrap checks

Before changing code:
1. verify current branch is exactly `fix/pre-vc9-functional-hardening-remediation`;
2. record current HEAD and clean/dirty status;
3. read root `AGENTS.md`;
4. read `remediation-branch-handoff.md`;
5. read `pre-vc9-functional-hardening-remediation-spec.md`;
6. read `pre-vc9-remediation-code-level-guide.md`;
7. read `pre-vc9-wp-architecture-review.md`;
8. read `codex-pre-vc9-completion-contract.md`;
9. read accepted D01-D15 in `architecture-business-decision-register.md`;
10. read T30 contracts/implementation plan and `pre-vc9-completion-ledger.md`.

If the branch is wrong, stop before edits.

## Operating mode

You are implementing an accepted design, not reopening D01-D15.

For each defect/WP:
```text
inventory/evidence
 -> reproducer
 -> root cause
 -> coherent full-stack fix
 -> focused regression
 -> negative/security regression
 -> affected T30/P/J evidence
 -> ledger
 -> commit
```

Do not stop after writing tests, making one test green, closing one FH defect, or completing one WP. Continue Phase A -> G until the exact current HEAD satisfies PRE-VC9_GATE_PASS or a genuine BLOCKED condition exists.

## Mandatory phase order

A. T30-01..05 security spine / WP-01 + WP-03 foundations.
B. T30-07..14 finance and ingestion / WP-04 + WP-05.
C. T30-06 Collections / WP-02.
D. T30-15..28 Customer 360, content, campaign, delivery, recovery / WP-06..WP-10.
E. T30-29 frontend coherence / WP-11.
F. T30-30 checkpointed Golden Journey / WP-12.
G. Full production gate and anti-omission reconciliation.

Dependencies in `pre-vc9-wp-architecture-review.md` still apply inside this phase order.

## WP completion semantics

Use these states:
- NOT_STARTED
- IMPLEMENTING
- CODE_COMPLETE
- TESTED
- VERIFIED
- BLOCKED

CODE_COMPLETE is not VERIFIED.
TESTED is not VERIFIED if linked security/business/process evidence is missing.
VERIFIED requires the WP Definition of Done and evidence on the current verification SHA.

## Never do

- do not change expected behavior merely to match current code;
- do not weaken security assertions;
- do not bypass auth in security smoke;
- do not use direct listener calls as RabbitMQ proof;
- do not create a parallel API when an accepted API already exists;
- do not use frontend-calculated authoritative money/workflow/delivery state;
- do not blind-retry ACCEPTANCE_UNKNOWN;
- do not edit historical Liquibase changesets for new behavior;
- do not call a branch green from inspection or test existence;
- do not start VC-9.

## Final output

Only one final verdict is valid:
`PRE-VC9_GATE_PASS`
or
`BLOCKED`.

PRE-VC9_GATE_PASS requires exact-SHA evidence for all final gates and a fully reconciled ledger. Any commit after final verification invalidates that verification and requires the affected/full final gate to run again.
