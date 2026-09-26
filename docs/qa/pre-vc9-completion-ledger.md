# Pre-VC9 Completion Ledger

Status: ACTIVE — MUST BE COMPLETED BY EXECUTION

This is the anti-omission evidence ledger required by `codex-pre-vc9-completion-contract.md`.

| Requirement | Source | Persona | Route/API | Permission | Tenant invariant | Test(s) | Result | Evidence SHA/run | Defect/Decision |
|---|---|---|---|---|---|---|---|---|---|
| T30-01..30 | top-30-smoke-contracts.md | mapped per scenario | mapped during implementation | mapped | Alpha/Beta where applicable | exact automated tests required | NOT_RUN | — | see T30/FH/D registers |
| J01..J10 | functional-hardening-master-plan.md | all | inventory | inventory | applicable | exact smoke mapping required | NOT_RUN | — | — |
| Route/controller/permission reconciliation | Wave A inventory | all | all primary | all | all tenant-owned | existing + missing tests | NOT_RUN | — | — |
| T30-30 Golden Journey | completion contract | cross-persona/system | full chain | least privilege | mandatory | PreVc9GoldenJourney smoke | NOT_RUN | — | D13 |
| Production-readiness gate | completion contract | system | all | all | mandatory | full CI/Testcontainers/OpenAPI/bootstrap | NOT_RUN | — | — |

Codex expands this ledger to concrete rows. A range row such as T30-01..30 is not sufficient for final completion: final ledger must have individual traceable entries or links to an equally explicit generated matrix.
