# Collectra Business Platform + Shared IAM Platform — Operating and Integration Guide

Status: ARCHITECTURE / INTEGRATION BASELINE; NOT A PRODUCTION ACCEPTANCE CERTIFICATE  
Date: 2026-11-15  
Working branch: `feat/iam-shared-platform`  
Future consumer: ZAKAZ (QRM), **not yet integrated**

## 1. Purpose and products

This guide separates two products with independent ownership, deployment, readiness and release gates.

**Collectra Business Platform** is a commercial multi-tenant receivables and collections application. It owns customers, contacts, contracts, invoices, payments, allocations/reversals, collection cases, promises, disputes, campaigns, templates, documents, messages, analytics, tenant memberships, business roles and permissions.

**Shared IAM Platform (App Common)** is the reusable infrastructure for applications owned by the same organization. Target ownership includes Keycloak orchestration, a shared global end-user identity, OIDC/SSO configuration, application registry and OAuth clients, global credential lifecycle, MFA, recovery, security events, cross-application account blocking propagation, platform operations and app-scoped notification transport. Keycloak—not App Common—is the credential and JWT issuer.

**Deployment reality:** App Common's independent repository, CI/CD, database and deployment are *target architecture*; the current shared-platform implementation/specifications are coordinated in `collectra-api`. Do not infer extraction has happened merely because ADRs are accepted. The products must eventually build, deploy, roll back and be monitored independently.

## 2. Authoritative architecture decisions

Read in precedence order:
1. `docs/adr/adr-iam-008-collectra-only-business-tenancy.md` — only Collectra is required to have business tenancy; other applications define their own authorization.
2. `docs/adr/adr-iam-007-unified-realm-global-identity-sso.md` — one shared **end-user** Keycloak realm, stable `(iss, sub)`, SSO across connected apps; app-specific OAuth audiences and local business identities.
3. `docs/adr/adr-iam-005-keycloak-jwt-bearer-contract.md` — Keycloak JWT trust/validation.
4. `docs/adr/adr-iam-004-app-common-platform-boundary.md` — independent product boundary and local business ownership, **except** its superseded per-application-realm/no-SSO provisions.
5. `docs/adr/adr-iam-006-internal-app-common-control-plane.md` — internal platform/operator boundary, **except** any superseded realm model.
6. `docs/architecture/collectra-system-overview.md` — Collectra business and execution architecture.

Older ADR-IAM-003, older IAM-R1.x, and App Common R0 descriptions of isolated end-user realms are historical where inconsistent with ADR-IAM-007/008. Do not provision a new end-user realm for ZAKAZ. App Common privileged operators may use a separately isolated **admin realm**.

## 3. Responsibility and data ownership

| Concern | App Common / Keycloak | Collectra | Future ZAKAZ |
| --- | --- | --- | --- |
| Global user identity, password, MFA, SSO | Keycloak authority; App Common configuration | OIDC consumer | OIDC consumer |
| OAuth client registry, issuer, allowed audiences/scopes | App Common | Validates own tokens | Validates own tokens |
| Global account suspension / security events | App Common control plane + Keycloak | Enforces local consumer state | Enforces local consumer state |
| Application-local user/profile UUID | No shared business UUID | Collectra-owned | ZAKAZ-owned if needed |
| Business tenant/membership | No universal tenant model | Mandatory, tenant-scoped | Defined by ZAKAZ, not copied |
| Business RBAC, orders, debt, finance | No business authorization | Owns receivables and permissions | Owns marketplace permissions/orders |
| Email delivery infrastructure | App-scoped notification transport | Generates business notification intent | May later request app-scoped notifications |
| Business data and migrations | Infrastructure only | Own PostgreSQL schema/DB | Own PostgreSQL schema/DB |
| Audit | Platform/security events | Business and tenant events | ZAKAZ business events |

Do not share business database tables, local user IDs or tenant UUIDs between applications. Do not resolve identity by unverified email. App Common operators do not inherit Collectra/ZAKAZ business administrator rights.

## 4. Runtime interaction

```text
                         User / browser / mobile
                                   |
                         Keycloak shared user realm
                         (OIDC Authorization Code + PKCE)
                                   |
                   +---------------+----------------+
                   |                                |
           token aud: collectra-api         token aud: qrm-api
                   |                                |
          Collectra API/resource server       ZAKAZ API (future)
          local (iss,sub) -> user ID          local (iss,sub) -> profile
          tenant/membership/RBAC              app-specific authorization
                   |                                |
          Collectra business DB                ZAKAZ business DB

 App Common (independent target control plane):
 application registry / OAuth provisioning / account security
 revocation event + ACK / app-scoped notifications / audit
```

