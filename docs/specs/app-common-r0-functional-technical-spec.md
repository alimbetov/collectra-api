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

## 12. R0.1 architecture hardening — binding implementation contracts (2026-11-14)

This section is normative and resolves ambiguities in sections 1–11. **No code or integration test is claimed implemented.** Design approval is conditional on the decisions below and the independent S2 gate.

### 12.1 Authority and service-boundary matrix
| Actor | Authentication | Authorized operations | Explicit prohibition |
|---|---|---|---|
| App Common platform operator | App Common operator realm; human OIDC + MFA | Application lifecycle, security policy, approved realm configuration | Collectra business RBAC or tenant membership mutation |
| Collectra end user | Collectra realm; OIDC Authorization Code + PKCE | Collectra business APIs under local RBAC | App Common operator endpoints |
| Collectra backend | Collectra realm confidential machine client, Client Credentials | App-scoped Notification API, allowed operational integration | Other app notifications or platform administration |
| Keycloak provisioner | Dedicated restricted admin service account | Realm/client configuration within approved policy | Business database writes |
| Notification worker | Internal workload identity | Consume scoped delivery jobs, provider submission | Realm provisioning or business-role changes |

App Common operator identities must be isolated from Collectra business users; identical email or human owner does not confer cross-context permissions. **Do not use a single audience for all App Common APIs.** Resolve caller application identity from validated issuer, client identity and audience/scope; payload applicationId is never an authorization source.

### 12.2 Application suspension semantics — eliminate the JWT paradox
The following are distinct operations and must not be described as equivalent:
1. `SUSPEND_LOGIN`: disable app's new login/token acquisition via supported Keycloak policy and client/realm configuration; already issued JWTs can remain valid.
2. `SUSPEND_NOTIFICATION`: deny new app-scoped notification submissions and prevent queued unsent deliveries according to explicit cancellation policy.
3. `SUSPEND_API_ACCESS`: distribute a signed/versioned application status policy to the consuming resource server; resource server checks local cached application status on every protected request and rejects suspended apps.

**R0 default:** `SUSPENDED` means all three actions requested. App Common control plane records desired state, tracks observed state for each enforcement point and exposes `SUSPENDING` as an *operation status*, not an additional application lifecycle state. The API must not report enforcement complete until all mandatory acknowledgments arrive. Unknown acknowledgments -> operation `PARTIALLY_ENFORCED`, alert and block subsequent sensitive lifecycle transitions. Publish and test propagation target (proposed <=60 seconds when infrastructure healthy); until enforcement acknowledgment, never claim immediate JWT revocation. During control-plane outage, business API may use last known trusted status with bounded cache age; on stale status beyond configured TTL, **fail closed** for protected requests if suspension guarantees require it. This is an availability/security tradeoff to be approved before production.

Keycloak logout/revocation is not a substitute for resource-server suspension checks. A JWT is cryptographically valid until expiry even after client disablement unless resource server performs extra state checks.

### 12.3 Notification delivery and idempotency semantics
`POST /api/v1/notifications/email` requires `Idempotency-Key` header, scoped to authenticated application + endpoint + request fingerprint. Persist request hash and result; same key/same payload returns original notification ID and state, same key/different payload -> 409. The operation must atomically persist request + outbox. Worker claims with PostgreSQL row locking/lease and fencing, bounded attempts and backoff; crashes release lease for retry.

Exactly-once external email delivery **cannot be guaranteed** from PostgreSQL + SMTP. A timeout after SMTP provider acceptance produces `DELIVERY_UNKNOWN`, not a safe blind resend; retry policy depends on provider idempotency support and message class. `PROVIDER_ACCEPTED` is transport acceptance; `DELIVERED` requires trustworthy provider delivery evidence; absent evidence retain `PROVIDER_ACCEPTED` or `DELIVERY_UNKNOWN`, never infer inbox delivery. Add `DELIVERY_UNKNOWN` to the notification state set in section 5. Templates are versioned, typed and variable-allowlisted; secrets/action tokens never logged. Recovery/verification emails remain Keycloak-managed.

Consent: distinguish transactional/security notifications from marketing; do not treat absence of marketing consent as a reason to suppress necessary security notices, but still enforce verified recipient and lawful basis. Define per-class retention and throttling. Cross-app recipient reference is always rejected.

