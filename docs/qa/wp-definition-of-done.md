# Work Package Definition of Done

Status: NORMATIVE / MANDATORY FOR WP-01..WP-12

A work package progresses through:
`NOT_STARTED -> IMPLEMENTING -> CODE_COMPLETE -> TESTED -> VERIFIED`
or `BLOCKED`.

A WP is never VERIFIED merely because its new tests pass.

## Universal WP Definition of Done

For every WP, all applicable checks must be satisfied:

### Contract and scope
- [ ] accepted D01-D15 semantics preserved;
- [ ] linked FH/T30/P/J requirements identified;
- [ ] no unrelated redesign or VC-9 work introduced;
- [ ] any newly discovered semantic conflict recorded as D16+ and BLOCKED rather than guessed.

### Implementation
- [ ] backend changes complete where required;
- [ ] frontend changes complete where required;
- [ ] forward-only migration/seed changes complete where required;
- [ ] OpenAPI/DTO/client contract updated coherently where required;
- [ ] no duplicate/parallel workflow introduced when canonical implementation exists;
- [ ] observability/errors remain safe and useful.

### Security and tenant isolation
- [ ] server-side authorization enforced;
- [ ] least-privilege positive and negative cases covered;
- [ ] foreign path/query/body references covered where applicable;
- [ ] denied mutation proves zero state change;
- [ ] human/service/platform trust zones preserved;
- [ ] no secret/raw sensitive payload leakage.

### Correctness and concurrency
- [ ] domain invariants explicitly asserted;
- [ ] idempotency/replay behavior covered where applicable;
- [ ] optimistic/pessimistic/concurrent conflict behavior covered where applicable;
- [ ] terminal states are monotonic where applicable;
- [ ] authoritative backend state is not recreated in frontend.

### Tests
- [ ] reproducer exists for each fixed defect;
- [ ] focused positive regression passes;
- [ ] focused negative regression passes;
- [ ] affected existing regression suite passes;
- [ ] linked security/tenant suite passes;
- [ ] linked T30 scenario(s) pass;
- [ ] linked P/J journey evidence updated;
- [ ] no test was weakened solely to fit implementation.

### Full-stack behavior
- [ ] navigation/direct-route behavior coherent where frontend applies;
- [ ] 401/403/404/409 behavior verified where applicable;
- [ ] query/cache invalidation correct after mutations;
- [ ] persistence/API/UI projections agree on authoritative state;
- [ ] async/broker boundary verified at the correct layer where applicable.

### Evidence
- [ ] completion ledger rows updated;
- [ ] defect register state updated;
- [ ] exact test commands recorded;
- [ ] evidence SHA/run recorded;
- [ ] unresolved risks/blockers explicitly recorded.

## State transition criteria

### CODE_COMPLETE
All intended production/test code for the WP is committed, but verification may still be outstanding.

### TESTED
Focused tests have executed successfully on the current WP SHA. This does not imply linked journey/security/full-gate verification.

### VERIFIED
Universal DoD plus WP-specific acceptance criteria are satisfied with executed evidence on the current verification SHA. No unresolved P0/P1 introduced by the WP and no unapproved P2 required for its acceptance.

### BLOCKED
A genuine external/environmental blocker or new semantic D16+ conflict prevents completion. Record exact blocker, evidence and the smallest next action. Failing code/tests are not themselves a reason to stop as BLOCKED when they can be fixed in-repo.

## WP-specific mandatory additions

- WP-01: endpoint-permission ledger reconciled; no business-core ROLE_HUMAN-only orphan.
- WP-02: Collections placeholder removed; authoritative timeline/command behavior; URL state and 409 reconciliation proven.
- WP-03: primary tenant resource/reference matrix reconciled including nested parent/child and list-oracle cases.
- WP-04: balance equations survive replay and concurrency.
- WP-05: canonical row contents/counts proven, not only ingestion/import status.
- WP-06: cross-domain Customer 360 tabs obey their own READ permissions and request behavior is bounded.
- WP-07: can-reference vs can-administer separation and immutable published references proven.
- WP-08: ambiguous provider acceptance is durable and recovery-safe; provider call count remains one after ambiguous crash/recovery scenario.
- WP-09: Message/CampaignRun transitions have one semantic authority and stale-worker/recovery races are proven.
- WP-10: real RabbitMQ Testcontainers path uses production topology from outbox through listener/worker.
- WP-11: route inventory equals classified/covered route inventory; 401 and 403 semantics tested separately.
- WP-12: clean bootstrap, checkpointed Golden Journey, real PostgreSQL + RabbitMQ, deterministic adapter, final monitoring reconciliation.

## Final rule

Completing all WP rows is necessary but not sufficient for PRE-VC9_GATE_PASS. Phase G full gates, anti-omission reconciliation and exact-final-SHA CI remain mandatory.