A successful SSO session is not a business authorization grant. A Collectra access token must be rejected by ZAKAZ API and vice versa, even when `iss` and `sub` match. APIs validate signature/JWKS, issuer, audience, expiry, allowed client/scopes and app-local state.

Ordinary authenticated business requests must not require a synchronous App Common call. New authentication, MFA step-up and other high-risk actions fail closed if required identity infrastructure is unavailable. Existing JWT residual validity/revocation windows require explicit policy and tests.

## 5. Operating procedures

### 5.1 Developer / macOS smoke

Prerequisites: JDK 17, Docker daemon, configured local environment and secrets from gitignored `.env`; do not commit tokens, passwords or SMTP credentials.

```bash
git switch feat/iam-shared-platform
./scripts/smoke-iam-macos.sh
./scripts/stop-iam-macos.sh

./scripts/smoke-mail-macos.sh
./scripts/stop-mail-macos.sh
```

These are separate verification paths. Consult `docs/qa/iam-macos-smoke-report.md` and `docs/qa/mail-macos-smoke-report.md` for exact versions, prerequisites, evidence and limitations. The mail script uses a **real KumoMTA** container and a **local Mailpit** SMTP recipient; it is not proof of external internet inbox delivery. Do not use a simulated gateway for the real-provider acceptance gate.

### 5.2 Application registration and onboarding (target)

1. Register the application in App Common's Application Registry under an immutable application key (e.g. `collectra`, later `zakaz`).
2. Provision a dedicated OIDC client in the **existing shared end-user realm**; configure redirect URI allowlist, allowed origins, PKCE, scopes, token lifetimes and API audience.
3. Register resource-server issuer/JWKS/audience validation in the application; never trust an audience provided by an untrusted request body.
4. Implement verified, idempotent first-login mapping from `(issuer, sub)` to an app-local user/profile. Preserve existing Collectra UUIDs, foreign keys, tenant memberships and roles during migration.
5. Enforce app-specific status and business authorization; ensure user is denied protected actions without required local rights.
6. Configure service-to-service clients separately from browser/mobile clients, with least-privilege scopes, audience and credential rotation.
7. Register consumer event delivery/ACK and app-scoped notification recipient ownership contracts, if those capabilities are enabled.
8. Run security, PostgreSQL, HTTP, browser, recovery and negative cross-app tests before activation.

The steps above are an integration **contract**, not a claim that all management endpoints/UI are already deployed.

### 5.3 Operations and incident handling

Maintain independent health, metrics, traces, logs, audit, alerts, backup/restore and rollback for App Common and Collectra. Track global account state propagation per consumer with versioned ACKs; do not report `ENFORCED` while any required consumer has not acknowledged. A failed consumer must remain visible as `PROPAGATING` or an explicit failure state. Test replay, restart, duplicate events and stale tokens. Keep Keycloak secrets and App Common platform credentials out of application business databases.

For messaging, distinguish **provider accepted** from **delivered to final recipient**. KumoMTA HTTP 200 can contain per-recipient failure. Persist that failure; do not manufacture provider message IDs.

## 6. Separate readiness scorecards

**Never combine readiness of these products into one percentage.** Each cell must be backed by a test run, exact commit SHA, environment, evidence link and reviewer.

| Gate | Collectra Business Platform | Shared IAM / App Common |
| --- | --- | --- |
| Core functionality | Customer → invoice → payment → collection → campaign → message E2E | Global identity, client registry, auth/MFA/recovery, security events |
| Security | Tenant isolation, local RBAC, invoice/payment authorization | Issuer/audience isolation, app-scoped permissions, operator isolation |
| Reliability | PostgreSQL/outbox/RabbitMQ/recovery, provider failure paths | Consumer event propagation, versioned ACK, stale-token denial, outage |
| Frontend | Role-based business journeys and error/recovery UX | Operator console, app registration, security operations |
| Integrations | ERP/import mapping, KumoMTA, business API | Keycloak/OIDC, first and second real consumer, notification M2M |
| Operations | Backup/restore, monitoring, performance, rollback | Independent deploy, secret rotation, DR, monitoring, rollback |
| Release evidence | Full CI + real business E2E on exact release SHA | Full CI + two-consumer security E2E on exact release SHA |

States: `NOT_STARTED`, `IMPLEMENTED_UNVERIFIED`, `PARTIALLY_VERIFIED`, `VERIFIED`, `BLOCKED`. A smoke PASS is not a blanket production PASS.

### Evidence baseline at authoring