### 12.4 Verified recipient resolution — signed contract
Preferred R0 design: app-owned `POST /internal/notification-recipient/resolve` called by App Common with mTLS or audience-restricted Client Credentials. Input: app-derived scope, recipientRef, notification class, correlation ID. Output: verified address, verification timestamp/version, class-specific consent/lawful-basis decision, TTL and signed/integrity-protected assertion or secure channel evidence. No generic recipient lookup endpoint exposed to other apps. Resolve immediately before delivery (not just enqueue); reject mismatched app, stale verification, invalid signature, revoked user or unverified email. For security events Keycloak remains email authority and must not depend on this resolver. Failure -> retryable delivery failure, never send to stale cached address. Record only minimal PII and encrypted-at-rest recipient snapshots where required for retry/audit.

### 12.5 Realm provisioning reconciliation and configuration drift
Desired realm/client configuration is immutable, versioned and signed by deployment pipeline. Store provisioning job correlation ID, desired config hash, observed realm ID/client IDs and per-step checkpoints. Every step is safe to retry after timeout: read by stable realm/client identifier, compare expected ownership marker and config hash, then create/update only if owned. Never adopt an existing realm merely because its name matches. Unknown ownership -> MANUAL_REVIEW. Detect drift periodically; alert and block activation for security-sensitive drift (redirect URIs, grant types, audiences, signing keys, MFA flow). Destructive drift remediation requires approval; never automatically delete users or credentials. Require exact Keycloak version and API integration fixture before GREEN.

### 12.6 OAuth2 audience, JWT claims and lifecycle
Enforce audience as a required array/string containing the exact resource API identifier; `azp` or `client_id` must match allowed clients for that realm/resource. Explicitly reject tokens with missing `aud`, wrong token type, cross-realm issuer or wrong resource client. Prefer short-lived access tokens (initial proposed 5 minutes, subject to measured UX/security review), refresh token rotation and safe browser storage via BFF/secure cookies when applicable. Local authorization changes must take effect independently of token expiry by checking current user/membership/authorization_version. Separate `401 invalid token` from `403 insufficient business permission`; application suspension error is documented without leaking sensitive state.

### 12.7 PostgreSQL and isolation requirements
Use separate database roles for App Common runtime, App Common migration, Keycloak runtime, and Collectra runtime; no shared superuser. Enforce unique `(application_id,idempotency_key)`, unique realm ownership, optimistic config version, notification lease/fencing and application status policy version constraints in PostgreSQL. Test race conditions with real PostgreSQL, not H2. Secrets never in tables as plaintext. Database schema names and migration engine must be pinned before DDL. Cross-app isolation must be proven at API, token, DB privileges and notification worker boundaries.

### 12.8 Data protection and security threat model
Threats to cover: confused deputy between audiences, stolen service token, compromised operator, forged MFA claims, redirect URI injection, realm takeover by name collision, recipient spoofing, stale suspension cache, duplicate SMTP delivery, replayed provider webhooks, lost outbox messages, and cross-tenant disclosure. Require rate limits, audit correlation, secret rotation, provider webhook signature/timestamp/replay checks, restricted admin network and immutable audit export. Privacy and data residency requirements are release gates, not assumptions.

### 12.9 Contract-first proof gates (new tests)
- HARD-01 Valid JWT after Keycloak client disablement is rejected by Collectra only **after** resource-server suspension policy propagation; measured SLA, no false immediate-revocation claim.
- HARD-02 Missing/stale suspension policy beyond approved TTL fails closed.
- HARD-03 Notification same idempotency key/same payload returns same ID; changed payload -> 409.
- HARD-04 SMTP accepted then response timeout -> DELIVERY_UNKNOWN; no unjustified DELIVERED claim or unbounded resend.
- HARD-05 Worker crash after claim, concurrent workers and lease expiry -> bounded attempts and no lost requests.
- HARD-06 Recipient email changed or unverified between queue and send -> send blocked.
- HARD-07 Cross-app machine token cannot invoke another app's notification or resolver.
- HARD-08 Provisioning timeout after Keycloak realm creation reconciles to same realm without duplicate.
- HARD-09 Foreign pre-existing realm with same name/ownership mismatch -> MANUAL_REVIEW, no takeover.
- HARD-10 Security-sensitive realm config drift blocks activation and raises alert.
- HARD-11 Operator token cannot mutate Collectra business roles; Collectra business admin cannot invoke App Common operator API.
- HARD-12 Token missing/wrong audience, wrong `azp`, wrong issuer, ID token and forged MFA claim rejected.
- HARD-13 PostgreSQL role isolation prevents App Common runtime from reading Collectra business tables.
- HARD-14 Keycloak unavailable: existing valid JWT + fresh trusted local policy follows approved behavior; new login/step-up unavailable.
- HARD-15 Provider webhook replay, signature mismatch and out-of-order event cannot forge DELIVERED.
- HARD-16 Two-person approval cannot be satisfied by same operator under multiple sessions.

