# Post-Slice 10A Hardening Roadmap

Status: MASTER PLAN

## Goal

Зафиксировать порядок доработок после текущего communication/frontend baseline и не блокировать frontend-разработку reliability-задачами delivery contour.

## Parallel tracks

```text
TRACK A — Frontend readiness
  A0 Frontend API baseline freeze
  A1 React/admin implementation can proceed against frozen /api/v1

TRACK B — Delivery production hardening
  B0 Test infrastructure cleanup
  B1 Slice 10A final closure
  B2 CI/coverage merge gate hardening
  B3 Documentation reconciliation
  B4 Slice 10B RabbitMQ production hardening
  B5 Real KumoMTA environment + reconciliation
  B6 Real SMS
  B7 Real Telegram
  B8 Real WhatsApp
  B9 In-App/Push
```

Frontend development MUST NOT wait for B4-B9 unless a specific screen depends on a real provider.

## Phase A0 — Frontend API baseline

Spec: `frontend-api-baseline-freeze.md`

Result:

- inventory of all `/api/v1/**` capabilities;
- stable DTO/error/pagination contract;
- explicit frontend readiness matrix;
- OpenAPI compatibility gate.

## Phase B0 — Test infrastructure cleanup

Spec: `test-infrastructure-cleanup.md`

Mandatory first step before final Slice 10A closure:

- RabbitMQ schedulers OFF in generic tests;
- background workers explicit opt-in;
- deterministic `Clock`;
- scripted providers instead of random acceptance gates;
- Testcontainers lifecycle cleanup;
- remove accidental repository artifacts.

## Phase B1 — Slice 10A final closure

Spec: `slice-10a-final-closure.md`

Closure order:

```text
P08 concurrency/idempotency/ambiguous outcome
P11 security abuse
P02 parser/file abuse
P10 API/observability/log safety
coverage gate 95/90
full integration suite
```

Do not mark completed while mandatory RED/BLOCKED rows remain.

## Phase B2 — CI gate

CI MUST run:

```bash
mvn --batch-mode --no-transfer-progress clean verify -Pslice10a-coverage
```

Required repository policy target:

- PR before main;
- required CI status;
- no merge on red coverage/integration suite;
- no automatic merge unless explicitly approved.

## Phase B3 — Documentation reconciliation

After implementation evidence is green:

- update Slice 9 from stale `READY FOR IMPLEMENTATION` to actual implemented/frozen state;
- close/update Slice 10A coverage audit;
- update specs index;
- document resolved ambiguous-delivery decision;
- record residual risks.

## Phase B4 — Slice 10B

Spec: `slice-10b-rabbitmq-production-hardening.md`

Result:

- publisher confirms;
- manual ack/prefetch;
- retry/DLX/DLQ/parking flow;
- crash/restart/redelivery matrix;
- graceful shutdown;
- queue/DLQ alerts;
- explicit ambiguous-delivery production design.

## Phase B5 — Real KumoMTA

Spec: `real-kumomta-environment-acceptance.md`

Result must prove:

```text
Collectra -> Outbox -> RabbitMQ -> Worker -> KumoMTA -> remote SMTP -> delivery/bounce -> reconciliation
```

HTTP inject acceptance alone is insufficient.

## Phases B6-B9 — Real channels

Spec: `real-channel-adapters-roadmap.md`

Recommended order:

```text
SMS -> Telegram -> WhatsApp -> In-App/Push
```

All adapters reuse provider-neutral Message state machine and shared outcome taxonomy.

## Global Definition of Done

The roadmap is considered completed when:

- frontend can operate against stable `/api/v1`;
- Slice 10A mandatory scenario and coverage gates are green;
- RabbitMQ contour survives restart/redelivery/failure scenarios;
- real email delivery and reconciliation work through KumoMTA;
- SMS, Telegram and WhatsApp have production adapters and real acceptance evidence;
- metrics, alerts, logs and security controls remain bounded and tenant-safe.
