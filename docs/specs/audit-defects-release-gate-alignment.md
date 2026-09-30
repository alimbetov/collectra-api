# Audit Defects and Release-Gate Alignment — Implementation Specification

Documentation lifecycle: HISTORICAL — retained for traceability; not a current execution plan.
Current project status and precedence: [`docs/README.md`](../README.md).

Status: REVIEWED — ALIGNED WITH CURRENT PROJECT ASSURANCE PROCESS
Branch: `fix/audit-defects-and-release-gate-alignment`
Baseline: current branch created from `main@5d5bd4bf01b5bb938bff6a79987956abc252b41c`
Date: 2026-09-30

## 1. Objective

Eliminate confirmed audit/documentation drift discovered after the A01/A02 passes and prevent the same classes of defects from recurring.

This work is intentionally narrow. It does not implement the larger A01 product gaps (Payments UI, tenant RBAC UI, Profile/Sessions UI, tenant Analytics UI, Operations Console). Those remain separate product-closure work.

The branch must leave the repository with:

1. one truthful File Registry category contract across backend/frontend/tests;
2. regression evidence that selectable frontend file categories cannot silently diverge from the public backend contract;
3. one consistent mapping between A02 Golden/Failure Journey evidence and PC-18/PC-19/PC-20;
4. no stale release-gate baseline/branch wording presented as current truth;
5. exact-SHA evidence rules preserved: documentation must never promote a gate to VERIFIED from source inspection or an older CI run.

## 2. Confirmed findings

### DEF-01 — Files frontend/API category drift

Historical A01 evidence reported that the Files upload UI exposed `TEMPLATE_ASSET` and `ATTACHMENT`, while backend `FileCategory` accepted only:

`IMPORT_SOURCE | REPORT | EXPORT | ASSET | TEMP`.

Current branch inspection shows this runtime defect is already remediated:

- backend `FileCategory` contains exactly the five canonical values above;
- frontend `FILE_CATEGORIES` contains exactly the same values;
- `FileUploadPage` renders its options directly from `FILE_CATEGORIES`.

Therefore DEF-01 is **RESOLVED_IN_CURRENT_CODE**, not an open implementation defect.

Remaining defect class: there is no explicit cross-contract regression guard proving that future frontend selectable categories remain aligned with the backend/OpenAPI enum.

### DEF-02 — Pre-Channel Release Gate evidence drift

`docs/qa/pre-channel-release-gate.md` still declares historical branch/baseline metadata and records:

- PC-18 = IMPLEMENTED with wording that the smoke still needs strengthening;
- PC-19 = NOT_STARTED;
- PC-20 = NOT_STARTED.

Meanwhile A02 now defines a broader executable assurance model with G01-G18 and F01-F14, and subsequent implementation work has strengthened cross-boundary/outbox concurrency evidence.

The documents therefore describe overlapping assurance scopes using different freshness levels. This is a traceability defect: a reader cannot reliably determine which evidence is current without reconstructing repository history.

### DEF-03 — A01 stale Files finding

`docs/audit/a01-product-capability-coverage.md` still describes A01-005 as an active P1 Files category defect although current frontend/backend code is aligned.

A canonical audit document must distinguish:
- OPEN;
- REMEDIATED, evidence pending;
- VERIFIED on exact SHA;
- historical finding.

Leaving a remediated defect described as currently broken is misleading.

## 3. Required remediation

### WP-01 — File category contract hardening

#### Functional invariant

Every category selectable by the general Files upload UI MUST be accepted by the public backend File Registry upload contract.

Canonical values at this baseline:

`IMPORT_SOURCE | REPORT | EXPORT | ASSET | TEMP`.

The general registry MUST NOT expose `TEMPLATE_ASSET` or `ATTACHMENT` unless the backend public contract is intentionally extended in the same change.

#### Implementation requirements

1. Preserve `FILE_CATEGORIES` as the single frontend source for upload category choices.
2. Do not duplicate a second hard-coded category list in `FileUploadPage`.
3. Add regression coverage for the rendered/selectable upload categories.
4. Add or strengthen backend/OpenAPI contract coverage for `FileCategory`.
5. Prefer generated/shared OpenAPI-derived contract validation if the current test architecture supports it without introducing a new build system; otherwise use explicit contract assertions on both sides and document why.
6. A future enum addition/removal must fail a relevant contract test unless frontend/backend are updated coherently.