### 12.10 Decision register — mandatory before implementation release
- DEC-08 Suspension propagation SLA, status-policy transport, cache TTL and fail-closed/availability choice.
- DEC-09 SMTP provider idempotency capability and policy for DELIVERY_UNKNOWN.
- DEC-10 Recipient resolver trust transport, assertion schema, verification freshness and ownership.
- DEC-11 Provisioning ownership markers, config drift remediation and pinned Keycloak release.
- DEC-12 Token TTL, `azp` allowlists, operator realm, BFF/session pattern.
- DEC-13 DB/schema topology, migration tooling, audit retention and residency.
All decisions require named owner, recorded acceptance and matching integration tests. No undocumented defaults may be treated as production-approved.

## 13. R0.2 executable contract refinement (normative; design decisions, not implementation evidence)

### 13.1 Canonical decisions vs environment-specific choices
The following are **approved design defaults for R0 test fixtures**, not assertions that infrastructure exists: Java 17+, Spring Boot 3.x, PostgreSQL 15+, Spring Security OAuth2 Resource Server, Keycloak as OIDC provider, Liquibase for versioned migrations (align with existing Collectra stack), Testcontainers for integration, React for Admin UI, and durable PostgreSQL outbox for notifications. RabbitMQ is a delivery accelerator and must not be a correctness dependency. Production Keycloak version/digest, SMTP provider, secrets manager, deployment topology, domain, residency and operator approval mechanism remain explicitly OPEN until independently approved.

The App Common repository must be created separately; no move of Collectra business user/tenant/role tables. A single App Common modular monolith is the initial deployable, with Keycloak and PostgreSQL as separate processes. Notification is an internal module with a separately scalable worker process allowed when throughput justifies it.

### 13.2 Exact lifecycle and transition table
`application_registry.state` is **desired business lifecycle**, not a guarantee that external Keycloak or API enforcement has converged. `provisioning_job.state` and `enforcement_operation.state` are independent. Never add `SUSPENDING` to the application lifecycle enum.

| From | Command | To | Preconditions |
|---|---|---|---|
| absent | register | REGISTERED | unique code and realm reservation, authorized operator |
| REGISTERED | provision | CONFIGURING | approved immutable desired configuration |
| CONFIGURING | activate | ACTIVE | provisioning SUCCEEDED, policy validation, independent approval |
| ACTIVE | suspend | SUSPENDED | durable operation created; enforcement status remains separately pending |
| SUSPENDED | reactivate | ACTIVE | reconciliation, policy verification, independent approval |
| REGISTERED/CONFIGURING/ACTIVE/SUSPENDED | decommission | DECOMMISSIONED | two-person approval, retention/export and revocation plan |

A `suspend` request returns `202` with `operationId` and `enforcementStatus=PENDING`; status becomes `ENFORCED` only when Keycloak new-login control, notification stop and resource-server suspension policy have acknowledged. `PARTIALLY_ENFORCED` or `FAILED` must never be presented as fully secured. Resource servers require authenticated, monotonically versioned policy updates and report applied version. Proposed R0 fixture: status policy max age 60s; enforce within 60s under healthy infrastructure; after TTL expiry fail closed. These numbers are **test defaults pending operational approval**, not production SLO commitments.

### 13.3 API envelope and idempotency contract
For mutating operator endpoints use `Idempotency-Key` header (UUID or opaque 16–128 ASCII characters), authenticated operator/client scope, canonical request hash, and a 24-hour deduplication window. A retry with same key+hash returns same resource/operation ID; different hash -> `409 IDEMPOTENCY_CONFLICT`. The key must be stored atomically with the local command and outbox. `POST /applications/{id}/activate|suspend|decommission` returns `202` and `operationId` where external effects are pending. `GET /api/v1/platform/operations/{operationId}` returns desired state, observed enforcement steps, last error code, retryability and timestamps; it must not expose secrets.

