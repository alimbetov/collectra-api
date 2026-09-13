# FrontendWeb Start Gate

Status: PLANNED / BLOCKED UNTIL CORE GATE IS GREEN

## Decision

Frontend application will live in this repository as a separate top-level module:

```text
collectra-api/
├── frontendweb/
├── src/
├── docs/
├── scripts/
├── compose.yaml
└── pom.xml
```

`frontendweb/` is intentionally not created by this specification branch. It may be created only after the mandatory core blockers below are closed.

## Repository model

For the current product stage a monorepo-style layout is preferred because it gives:

- one review history for backend/API/frontend contract changes;
- atomic PRs when an API change and its frontend adaptation belong together;
- one CI entry point and one release trace;
- easier local environment orchestration;
- shared OpenAPI/client generation tooling;
- simpler onboarding while the product is still evolving rapidly.

Frontend remains runtime-decoupled from backend implementation. The supported contract is HTTP `/api/v1/**`, auth semantics, documented DTOs, pagination and stable error codes.

## Channel strategy before real integrations

Real provider integrations are NOT a prerequisite for frontend development.

Until their dedicated implementation phases, channel calls MUST use deterministic mock adapters for:

```text
EMAIL
SMS
WHATSAPP
TELEGRAM
IN_APP
```

Mocks MUST support scripted outcomes rather than random behavior:

```text
ACCEPTED
RETRYABLE_FAILURE
RATE_LIMITED
PERMANENT_FAILURE
TIMEOUT_BEFORE_ACCEPT
SLOW_SUCCESS
```

Where an ambiguous `ACCEPT_THEN_TIMEOUT` scenario is required for reliability testing, it must be represented explicitly by a test/fault scenario and must not be confused with a normal retryable failure.

Mock configuration MUST be unavailable/fail-closed in production profiles.

The frontend must consume provider-neutral message/delivery status and must not depend on KumoMTA/SMS/WhatsApp/Telegram-specific DTOs.

## Mandatory gate before creating frontendweb

All items below MUST be green.

### G1 — Test infrastructure cleanup

- RabbitMQ schedulers/workers OFF by default in generic tests;
- background processes explicit opt-in per test;
- deterministic `Clock`;
- deterministic scripted channel mocks;
- Testcontainers lifecycle stable;
- accidental repository artifacts removed;
- generic `mvn verify` repeatable without background race noise.

Evidence: `test-infrastructure-cleanup.md` Definition of Done.

### G2 — Slice 10A critical closure

The following mandatory areas must be green:

```text
P08 concurrency / idempotency
P11 security abuse / authorization
P02 parser / file abuse
P10 API / observability / log safety
```

Any remaining production-provider ambiguity may be moved to Slice 10B only when:

1. it is explicitly documented as a Slice 10B responsibility;
2. current mock behavior is deterministic;
3. it does not make frontend-visible state inconsistent;
4. it is not a security or tenant-isolation defect.

### G3 — Critical-core coverage gate

CI must enforce:

```text
LINE   >= 95%
BRANCH >= 90%
```

using the existing `slice10a-coverage` profile.

The gate must execute in CI, not exist only in `pom.xml`.

### G4 — Frontend API baseline freeze

Before frontend implementation begins:

- inventory current `/api/v1/**` endpoints;
- reconcile Slice 9 documentation with implemented code;
- confirm tenant scoping;
- confirm auth/login/refresh/logout/me flows;
- confirm pagination/sort/filter semantics;
- confirm `ProblemDetail` + stable `code` contract;
- identify screens whose API is COMPLETE/PARTIAL/BLOCKED;
- freeze currently implemented frontend contracts against accidental breaking changes.

Evidence: `frontend-api-baseline-freeze.md`.

## Explicit non-blockers for frontendweb

The following MUST NOT block creation of `frontendweb/` after G1-G4 are green:

```text
Slice 10B production RabbitMQ hardening
real KumoMTA deployment
real SMS provider
real Telegram provider
real WhatsApp provider
real In-App/Push provider
remote-provider reconciliation
production DNS/SPF/DKIM/DMARC
```

These are separate production-integration phases. Frontend uses mocks until the real adapters replace them behind the same provider-neutral contract.

## Initial frontendweb scope after gate

Recommended first implementation slices:

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

Do not begin with provider-specific configuration screens unless their API contract is already frozen.

## Definition of Done for the start gate

`frontendweb/` may be created when:

- test runtime is deterministic;
- critical Slice 10A scenario gates are green;
- 95/90 critical-core coverage runs in CI;
- frontend API baseline is documented and sufficiently stable;
- all channel provider calls needed by current frontend flows have deterministic mock implementations;
- no unresolved blocker can corrupt tenant isolation, auth, financial truth or frontend-visible workflow state.
