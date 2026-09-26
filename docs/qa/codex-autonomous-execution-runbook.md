# Codex Autonomous Execution Runbook

Status: NORMATIVE HANDOFF
Branch: spec/functional-hardening-user-journeys

## Start
Confirm branch/start SHA. Read AGENTS.md, accepted D01-D15, completion contract, full-stack consistency contract, T30, security matrix and P1-P15 frontend specs. Regenerate repository inventory before editing. Expand the ledger to individual D01-D15, P1-P15, J01-J10, T30-01..30 and primary surfaces.

## Loop
For completion phases A through G:
discover -> reproduce -> classify -> root cause -> coherent fix -> regression test -> focused run -> affected journey -> ledger/defect update -> commit.
Do not mix unrelated refactors into defect commits.

## Decisions
D01-D15 are accepted: implement them and do not stop for workshop approval. Only a newly discovered semantic conflict may create D16+ and BLOCKED. Technical choices are Codex responsibility.

## Full-stack change rule
Backend authorization change => permission catalog/seed/migration + backend guard + frontend gate + fixtures/tests.
API DTO/status/error change => OpenAPI + frontend contracts/adapters + tests in same change.
Lifecycle/concurrency change => mutation and read projection remain coherent.
New/finished route => direct load + deep link + permissions + loading/empty/error/conflict tests.

## Final verification
Use Java 17 and a package-compatible Node version (CI uses 22.22.2). At minimum:
mvn --batch-mode --no-transfer-progress spotless:check
mvn --batch-mode --no-transfer-progress clean verify -Pslice10a-coverage
real RabbitMQ Testcontainers broker smoke
frontendweb: npm ci; npm run typecheck; npm run test:ci; npm run build
OpenAPI compatibility
clean Liquibase/bootstrap
T30/persona/P1-P15/Golden Journey evidence.
If a dedicated smoke command/profile is added, add it to CI; no hidden local-only gate.

## CI
Push exact HEAD and wait for that SHA's required CI. A newer commit invalidates prior final-green evidence. On failure use raw job logs and fix root cause.

## Stop
Only PRE-VC9_GATE_PASS, or BLOCKED with every blocker, failed command/test, decision/defect ID and exact SHA.
