# APP-COMMON-R0 — Functional and Technical Specification
Status: PROPOSED IMPLEMENTATION BASELINE / NOT IMPLEMENTED
Date: 2026-11-14
Branch: feat/iam-shared-platform
Normative dependencies: ADR-IAM-004, ADR-IAM-005, IAM-R1.2/R1.3

## 1. Product scope
**App Common** is an independently deployed central authentication, notification and operations platform for multiple applications. **Collectra** is its first business consumer. App Common has a separate target repository, API, infrastructure data ownership, admin UI and CI/CD; creation of the new repository is a separate deliverable and is not claimed completed. App Common is NOT a Collectra business module. Existing business users, tenants, memberships, roles and permissions remain in Collectra. A second application is an isolation fixture until a real consumer is approved.

### R0 in scope
Application Registry, operator administration, Keycloak realm/client provisioning, OIDC JWT bearer security, app-scoped notifications (email first), platform audit, health/metrics, secrets integration, backups, test integration with Collectra. Legacy Collectra authentication migration is gated, not automatic.

### R0 out of scope
Shared business RBAC/tenants/users, global identity matching, cross-realm SSO, SMS/push provider in production, central synchronous authorization of every Collectra request, automatic creation of production apps without approval, general-purpose workflow engine.

## 2. Components and ownership
- App Common modular monolith: `application-registry`, `keycloak-provisioning`, `platform-security`, `notification`, `platform-audit`, `operations`; Spring Boot/Java with PostgreSQL migrations and separate Admin UI.
- Keycloak: authentication, credentials, verified email, reset, MFA, sessions and JWT signing. App Common configures Keycloak through supported APIs/config-as-code and never directly writes Keycloak tables.
- Collectra: OIDC resource server and local identity adapter `(issuer,subject)->localUserId`; local account/tenant/membership authorization; legacy migration.
- RabbitMQ (optional transport, required when async delivery enabled): durable notification delivery jobs with transactional outbox; not a substitute for database idempotency.
- Physical DB sharing allowed only with isolated roles/schemas, no cross-application SQL joins. Separate runtime identities, DB migrations, backups and secrets.

## 3. Application Registry
Fields: `application_id` (immutable UUID), `code` (unique), `display_name`, `owner`, `realm_name` (unique), `issuer_uri`, `state`, `config_version`, `created_at`, `updated_at`, `suspended_at`. Client records: client ID, allowed audiences, approved redirect URIs, grant types, enabled flag; secrets only as secret-store references. Registry contains no app business users or roles.

States: `REGISTERED -> CONFIGURING -> ACTIVE -> SUSPENDED -> DECOMMISSIONED`. Recovery from `SUSPENDED` to `ACTIVE` requires approval and validation. `DECOMMISSIONED` terminal. Every transition optimistic-version checked and audited. Realm provisioning is idempotent, with job state `PENDING/RUNNING/SUCCEEDED/RETRYABLE_FAILURE/MANUAL_REVIEW`; partial provisioning never activates an app.

Operator API, proposed paths:
- `POST /api/v1/platform/applications` register (idempotency key)
- `GET /api/v1/platform/applications/{id}` read
- `POST /api/v1/platform/applications/{id}/provision` start/retry controlled provisioning
- `POST /api/v1/platform/applications/{id}/activate` activate only after verified readiness
- `POST /api/v1/platform/applications/{id}/suspend` suspend
- `POST /api/v1/platform/applications/{id}/decommission` controlled terminal transition
- `GET /api/v1/platform/applications/{id}/provisioning-jobs` operational status
All writes: platform operator MFA, scoped RBAC, audit, idempotency where retries matter. Destructive changes require two-person approval. API returns standard `application/problem+json` errors, 409 for invalid transition/conflict, 422 for invalid config, 401/403 for auth, 503 for provider unavailable.

## 4. JWT and OAuth2 contracts
Keycloak is the sole issuer of signed JWT access tokens. No App Common-issued end-user JWT. Separate realm for each business application; separate secured operator context. `Authorization: Bearer <JWT>` is accepted by the target resource server only when signature, algorithm allowlist, exact `iss`, expected `aud`, `exp`, `nbf` and configured client policy pass. Reject ID tokens, refresh tokens, query-string tokens, unknown key IDs and mismatched realms. `sub` is opaque and cannot be parsed as a local UUID.

Logical audiences: `collectra-api`, `app-common-admin-api`, `app-common-notification-api`. Realm/client identifiers are pinned in configuration and tests. Human login: Authorization Code + PKCE S256; confidential BFF when appropriate. Machine access: Client Credentials with separate service identity and minimal scopes. No password grant. Rotate client secrets and signing keys with overlap and tests; no token logging. Local account status and authorization_version must be checked per Collectra security contract.

MFA: pinned Keycloak authentication flow + verified assurance mapping; fresh step-up <=10 minutes for privileged operations. Never infer MFA merely from untrusted role or `amr` text.