#### Acceptance

- upload page renders exactly the canonical categories;
- no stale `TEMPLATE_ASSET`/`ATTACHMENT` general-registry option exists;
- frontend tests green;
- backend/OpenAPI contract test green;
- production frontend build green.

### WP-02 — A01 finding reconciliation

Update A01-005 without erasing history.

Required representation:

- record the original mismatch as a historical finding;
- state that current code is aligned;
- identify executable regression evidence;
- keep A01 terminology from `project-assurance-ledger.md`: before complete executable proof, the capability remains `PARTIAL` (or `BROKEN` only if verification demonstrates failure);
- close/update the defect-ledger row only with repository evidence and verification on an exact SHA; do not introduce a new A01 status vocabulary.

Do not rewrite unrelated A01 findings in this work package.

### WP-03 — A02 ↔ Pre-Channel Gate traceability

Create an explicit mapping from release-gate rows to A02 evidence.

At minimum:

- PC-18 maps to the positive Golden Journey G01-G18 subsets needed for the provider-neutral business chain;
- PC-19 maps to the applicable F01-F14 negative/failure scenarios plus its explicit paid/inactive/no-contact/attachment/foreign-tenant/duplicate-side-effect assertions;
- PC-20 is not a business test; it is the exact-SHA aggregation gate proving all applicable evidence is green together.

The mapping must make clear that A02 and PC ledgers are not independent competing truth sources:
- A02 describes cross-boundary assurance scenarios;
- Pre-Channel Release Gate is the release qualification ledger consuming executable evidence.

### WP-04 — Release-gate metadata and state reconciliation

Update `docs/qa/pre-channel-release-gate.md` to remove stale current-state claims.

Requirements:

1. Replace historical branch metadata with a current/candidate evidence model.
2. Record the exact audited candidate SHA when known.
3. Never mark PC-18/19/20 VERIFIED solely because tests exist.
4. Determine PC-18 and PC-19 state from current executable evidence:
   - `IMPLEMENTED`: required executable scenario exists but has not passed the candidate exact-SHA gate;
   - `TESTED`: scenario has passed, but not as part of the complete same-SHA release qualification;
   - `VERIFIED`: all required same-SHA release evidence satisfies the gate rules.
5. PC-20 remains NOT_STARTED/IMPLEMENTED until one exact candidate SHA has formatting, compile/unit, PostgreSQL Testcontainers, frontend and RabbitMQ/golden evidence green together.
6. Preserve the external-provider boundary: this gate does not certify live KumoMTA/SMS/Telegram/WhatsApp delivery.

### WP-05 — Documentation consistency check

Audit the canonical documents for stale references to the old PC-18/PC-19 state or superseded baselines:

- `docs/audit/a01-product-capability-coverage.md`
- `docs/audit/a02-golden-journey.md`
- `docs/qa/pre-channel-release-gate.md`
- `docs/architecture/technical-audit-ledger.md`
- `docs/architecture/collectra-system-overview.md`

Only update statements contradicted by current code/evidence. Historical specs remain historical and must not be mass-rewritten.

## 4. Test matrix

| ID | Verification | Required result |
|---|---|---|
| T01 | frontend FileUploadPage category test | exact canonical options |
| T02 | frontend typecheck | PASS |
| T03 | frontend test suite | PASS |
| T04 | frontend production build | PASS |
| T05 | backend FileCategory/OpenAPI contract assertion | PASS |
| T06 | Spotless/check formatting | PASS |
| T07 | focused A02/outbox/Golden Journey evidence | PASS |
| T08 | PostgreSQL Testcontainers verify | PASS |
| T09 | RabbitMQ Golden Journey boundary | PASS |
| T10 | documentation state/evidence review | no unsupported VERIFIED state |
| T11 | exact-SHA full CI | SUCCESS before final release-gate verification |

## 5. Governance and evidence-state rules

This work MUST NOT introduce a fourth repository-wide status vocabulary. Each canonical ledger keeps its existing semantics:

- **A01 capability evidence** (`project-assurance-ledger.md`): `VERIFIED | PARTIAL | SPEC_ONLY | MISSING | BROKEN | OUT_OF_SCOPE`.
- **Technical audit** (`technical-audit-ledger.md`): `IMPLEMENTED | PARTIAL | AUDIT_REQUIRED | RELEASE_BLOCKED | VERIFIED`.
- **Pre-Channel Release Gate** (`pre-channel-release-gate.md`): `NOT_STARTED | IMPLEMENTED | TESTED | VERIFIED | BLOCKED`.
- **Defect ledger rows** retain their existing defect state conventions rather than borrowing release-gate states.

