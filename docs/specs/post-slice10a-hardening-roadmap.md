# Post-Slice 10A Hardening Roadmap

Status: MASTER PLAN

## Goal

Зафиксировать порядок доработок после текущего communication/frontend baseline, закрыть core-блокеры перед созданием `frontendweb/` и не делать реальные provider integrations обязательным условием frontend-разработки.

## Target repository layout

После прохождения frontend start gate:

```text
collectra-api/
├── frontendweb/
├── src/
├── docs/
├── scripts/
├── compose.yaml
└── pom.xml
```

Frontend runtime-contract remains HTTP `/api/v1/**`; frontend не зависит от Java implementation classes или persistence model.

## Execution tracks

```text
TRACK A — Core closure before frontendweb
  A0 Test infrastructure cleanup
  A1 Slice 10A critical closure
  A2 CI 95/90 critical-core coverage gate
  A3 Frontend API baseline freeze
  A4 Documentation reconciliation
  A5 CREATE frontendweb/

TRACK B — Frontend implementation
  B0 project shell/tooling
  B1 auth/session
  B2 navigation/permissions
  B3 customers/segments
  B4 contracts
  B5 receivables/payments
  B6 collection
  B7 campaigns/messages
  B8 imports/templates/files/documents
  B9 dashboard

TRACK C — Production delivery hardening/integrations
  C0 Slice 10B RabbitMQ production hardening
  C1 Real KumoMTA environment + reconciliation
  C2 Real SMS
  C3 Real Telegram
  C4 Real WhatsApp
  C5 In-App/Push
```

Tracks B and C may proceed in parallel after A0-A4 are green.

## Channel strategy until real providers

Real channel integrations do NOT block frontend development.

Until C1-C5, all channels use deterministic provider-neutral mocks:

```text
EMAIL
SMS
WHATSAPP
TELEGRAM
IN_APP
```

Required scripted mock outcomes:

```text
ACCEPTED
RETRYABLE_FAILURE
RATE_LIMITED
PERMANENT_FAILURE
TIMEOUT_BEFORE_ACCEPT
SLOW_SUCCESS
```

`ACCEPT_THEN_TIMEOUT` is an explicit reliability fault scenario, not a generic retryable response.

Random 80/20 behavior MUST NOT be used for CI or deterministic integration tests. Random/stochastic behavior may exist only in an explicitly named local demo profile and must never be the authoritative test oracle.

Mocks MUST fail closed or be unavailable in production profile.

## Phase A0 — Test infrastructure cleanup

Spec: `test-infrastructure-cleanup.md`

Mandatory first step:

- RabbitMQ schedulers OFF in generic tests;
- background workers explicit opt-in;
- deterministic `Clock`;
- deterministic scripted providers;
- Testcontainers lifecycle cleanup;
- remove accidental repository artifacts;
- repeatable generic `mvn verify`.

## Phase A1 — Slice 10A critical closure

Spec: `slice-10a-final-closure.md`

Closure priority:

```text
P08 concurrency/idempotency
P11 security abuse
P02 parser/file abuse
P10 API/observability/log safety
```

Production-provider concerns may move to Slice 10B only when they are explicitly documented and do not leave a security, tenant-isolation, financial-truth or frontend-state correctness blocker.

## Phase A2 — CI coverage gate

CI MUST run the existing critical-core profile:

```bash
mvn --batch-mode --no-transfer-progress clean verify -Pslice10a-coverage
```

Mandatory thresholds:

```text
LINE   >= 95%
BRANCH >= 90%
```

The profile existing only in `pom.xml` is insufficient; it must be an enforced CI gate.

Target repository policy:

- PR before main;
- required CI status;
- no merge on red coverage/integration suite;
- no automatic merge unless explicitly approved.

## Phase A3 — Frontend API baseline

Specs:

- `frontend-api-baseline-freeze.md`
- `frontendweb-start-gate.md`

Result:

- inventory current `/api/v1/**` capabilities;
- auth/session contract confirmed;
- stable DTO/error/pagination/filter semantics;
- tenant isolation confirmed;
- screen readiness matrix COMPLETE/PARTIAL/BLOCKED;
- OpenAPI compatibility strategy;
- no accidental breaking changes after frontend implementation starts.

## Phase A4 — Documentation reconciliation

Before creating `frontendweb/`:

- update stale Slice 9 status to match implemented code;
- close/update Slice 10A coverage audit;
- update specs index;
- record deferred Slice 10B provider-delivery risks;
- record residual non-blocking limitations.

## Phase A5 — Create frontendweb

Only after A0-A4 are green create the top-level `frontendweb/` module.

Initial implementation order:

```text
FW0 project shell/tooling
FW1 authentication/session
FW2 application shell/navigation/permissions
FW3 customers + contacts + segments
FW4 contracts
FW5 invoices + receivables
FW6 payments + allocations
FW7 collection cases / promises / disputes / actions
FW8 campaigns
FW9 messages / delivery monitoring
FW10 imports/templates/files/documents
FW11 dashboard
```

Provider-specific screens should be postponed until their contracts are frozen. Generic channel/status UX may use mocks.

## Phase C0 — Slice 10B

Spec: `slice-10b-rabbitmq-production-hardening.md`

This is production hardening, not a blocker for frontend start after A0-A4.

Result:

- publisher confirms;
- manual ack/prefetch;
- retry/DLX/DLQ/parking flow;
- crash/restart/redelivery matrix;
- graceful shutdown;
- queue/DLQ alerts;
- explicit ambiguous-delivery production design.

## Phase C1 — Real KumoMTA

Spec: `real-kumomta-environment-acceptance.md`

Target evidence:

```text
Collectra -> Outbox -> RabbitMQ -> Worker -> KumoMTA -> remote SMTP -> delivery/bounce -> reconciliation
```

Before this phase EMAIL frontend flows continue using deterministic mock delivery.

## Phases C2-C5 — Real channels

Spec: `real-channel-adapters-roadmap.md`

Recommended order:

```text
SMS -> Telegram -> WhatsApp -> In-App/Push
```

All real adapters replace mock implementations behind the same provider-neutral `DeliveryGateway`/outcome contract. Frontend should not require channel-specific state-machine rewrites when this switch happens.

## Frontend start gate

`frontendweb/` MUST NOT be created until:

```text
[ ] test infrastructure deterministic
[ ] Slice 10A critical security/concurrency/parser/observability blockers closed
[ ] LINE >=95% / BRANCH >=90% enforced by CI for critical core
[ ] /api/v1 frontend baseline audited/frozen
[ ] Slice 9/10A documentation reconciled with main
[ ] deterministic mocks exist for channel flows used by frontend
```

The following are explicitly NOT frontend-start blockers:

```text
Slice 10B completion
real KumoMTA
real SMS
real Telegram
real WhatsApp
real In-App/Push
provider DNS/reputation/reconciliation infrastructure
```

## Global Definition of Done

The broader roadmap is complete when:

- `frontendweb/` operates against stable `/api/v1`;
- critical Slice 10A scenario/coverage gates remain green;
- RabbitMQ contour survives restart/redelivery/failure scenarios;
- real email delivery/reconciliation works through KumoMTA;
- subsequent real channels replace mocks without changing frontend business contracts;
- metrics, alerts, logs and security controls remain bounded and tenant-safe.