## 5. Notification API and delivery
- `POST /api/v1/notifications/email`: authenticated application machine client; server derives application scope from client identity, not caller-supplied `applicationId`. Request: `recipientRef` (app-local opaque ID), `templateCode`, `locale`, `templateVariables` allowlisted, `idempotencyKey`, optional correlation ID. No raw arbitrary SMTP recipients in normal application API.
- App-owned recipient resolver or signed app-scoped recipient assertion returns verified address and consent status at send time. Avoid direct SQL access to app user tables. Fail closed on unverified recipient and expired assertion. Security-critical Keycloak verification/recovery emails remain Keycloak-owned SMTP flows, not dependent on notification event recursion.
- Response `202 Accepted` with `notificationId`; states `ACCEPTED, QUEUED, SENDING, PROVIDER_ACCEPTED, DELIVERED, RETRYABLE_FAILURE, PERMANENT_FAILURE, CANCELLED`. `PROVIDER_ACCEPTED` is NOT proof of delivery. `DELIVERED` only with trustworthy provider evidence.
- Unique `(application_id,idempotency_key)`; durable outbox, retry with capped exponential backoff + jitter, dead-letter handling, concurrency-safe worker claim, redaction of PII, retention and audit.
- Rate limits per application, recipient and template. Notification scopes never permit reading or delivering another application's recipient.

## 6. Data and migration
App Common owns `application_registry`, `application_clients`, `provisioning_jobs`, `notification_requests`, `notification_attempts`, `notification_outbox`, `platform_audit_events`, and operational configuration. Keycloak owns its own database/schema. Collectra owns its user and business schema. All DDL through versioned migrations, PostgreSQL Testcontainers integration tests, explicit unique/FK/check constraints, least-privilege DB roles.

Collectra migration: retain existing local UUIDs, tenants, memberships, FK references, status and business permissions. Introduce `external_identity_links` and credential authority `LEGACY|KEYCLOAK`; verified ownership and idempotent migration journal; one atomic local cutover clears legacy hash, revokes local refresh sessions and increments authorization_version. Never match accounts solely by email. Feature flag and rollback plan; no automatic password hash restoration after cutover.

## 7. Nonfunctional and operations
- Target 99.9% monthly availability, RPO <=15 min, RTO <=60 min subject to measured infrastructure qualification.
- Keycloak HA, PostgreSQL HA/PITR, encryption at rest and in transit, secret store, audit integrity, least-privilege admin, network isolation, metrics/tracing/log redaction, alerting and restore drill.
- Keycloak outage: ordinary Collectra API requests can validate existing signed JWTs with trusted cached JWKS and active local authorization; no new login or fresh MFA step-up. Never bypass signature/issuer/audience validation.
- Data retention, consent, cross-border data residency, sender domain/DKIM/SPF/DMARC, SLO error budget and incident runbooks are release-gate configuration decisions.

## 8. Delivery sequence and gates
- R0.1 repository/deployable boundary and versioned API contracts; independent CI.
- R0.2 real Keycloak + PostgreSQL with two test realms and configuration-as-code.
- R0.3 positive/negative JWT Resource Server integration tests, including cross-audience and cross-realm rejection.
- R0.4 Application Registry with PostgreSQL concurrency/idempotency and failure recovery.
- R0.5 Notification email transport, recipient verification, outbox, retries, dead-letter, isolation.
- R0.6 Collectra integration and migration proof with React login flow.
- R0.7 security, HA, restore, performance and independent S2 review.

## 9. Mandatory acceptance matrix
| ID | Executable proof |
|---|---|
| AC-01 | App Common and Collectra build, deploy and roll back independently |
| AC-02 | Collectra local UUIDs/tenants/roles unchanged by platform onboarding |
| AC-03 | Correct Keycloak JWT accepted only by matching audience and issuer |
| AC-04 | Wrong issuer/audience, ID token, expired/tampered JWT rejected |
| AC-05 | Platform operator cannot grant Collectra business roles |
| AC-06 | Two independent realms with same email do not share identity |
| AC-07 | Concurrent duplicate application registration creates one registry record |
| AC-08 | Partial Keycloak provisioning retry creates no duplicate realm/client |
| AC-09 | Suspended application denied new auth and notifications under documented propagation SLA |
| AC-10 | Notification dedupe, retry, DLQ, provider acceptance vs delivery proven |
| AC-11 | Cross-application notification recipient spoofing rejected |
| AC-12 | Collectra account/membership revocation denies locally despite valid JWT |
| AC-13 | Admin MFA step-up and two-person destructive approval enforced |
| AC-14 | Keycloak outage behavior safe; cached JWKS cannot accept invalid token |
| AC-15 | Legacy Collectra credential cutover preserves local IDs and authorization |
| AC-16 | Backup restore and failover measured against RPO/RTO |
| AC-17 | Client Credentials token cannot invoke user-only or operator-only APIs |
| AC-18 | Application and secret rotation cannot change other realms |
| AC-19 | Real React Authorization Code + PKCE login/logout/refresh proven |
| AC-20 | S2 independent review and threat model approve release gates |

Every test must record real execution command, environment, code SHA, expected/actual assertion and CI artifact. Documentation is not PASS. Production gate: no unresolved P0/P1, real PostgreSQL/Keycloak tests, independent S2 and restore drill.

## 10. Open decisions requiring approval before implementation
DEC-01 Exact new App Common repository name, organization and deployment environment.
DEC-02 Keycloak pinned version, admin realm policy, supported OIDC client profiles and token TTL.
DEC-03 PostgreSQL physical topology, migration engine, DB role separation and secrets manager.
DEC-04 SMTP provider, domains, verified recipient contract and delivery evidence capability.
DEC-05 App Common Admin UI framework, operator roles and two-person approval mechanism.
DEC-06 Regulatory data residency, retention and consent policy.
DEC-07 Collectra legacy migration cohort, rollback window and cutover schedule.

## 11. Source precedence
ADR-IAM-004 defines product boundaries; ADR-IAM-005 defines bearer tokens; IAM-R1.2/R1.3 defines identity, migration, MFA and recovery. This document operationalizes them and does not claim that App Common repository, runtime, tests or CI already exist.