Errors use RFC 9457 Problem Details with stable `type`, `title`, `status`, `detail`, `instance`, `code`, `correlationId`. Never expose whether an unrelated realm or recipient exists. Use `401` for invalid/missing bearer credential; `403` for valid token lacking privilege; `409` for lifecycle/idempotency conflicts; `422` for semantic configuration errors; `429` for quota; `503` for unavailable dependencies.

### 13.4 Recipient resolution and delivery state machine
The normal app notification API accepts only app-scoped `recipientRef`, not arbitrary email addresses. App Common derives `applicationId` from validated machine identity. Collectra exposes a **private** recipient resolution contract, authenticated using dedicated service credentials with the Collectra resolver audience; response includes `recipientRef`, `verifiedEmail`, `verifiedAt`, `verificationVersion`, `accountActive`, `notificationClass`, `allowed`, `expiresAt`. Response must be integrity-protected by authenticated TLS and strict caller authorization; no signature requirement when protected by a mutually authenticated trusted channel. TTL <=60s in the test fixture, but **no cached assertion may override a new revocation**. Re-resolve immediately before provider submission. This is a cross-service dependency of notifications only, not normal business API calls.

Notification states: `ACCEPTED -> QUEUED -> SENDING -> PROVIDER_ACCEPTED -> DELIVERED` where delivery evidence exists. `SENDING -> DELIVERY_UNKNOWN` on ambiguous provider result. `QUEUED/SENDING -> RETRYABLE_FAILURE` only where retry is safe; `PERMANENT_FAILURE` on definitive rejection; `CANCELLED` only before irrevocable provider acceptance. `DELIVERED` is not emitted from bare SMTP 250 alone. Terminal/transition rules are enforced by DB constraints/service state machine and monotonic event sequence; late callbacks cannot regress state. Duplicate provider callbacks are deduplicated by provider event ID and scoped provider account.

For `DELIVERY_UNKNOWN`, do not automatically resend security-sensitive or non-idempotent email without provider deduplication support or explicit policy; expose manual reconciliation and alert. Promise **at-least-once processing**, not exactly-once external delivery.

### 13.5 Trust and operator bootstrap
App Common operator realm must be isolated from Collectra realm and must have its own dedicated API audience. Bootstrap of first platform operator is an audited deployment operation using one-time privileged credentials in a secrets manager, with forced MFA enrollment and credential rotation; no permanent default admin account or hardcoded password. Two-person approvals are tied to **distinct immutable operator subjects**, not sessions or email addresses; requester cannot approve own operation. Break-glass access requires time-limited elevation, immutable audit and post-incident review.

### 13.6 Token revocation, roles and session compatibility
Keycloak JWTs are authoritative authentication evidence; they are **not** authoritative Collectra membership/role snapshots. The Collectra adapter resolves active `(issuer,subject)` mapping and checks local user status, tenant membership and authorization version. Current-user endpoints must not parse `sub` as a UUID. Existing local JWTs may only be accepted behind a time-limited migration feature flag with a distinct legacy issuer/audience and a measured retirement date; never treat legacy and Keycloak tokens as interchangeable. Invalid token -> 401; valid token but revoked business permission -> 403; locally blocked user -> deny under documented security policy. Add tests for a role change between token issue and API call.

### 13.7 Schema minimum and indexes
Required App Common tables: `applications` (unique code, realm name, config version), `application_clients` (unique application/client ID), `provisioning_jobs` (unique active application+desired config version), `enforcement_operations` (application, desired version, observed step acknowledgments), `idempotency_records` (unique app/actor/endpoint/key), `notification_requests` (unique application/idempotency key, canonical request hash), `notification_attempts` (unique request/attempt), `notification_outbox` (claim lease/fencing token), `notification_provider_events` (unique provider account/event ID), `platform_audit_events` (append-only). Avoid storing raw JWT, Keycloak passwords, refresh tokens or SMTP credentials. Actual DDL, data types, migrations and constraints require PostgreSQL RED tests before implementation.

### 13.8 Source consistency cleanup and release gates
Sections 3 and 5 list initial fields/states; **this section refines them** with operation status, idempotency and `DELIVERY_UNKNOWN`. Historical IAM-R1.2 statements about `PENDING_VERIFICATION` are superseded by R1.3: Keycloak owns unverified signup, app-owned provisioning begins only after verified identity. ADR-IAM-003 is historical and does not designate Collectra as platform. `App-B` is a test realm fixture. Any code-level behavior that differs from these contracts is a defect or requires a new ADR.

