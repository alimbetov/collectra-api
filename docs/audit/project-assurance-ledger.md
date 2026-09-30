# Collectra Project Assurance Ledger

Baseline: `main@76e121082d3f339c8440fa2d9f34a092b3048345`
Audit branch: `audit/a01-a12-project-assurance`

## Rule

No capability, audit area, or defect is CLOSED without repository evidence and a verification test on an exact SHA.
Historical specs and READY labels are evidence inputs, not closure evidence.

## Audit program

| Area | Scope | Status |
|---|---|---|
| A01 | Product capability coverage | IN_PROGRESS |
| A02 | E2E business processes | NOT_STARTED |
| A03 | Architecture | NOT_STARTED |
| A04 | Domain/state machines | NOT_STARTED |
| A05 | Data integrity & database | NOT_STARTED |
| A06 | Concurrency/idempotency/consistency | NOT_STARTED |
| A07 | Tenant isolation & security | NOT_STARTED |
| A08 | Integration & resilience | NOT_STARTED |
| A09 | API/frontend contract | NOT_STARTED |
| A10 | Performance & scalability | NOT_STARTED |
| A11 | Observability/operations | NOT_STARTED |
| A12 | CI/CD/release readiness | NOT_STARTED |

## Defect ledger

| ID | Area | Severity | Finding | State | Closure evidence |
|---|---|---:|---|---|---|
| A01-001 | A01 | P1 TBD | Post-freeze frontend gates must be re-verified against current implementation; historical READY labels are insufficient proof of complete user capability. | VERIFYING | capability trace + tests |
| A01-002 | A01 | P2 TBD | Platform administration is explicitly outside tenant frontend MVP; verify whether current product scope requires platform operational UI before release. | VERIFYING | scope decision + implementation evidence |
| A01-003 | A01 | P2 TBD | Real provider capability must not be inferred from mock/simulation or pre-provider gates; verify externally usable channel capability separately. | VERIFYING | provider certification/evidence |

Severity remains TBD until impact is demonstrated. Findings may be CLOSED_AS_DESIGNED if current accepted product scope explicitly excludes them.

## Evidence states

- VERIFIED — implementation + executable verification evidence.
- PARTIAL — some layers exist but the complete user capability is not proven.
- SPEC_ONLY — documented but implementation not established.
- MISSING — required capability has no implementation.
- BROKEN — implementation exists but verification demonstrates failure.
- OUT_OF_SCOPE — explicitly excluded by accepted scope.