- `docs/qa/iam-macos-smoke-report.md`: SMOKE-01–06 and 08 **PASS** in reported macOS environment; SMOKE-07 **BLOCKED** because a real second consumer/App-B was unavailable. The operation correctly remained `PROPAGATING`, not `ENFORCED`.
- `docs/qa/mail-macos-smoke-report.md`: real KumoMTA HTTP injection and local Mailpit SMTP receipt **PASS**, plus per-recipient rejection **PASS**; external mailbox delivery and production reliability **unverified**.
- Independent App Common deployment, cross-app browser SSO and ZAKAZ integration are **not verified**.
- Historic reports are pinned to their recorded SHAs; re-run tests against the current release candidate. No new test execution is claimed by this document.

## 7. ZAKAZ integration roadmap (future, no implementation claim)

**ZAKAZ-IAM-R0 — contract freeze**
- Inventory existing ZAKAZ login, user IDs, account recovery, roles and API security.
- Decide verified migration/linking rules for existing ZAKAZ users; never merge by email alone.
- Define `zakaz` application key, `qrm-api` audience, browser/mobile clients, redirect URIs, scopes, token TTL, logout and revocation policy.
- Keep ZAKAZ orders, sellers, buyers and business rules in ZAKAZ; do not copy Collectra tenant model.

**ZAKAZ-IAM-R1 — isolated integration**
- Configure ZAKAZ clients in the same shared Keycloak end-user realm.
- Implement Authorization Code + PKCE for React and Flutter; API JWT validation.
- Add idempotent local profile linking and account-specific authorization.
- Prove login/logout/relogin, password reset, MFA, unknown account, suspended app account, expired JWT and key rotation.

**ZAKAZ-IAM-R2 — two-consumer acceptance**
- One verified global Keycloak user signs into Collectra and ZAKAZ with the same `(iss,sub)` but separate app-local IDs.
- Each API rejects the other application's access token (audience/client isolation).
- Local Collectra block does not disable ZAKAZ; global block prevents new access to both according to approved revocation window.
- Real HTTP security-event delivery and **two distinct authenticated consumer ACKs** are persisted; only then can global enforcement be reported as complete.
- Repeat with restart, duplicate event, consumer outage and recovery. No simulated second ACK.

**ZAKAZ-IAM-R3 — operational certification**
- Independent deployment/rollback, secret rotation, load, backup/restore, browser/device E2E and security review.
- Add shared notification transport only after app-scoped recipient/consent and real-provider tests.

Do not begin ZAKAZ integration by reusing Collectra JWTs, business user IDs or database tables.

## 8. Acceptance matrix

| ID | Test | Expected result |
| --- | --- | --- |
| IAM-01 | Collectra OIDC login and local tenant RBAC | Authenticated; unauthorized tenant denied |
| IAM-02 | ZAKAZ OIDC login with same global user | Same `(iss,sub)`, independent local profile |
| IAM-03 | Collectra token to ZAKAZ API and reverse | Both rejected |
| IAM-04 | Global block with two consumers | Two real ACKs; stale token rejected per policy |
| IAM-05 | App-specific block | Only target app denied |
| IAM-06 | Consumer restart / duplicate event | Version monotonic; idempotent recovery |
| IAM-07 | App Common / Keycloak outage | Existing authorized business requests follow defined JWT policy; new login fails safely |
| IAM-08 | App-scoped notification from each consumer | Cross-app recipient access denied |
| IAM-09 | Admin vs business principal | No privilege inheritance |
| IAM-10 | Legacy Collectra/ZAKAZ identity migration | Verified linking, local IDs/FKs preserved |
| IAM-11 | Independent App Common deploy/rollback | Collectra business schema unchanged |
| MAIL-01 | Real KumoMTA to local SMTP mailbox | Injection + SMTP receipt + durable status |
| MAIL-02 | Provider reject/timeout/ambiguous outcome | Safe failed/retry/unknown handling; no blind duplicate |

Current acceptance: IAM-01 and parts of IAM-04/06, MAIL-01 and controlled rejection have reported local evidence; **the full matrix is not green**.

## 9. Release and documentation governance

- Maintain one current working branch `feat/iam-shared-platform` for this documentation; do not merge into `main` without explicit release decision.
- Every readiness change records `product | gate | status | exact SHA | environment | test/run | evidence | reviewer | date`.
- No `VERIFIED` status based solely on documentation, code presence, mocks or CI skipped tests.
- Before App Common extraction, freeze public OIDC/service contracts, data ownership, migration and rollback. Prefer an independent S2 architecture/security review.
- On extraction, migrate platform-owned implementation and documentation to its own repository without rewriting Collectra business history.
