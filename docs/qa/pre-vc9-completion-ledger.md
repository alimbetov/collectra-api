# Pre-VC9 Completion Ledger

Status: ACTIVE — FAIL-CLOSED EXECUTION LEDGER

This ledger is required by `codex-pre-vc9-completion-contract.md`. The rows below are deliberately pre-expanded so execution cannot redefine the minimum coverage set.

Allowed execution states:
`NOT_STARTED | IMPLEMENTING | CODE_COMPLETE | TESTED | VERIFIED | BLOCKED`.

Rules:
- Never delete a required row to make completion easier.
- Split a row into more concrete rows when one requirement spans materially different routes/resources; retain traceability to the parent ID.
- VERIFIED requires executed evidence on the current verification SHA and the applicable WP Definition of Done.
- CODE_COMPLETE or TESTED is not sufficient for PRE-VC9_GATE_PASS.
- If a requirement is genuinely not applicable because of an accepted normative decision, keep the row and record the decision/reason; do not silently omit it.

| Requirement | Source | Persona | Route/API | Permission | Tenant invariant | Test(s) | Result | Evidence SHA/run | Defect/Decision |
|---|---|---|---|---|---|---|---|---|---|
| WP-01 | remediation spec + WP review | map during execution | map concrete route/API | map exact authority/scope | map invariant | exact focused + negative tests | NOT_STARTED | — | map FH/D/T30/P/J |
| WP-02 | remediation spec + WP review | map during execution | map concrete route/API | map exact authority/scope | map invariant | exact focused + negative tests | NOT_STARTED | — | map FH/D/T30/P/J |
| WP-03 | remediation spec + WP review | map during execution | map concrete route/API | map exact authority/scope | map invariant | exact focused + negative tests | NOT_STARTED | — | map FH/D/T30/P/J |
| WP-04 | remediation spec + WP review | map during execution | map concrete route/API | map exact authority/scope | map invariant | exact focused + negative tests | NOT_STARTED | — | map FH/D/T30/P/J |
| WP-05 | remediation spec + WP review | map during execution | map concrete route/API | map exact authority/scope | map invariant | exact focused + negative tests | NOT_STARTED | — | map FH/D/T30/P/J |
| WP-06 | remediation spec + WP review | map during execution | map concrete route/API | map exact authority/scope | map invariant | exact focused + negative tests | NOT_STARTED | — | map FH/D/T30/P/J |
| WP-07 | remediation spec + WP review | map during execution | map concrete route/API | map exact authority/scope | map invariant | exact focused + negative tests | NOT_STARTED | — | map FH/D/T30/P/J |
| WP-08 | remediation spec + WP review | map during execution | map concrete route/API | map exact authority/scope | map invariant | exact focused + negative tests | NOT_STARTED | — | map FH/D/T30/P/J |
| WP-09 | remediation spec + WP review | map during execution | map concrete route/API | map exact authority/scope | map invariant | exact focused + negative tests | NOT_STARTED | — | map FH/D/T30/P/J |
| WP-10 | remediation spec + WP review | map during execution | map concrete route/API | map exact authority/scope | map invariant | exact focused + negative tests | NOT_STARTED | — | map FH/D/T30/P/J |
| WP-11 | remediation spec + WP review | map during execution | map concrete route/API | map exact authority/scope | map invariant | exact focused + negative tests | NOT_STARTED | — | map FH/D/T30/P/J |
| WP-12 | remediation spec + WP review | map during execution | map concrete route/API | map exact authority/scope | map invariant | exact focused + negative tests | NOT_STARTED | — | map FH/D/T30/P/J |
| T30-01 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-02 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-03 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-04 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-05 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-06 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-07 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-08 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-09 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-10 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-11 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-12 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-13 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-14 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-15 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-16 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-17 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-18 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-19 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-20 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-21 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-22 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-23 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-24 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-25 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-26 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-27 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-28 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-29 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| T30-30 | top-30-smoke-contracts.md | per contract | map concrete route/API | map | Alpha/Beta where applicable | exact automated test | NOT_STARTED | — | map FH/D/WP |
| P1 | full-stack consistency / frontend processes | map persona(s) | map journey surfaces | least privilege | applicable | exact process evidence | NOT_STARTED | — | map WP/T30/D |
| P2 | full-stack consistency / frontend processes | map persona(s) | map journey surfaces | least privilege | applicable | exact process evidence | NOT_STARTED | — | map WP/T30/D |
| P3 | full-stack consistency / frontend processes | map persona(s) | map journey surfaces | least privilege | applicable | exact process evidence | NOT_STARTED | — | map WP/T30/D |
| P4 | full-stack consistency / frontend processes | map persona(s) | map journey surfaces | least privilege | applicable | exact process evidence | NOT_STARTED | — | map WP/T30/D |
| P5 | full-stack consistency / frontend processes | map persona(s) | map journey surfaces | least privilege | applicable | exact process evidence | NOT_STARTED | — | map WP/T30/D |
| P6 | full-stack consistency / frontend processes | map persona(s) | map journey surfaces | least privilege | applicable | exact process evidence | NOT_STARTED | — | map WP/T30/D |
| P7 | full-stack consistency / frontend processes | map persona(s) | map journey surfaces | least privilege | applicable | exact process evidence | NOT_STARTED | — | map WP/T30/D |
| P8 | full-stack consistency / frontend processes | map persona(s) | map journey surfaces | least privilege | applicable | exact process evidence | NOT_STARTED | — | map WP/T30/D |
| P9 | full-stack consistency / frontend processes | map persona(s) | map journey surfaces | least privilege | applicable | exact process evidence | NOT_STARTED | — | map WP/T30/D |
| P10 | full-stack consistency / frontend processes | map persona(s) | map journey surfaces | least privilege | applicable | exact process evidence | NOT_STARTED | — | map WP/T30/D |
| P11 | full-stack consistency / frontend processes | map persona(s) | map journey surfaces | least privilege | applicable | exact process evidence | NOT_STARTED | — | map WP/T30/D |
| P12 | full-stack consistency / frontend processes | map persona(s) | map journey surfaces | least privilege | applicable | exact process evidence | NOT_STARTED | — | map WP/T30/D |
| P13 | full-stack consistency / frontend processes | map persona(s) | map journey surfaces | least privilege | applicable | exact process evidence | NOT_STARTED | — | map WP/T30/D |
| P14 | full-stack consistency / frontend processes | map persona(s) | map journey surfaces | least privilege | applicable | exact process evidence | NOT_STARTED | — | map WP/T30/D |
| P15 | full-stack consistency / frontend processes | map persona(s) | map journey surfaces | least privilege | applicable | exact process evidence | NOT_STARTED | — | map WP/T30/D |
| J01 | functional-hardening-master-plan.md | named persona | map journey surfaces | least privilege | applicable | exact journey evidence | NOT_STARTED | — | map WP/T30/P |
| J02 | functional-hardening-master-plan.md | named persona | map journey surfaces | least privilege | applicable | exact journey evidence | NOT_STARTED | — | map WP/T30/P |
| J03 | functional-hardening-master-plan.md | named persona | map journey surfaces | least privilege | applicable | exact journey evidence | NOT_STARTED | — | map WP/T30/P |
| J04 | functional-hardening-master-plan.md | named persona | map journey surfaces | least privilege | applicable | exact journey evidence | NOT_STARTED | — | map WP/T30/P |
| J05 | functional-hardening-master-plan.md | named persona | map journey surfaces | least privilege | applicable | exact journey evidence | NOT_STARTED | — | map WP/T30/P |
| J06 | functional-hardening-master-plan.md | named persona | map journey surfaces | least privilege | applicable | exact journey evidence | NOT_STARTED | — | map WP/T30/P |
| J07 | functional-hardening-master-plan.md | named persona | map journey surfaces | least privilege | applicable | exact journey evidence | NOT_STARTED | — | map WP/T30/P |
| J08 | functional-hardening-master-plan.md | named persona | map journey surfaces | least privilege | applicable | exact journey evidence | NOT_STARTED | — | map WP/T30/P |
| J09 | functional-hardening-master-plan.md | named persona | map journey surfaces | least privilege | applicable | exact journey evidence | NOT_STARTED | — | map WP/T30/P |
| J10 | functional-hardening-master-plan.md | named persona | map journey surfaces | least privilege | applicable | exact journey evidence | NOT_STARTED | — | map WP/T30/P |
| Route/controller/permission/resource reconciliation | completion contract | all | all primary surfaces | all | all tenant-owned | generated inventory + mapped tests | NOT_STARTED | — | anti-omission |
| Real RabbitMQ production-topology smoke | WP-10 | system | outbox -> AMQP -> listener -> worker | service/system | tenant preserved | RabbitMQ Testcontainers smoke | NOT_STARTED | — | T30-27/T30-30 |
| Clean bootstrap/Liquibase | production gate | system | startup | n/a | n/a | clean DB bootstrap | NOT_STARTED | — | WP-12 |
| OpenAPI compatibility | production gate | clients/frontend | public APIs | n/a | n/a | compatibility gate | NOT_STARTED | — | all API WPs |
| Frontend typecheck/test/build | production gate | human personas | all frontend | all | applicable | npm ci/typecheck/test:ci/build | NOT_STARTED | — | WP-02/WP-06/WP-11 |
| Backend full verify/coverage | production gate | system | all | all | all | clean verify + integration/Testcontainers | NOT_STARTED | — | all WPs |
| T30-30 Golden Journey final checkpoint | completion contract | cross-persona/system | full chain | least privilege | Alpha/Beta probes | checkpointed Golden Journey | NOT_STARTED | — | D13/WP-12 |
| Final exact-SHA CI | completion contract | system | all | all | mandatory | repository CI on final HEAD | NOT_STARTED | — | final gate |

Final completion requires every required row to be VERIFIED or explicitly retained with an accepted non-applicable/deferred decision that is permitted by the higher-priority contract. A range summary cannot replace these rows.