Cross-ledger rule: `VERIFIED` always requires the executable evidence demanded by that ledger on the exact candidate SHA. Test existence, source inspection, documentation changes, or an older green run are insufficient.

A later code commit invalidates final release-candidate `VERIFIED` evidence until the applicable gates are rerun.
## 6. Non-goals

This branch does not implement:

- Payments/Allocation/Reversal tenant UI;
- tenant membership/role administration UI;
- Profile/Security/Sessions UI;
- tenant Analytics/Reporting UI;
- consolidated Operations/Diagnostics UI;
- production Kubernetes/Helm/IaC;
- DR/backup/load certification;
- live external-provider certification.

If audit of this branch discovers a P0/P1 defect directly caused by WP-01..WP-05, record it and fix it here. Unrelated product gaps must be recorded for the subsequent product-closure branch rather than expanding this branch indefinitely.

## 7. Execution order

1. Establish branch baseline from current `main`; inspect CI/evidence and preserve superseded baseline history.
2. Implement WP-01 regression protection without rewriting the already-correct enum contract.
3. Run focused frontend/backend contract tests.
4. Reconcile A01-005 using the existing A01/defect-ledger vocabulary.
5. Map A02 evidence to PC-18/PC-19/PC-20.
6. Reconcile canonical release-gate documentation.
7. Run the same executable gates used by `.github/workflows/ci.yml`: `spotless:check`, `scripts/test-slice-07.sh unit`, `mvn clean verify -Pslice10a-coverage`, frontend `typecheck`, `test:ci`, and production `build`.
8. Confirm whether RabbitMQ Golden Journey evidence is actually executed by the Maven verification path; if separate execution is required, name and execute that gate explicitly.
9. Push candidate SHA and require both CI jobs (`verify` and `frontend`) to succeed on that exact SHA.
10. Reconcile A01/A02/PC evidence against that run. Only then promote eligible rows to `VERIFIED` and record SHA/run.
## 8. Definition of Done

This specification is complete only when:

- Files category drift cannot silently recur without failing automated evidence;
- A01 no longer reports the already-remediated Files mismatch as an active P1 defect;
- A02 and Pre-Channel Release Gate have explicit traceability;
- PC-18/PC-19/PC-20 states describe current executable reality;
- no documentation claims VERIFIED without same-SHA evidence;
- all code changes have regression tests;
- both repository CI jobs are green on one recorded exact SHA;
- RabbitMQ evidence is explicitly tied either to the standard Maven verification path or to a named separate executable gate.



## 9. Project-process alignment constraints

1. Reuse the existing shared outbox, tests, A01/A02 ledgers and PC gate; do not create a parallel assurance subsystem.
2. Historical READY labels, test presence and source inspection are evidence inputs only.
3. Fix confirmed findings with regression evidence; do not change already-correct runtime code merely to satisfy stale documentation.
4. Preserve PostgreSQL-owned concurrency/idempotency invariants and RabbitMQ at-least-once semantics.
5. Preserve the explicit provider boundary: pre-channel qualification is not live-provider certification.
6. Preserve tenant-scoped lookup and non-disclosing denial semantics in any new integration evidence.
7. Current code plus the newest canonical contract wins; historical specifications remain traceability artifacts.
8. Keep branch scope bounded; unrelated A01/A03-A12 findings belong in their appropriate ledger/backlog.
9. Exact-SHA loop is authoritative: failure -> raw evidence -> root cause -> fix -> new SHA -> rerun.

## 10. Review result

The specification is aligned with the current project assurance process and is implementation-ready subject to exact-SHA verification.

The review corrected one specification defect: the proposed global `REMEDIATED_PENDING_EXACT_SHA` state was removed because it conflicted with established per-ledger state vocabularies.

The Files mismatch is a historical/remediated finding on the inspected baseline; implementation scope is regression protection plus A01 evidence reconciliation, not another enum rewrite.

The Pre-Channel scope is evidence/governance reconciliation plus any genuinely missing PC-18/PC-19 executable scenarios. PC-20 remains the final same-SHA qualification gate and cannot be satisfied by documentation changes alone.
