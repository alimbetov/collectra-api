# CODEX START HERE — Functional Hardening User-Journey Verification

Status: MANDATORY ENTRY POINT

## Target

Work on:
`spec/functional-hardening-user-journeys`

Current implementation baseline:
`main@898cd34637a21c0531c5b9e97dd065c68abf12c7`

The specification branch was fast-forwarded to that same baseline after the pre-VC9 remediation merge. Treat the current repository state as the implemented baseline. Do **not** replay historical remediation merely because an older ledger/defect entry says IMPLEMENTING, TESTED, BLOCKED or verification pending.

Do not start VC-9 Analytics in this pass.

## Objective

Verify the implemented VC-0..VC-8 product as coherent end-to-end user journeys. Find and fix only defects or contract gaps that are reproduced against the current branch.

The operating sequence is:

```text
current code/spec inventory
 -> reproduce journey/contract
 -> classify actual result
 -> fix only confirmed gap
 -> regression test
 -> affected journey
 -> exact-SHA verification
 -> evidence reconciliation
```

A historical finding is evidence, not a current defect, until reproduced on the current baseline.

## Bootstrap checks

Before changing code:

1. verify current branch is exactly `spec/functional-hardening-user-journeys`;
2. record current HEAD and clean/dirty status;
3. verify the branch contains `main@898cd34637a21c0531c5b9e97dd065c68abf12c7` or a later main baseline;
4. read root `AGENTS.md`;
5. read this file;
6. read `codex-functional-hardening-execution-brief.md`;
7. read `codex-autonomous-execution-runbook.md`;
8. read `codex-pre-vc9-completion-contract.md`;
9. read accepted D01-D15 in `architecture-business-decision-register.md`;
10. read `pre-vc9-functional-hardening-remediation-spec.md`, WP DoD, T30 contracts, security/isolation matrix and completion ledger as verification contracts;
11. regenerate route/controller/permission/resource/test inventories from current code before trusting old counts.

If the branch is wrong, stop before edits.

## Source-of-truth rule

Use this precedence for current behavior:

1. accepted architecture/business decisions D01-D15;
2. normative completion/security/full-stack contracts;
3. current implementation and migrations;
4. current executable tests;
5. historical defect/ledger status.

Do not change expected behavior merely to make a stale test pass. Do not reopen an accepted D01-D15 decision unless current evidence exposes a genuine semantic contradiction; record that as D16+ and BLOCKED.

## Verification-first rule

For every historical WP/FH/T30/P/J item:

- inspect the current implementation;
- execute or extend the smallest deterministic reproducer;
- if it passes, reconcile evidence/status; do not rewrite the feature;
- if it fails, capture the raw relevant failure, classify severity and root cause;
- fix the coherent full-stack contract;
- add/adjust regression coverage;
- rerun focused tests and the affected user journey.

Never infer a current defect solely from an old branch SHA or old CI failure.

## Mandatory journey coverage

Reconcile at minimum:

- identity/RBAC and tenant isolation;
- integration/service-client trust boundary and ingestion;
- Customer 360;
- contracts;
- receivables/payments/allocation/reversal;
- Collections workspace and lifecycle;
- templates/files/generation;
- campaigns/eligibility/runs;
- messages/attachments/delivery/recovery;
- monitoring/read-only support surfaces;
- frontend route/navigation/action permissions;
- T30-01..30;
- personas J01..J10;
- P1..P15;
- Golden Journey through deterministic adapter boundary.

VC-9 Analytics remains out of scope.

## Full-stack change rule

A confirmed backend authorization defect requires permission catalog/seed/migration, backend guard, frontend gate and tests to remain coherent.

A confirmed API DTO/status/error defect requires OpenAPI, frontend contracts/adapters and tests to remain coherent.

A confirmed lifecycle/concurrency defect requires mutation and read projection to remain coherent.

A confirmed route defect requires direct load/deep link, permission, loading/empty/error and conflict coverage where applicable.

Do not introduce unrelated refactors.

## Required final gates

On the final candidate SHA run the applicable repository gates, including:

```bash
mvn --batch-mode --no-transfer-progress spotless:check
mvn --batch-mode --no-transfer-progress clean verify -Pslice10a-coverage
```

and:

- PostgreSQL Testcontainers paths;
- real RabbitMQ Testcontainers broker smoke;
- clean Liquibase/bootstrap;
- OpenAPI compatibility/regression;
- `frontendweb`: `npm ci`, `npm run typecheck`, `npm run test:ci`, `npm run build`;
- T30/persona/P1-P15 reconciliation;
- checkpointed Golden Journey;
- regenerated route/controller/permission/resource/test inventories.

A newer commit invalidates final exact-SHA evidence and requires the affected/final gate to be rerun.

## External production-safety condition

Do not fabricate closure of an external secret-rotation requirement. If a credential recorded in Git history was ever used in an environment, production readiness requires evidence that the external credential was rotated/revoked. Code/config cleanup alone is not rotation evidence.

## Never do

- do not replay the old remediation branch as a checklist of missing implementation;
- do not weaken security assertions;
- do not bypass authorization in smoke tests;
- do not use direct listener calls as RabbitMQ proof;
- do not create a parallel API where an accepted API exists;
- do not use frontend-calculated authoritative money/workflow/delivery state;
- do not blind-retry ambiguous provider acceptance;
- do not edit historical Liquibase changesets for new behavior;
- do not call inspection or test existence PASS evidence;
- do not claim green/verified/closed without executed evidence on the stated SHA;
- do not implement VC-9 Analytics.

## Checkpoint report

At each meaningful checkpoint report:

- exact branch/SHA;
- inventory/journey executed;
- actual command/test and result;
- raw relevant failure when present;
- root cause and defect ID/severity when confirmed;
- files changed;
- focused and affected-journey reruns;
- next single action.

Continue autonomously while a confirmed in-scope defect is actionable.

## Final output

The final report must distinguish:

- implementation status;
- executed verification status;
- external production-safety blockers;
- deferred VC-9 scope.

Only emit `PRE-VC9_GATE_PASS` when the current exact SHA satisfies the normative completion contract. Otherwise emit `BLOCKED` with the exact remaining blocker, failed command/test or external evidence requirement.