Additional executable cases:
- SPEC-01 same idempotency key, different payload -> 409 without second side effect;
- SPEC-02 concurrent suspend/reactivate with optimistic version -> exactly one accepted transition;
- SPEC-03 partial suspension reports PARTIALLY_ENFORCED and never claims access revoked;
- SPEC-04 stale policy cache fails closed after configured TTL;
- SPEC-05 queued email recipient changes address -> only freshly verified address eligible;
- SPEC-06 SMTP ambiguous result remains DELIVERY_UNKNOWN, no blind duplicate;
- SPEC-07 two-person approval rejects same immutable subject in distinct sessions;
- SPEC-08 legacy JWT sub parsing path replaced without breaking local user UUID references;
- SPEC-09 provider callbacks duplicate/out-of-order cannot regress delivery status;
- SPEC-10 foreign realm ownership marker cannot be adopted.

**Release sequence:** (1) independent design S2 review; (2) real PostgreSQL/Keycloak/HTTP RED fixtures; (3) minimal implementation and GREEN; (4) Collectra React browser E2E; (5) fault injection, backup restore and measured SLO; (6) production approval. Do not mark any criterion PASS without recorded executable evidence.

## 14. Mandatory user-journey coverage gate (normative)
The companion specification `docs/specs/app-common-r0-user-journey-simulation.md` is the authoritative functional journey catalog for APP-COMMON-R0.3: US-01..US-24, three detailed reasoned traces, and GAP-01..GAP-12. This **design simulation is not runtime verification**. Each implemented capability must trace to a journey and a positive, negative, concurrent/failure test with CI evidence. The release cannot claim full functional coverage while P0/P1 journeys remain unverified. Prioritize US-01/02/03/09/11/13/14/20 and independent S2 review before GREEN implementation.

## 15. Internal shared service scope (normative; ADR-IAM-006)
**App Common is an internal centralized control plane for the owner's own applications**, not a public SaaS or customer-facing application. Its operator UI, provisioning endpoints, Keycloak admin access, infrastructure and operational APIs are private-only, with explicit network policy, MFA and audit. Consuming application backends use private authenticated service APIs; business end-user APIs remain with their respective applications. Keycloak end-user OIDC endpoints may need controlled public ingress for browser/mobile login, without exposing admin interfaces. No public self-service app registration, external customer billing/tenancy or global shared business user directory. Multi-application isolation remains mandatory. ADR-IAM-006 and INT-01..INT-08 are required acceptance gates; all prior R0 user journeys are evaluated under this private-platform boundary.

## 16. Unified shared realm and global identity — binding supersession (ADR-IAM-007)
**New authoritative decision:** App Common uses **one shared Keycloak end-user realm and global user directory** for all owned applications, with cross-app SSO and one stable Keycloak `(issuer,sub)` per global account. This replaces every earlier R0 reference to separate end-user realms, same-email identities being isolated between applications, and App-B as a second realm. New applications register **distinct OAuth2 clients, resource audiences and scopes in the shared realm**; they do not create another end-user realm. Keep an isolated operator realm as a security boundary. Application-specific local UUIDs, profiles, tenants, memberships and business RBAC remain app-owned; first login provisions app-local profile idempotently. No global business-role sharing. Strict `aud` checks prevent cross-API token reuse. App suspension disables its client and API policy, **not** the global realm or user account. Global user suspension has distinct semantics and must be propagated across apps. Individual app decommissioning must never delete the shared realm/global identity. ADR-IAM-007 supersedes conflicting sections 1–15; SSO-01..SSO-12 are mandatory tests. Migration of legacy users requires verified identity ownership, never email-only linking.

## 17. Business tenancy boundary — binding ADR-IAM-008
**One shared Keycloak end-user realm, global account and SSO for all owned applications.** App Common provides common authentication, MFA, account lifecycle, application registry, notifications and audit. **Only Collectra requires business tenant isolation** in the current scope; its tenants, memberships, business roles and tenant-data filters remain entirely in Collectra. No other app must adopt Collectra tenant tables, claims or membership model. Other apps may create local profiles and define app-specific authorization only as needed. OAuth2 audience/client isolation, service credential boundaries and notification recipient scope remain mandatory for all applications. New apps provision OIDC clients in the existing shared realm, never a new end-user realm. R0 email notifications; SMS/push are future scoped capabilities, not implemented. ADR-IAM-008 and TEN-01..TEN-10 supersede conflicting universal-tenancy and per-app-realm requirements.
