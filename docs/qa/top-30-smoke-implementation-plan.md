# Top-30 Smoke Implementation Plan

## Phase 0 — decisions that gate semantics
Resolve D01/D02/D03/D05/D06/D09/D10/D12/D13. D04 can remain outside Golden Journey if navigation is honestly deferred. D08 is validated against existing capability model. D15 ambiguous-provider behavior requires an explicit implementation-state review before changing code.

## Phase 1 — security spine
Implement/expand T30-01..05, 18, 21, 28 with reusable Alpha/Beta fixtures. Parameterize resource access. Every denied mutation verifies zero state change.

## Phase 2 — financial + collections truth
Implement T30-07..10 and 16. Reuse CustomerReceivableCore and Collection fixtures. Add browser T30-06 only after Collections workspace implementation contract is accepted.

## Phase 3 — ingestion spine
Implement T30-11..14 using real service authentication, IntegrationSource, schema/mapping and durable operation outcomes. Do not bypass API security by calling services directly in the smoke layer.

## Phase 4 — campaign/content/delivery
Implement T30-17..27. Reuse deterministic Clock/gateway/fault fixtures. Keep existing 100-duplicate concurrency regression. Test exact provider-call counts.

## Phase 5 — frontend product coherence
Implement T30-06/10/15/16/20/28/29 at page/browser level. Frontend tests prove wiring; backend tests remain authoritative for authorization and business invariants.

## Phase 6 — composite gate
T30-30 runs on clean PostgreSQL + RabbitMQ and crosses real public/service APIs wherever practical. No real external provider network. Run affected regressions plus frontend typecheck/test/build, backend formatting/verify, OpenAPI and clean bootstrap on exact SHA.

## Anti-patterns forbidden
- one monolithic test with 200 assertions and no diagnostic boundaries;
- mocks for tenant/auth where real security filters are the subject;
- direct repository setup for the entire Golden Journey when public setup APIs exist;
- sleep-based async assertions;
- changing expected status merely to match current implementation;
- test-only production backdoors;
- shared Alpha/Beta IDs or credentials;
- PASS inferred from old CI/test names.
