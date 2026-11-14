# APP-COMMON-R1 — Consolidated functional and technical specification
Status: PROPOSED CONSOLIDATED BASELINE / IMPLEMENTATION HOLD
Date: 2026-11-14
Branch: feat/iam-shared-platform
Replaces: APP-COMMON-R0 sections 1–17 for forward implementation
Decision authorities: ADR-IAM-005 (JWT), ADR-IAM-006 (internal control plane as refined), ADR-IAM-007 (one shared end-user realm), ADR-IAM-008 (Collectra-only business tenancy)
Traceability: docs/specs/app-common-r1-user-journey-traceability.md

## 1. Product boundary and non-goals
App Common is an **internal, independently deployable shared identity and service control plane** for the owner's applications. It is not a public SaaS, a business tenant registry, a proxy for all API requests, or a replacement for each app's domain authorization. It has its own target repository, deployment, database, admin UI, CI/CD, audit and operational ownership; the independent repository/runtime are **not yet evidenced**.

Keycloak is the **only** identity/credential authority and OAuth2/OIDC JWT issuer. **One shared Keycloak end-user realm and one global user directory** serve all applications. Each application receives a distinct OAuth2 client and resource API audience; browser SSO shares the global identity. The same user can enter all connected apps without another registration, subject to application availability and application-specific business rules.

**Only Collectra requires tenant isolation of business data** in the current scope. Its local user UUIDs, organizations, tenant membership, permissions and business records remain Collectra-owned. Other applications are not required to implement tenant membership or a local user table unless their domain needs it. No shared global business roles.

The operator security domain is separate from the end-user realm (recommended isolated operator realm), not a second end-user realm. Operator APIs and Keycloak admin access are private-only; browser/mobile OIDC endpoints may have controlled public ingress. App Common is not on the synchronous hot path for ordinary consumer API authorization.

### R1 scope
Internal application registry and lifecycle; client/audience provisioning and reconciliation in shared realm; global identity lifecycle and SSO; Keycloak configuration; operator MFA and approvals; per-application access enforcement; app-scoped transactional email with templates, verified recipient resolution, outbox and audit; Collectra integration and verified legacy migration; operations, monitoring, backup/restore, acceptance fixtures.

### Explicitly out of scope
Public app signup, external customer organizations/billing, central business RBAC, global business tenancy, automatic email-based account merging, a second end-user realm per app, guaranteed exactly-once SMTP delivery, implemented SMS/push (future extension), live claims of production readiness.

## 2. Ownership and data contract
| Data / capability | Authoritative owner | Consumer responsibility |
|---|---|---|
| Global `sub`, credentials, verified email, MFA, SSO, global disablement | Keycloak shared end-user realm | Trust validated issuer/subject, handle state and local onboarding |
| Application registration, OAuth2 client metadata, operations, policy versions, audit | App Common PostgreSQL | App-specific client integration and policy acknowledgement |
| Token issuance, JWKS, session lifecycle | Keycloak | Verify `iss`, signature, `aud`, expiry, accepted client/scope and local authorization |
| Collectra tenant, local user UUID, membership, roles, business data | Collectra PostgreSQL | Enforce tenant boundaries in every relevant data operation |
| Other-app profile/business permissions | Each app, if needed | Do not require Collectra tenant |
| Notification requests, attempts, templates, provider events | App Common PostgreSQL | Verify recipient references, permissions, consent and business eligibility |

No second global user-password table in App Common. Minimal App Common user metadata may be cached with version/freshness and must not become an alternative identity authority.

## 3. Authentication and first entry
- Browser/mobile: Authorization Code + PKCE S256, allowlisted redirect URIs/origins, secure session storage policy and CSRF protection appropriate to chosen SPA/BFF topology. **BFF vs SPA token storage remains an explicit open decision**.
- Machine: Client Credentials with separate confidential clients, least-privilege scopes, API audience and secret rotation; optionally mTLS.
- Same `iss` and `sub` across apps, different `aud` and client authorization. Access token for API A must be rejected by API B; ID token must never be accepted as API bearer.
- First login to a new app may provision an app-local profile idempotently under unique `(issuer,subject)` per app; **Collectra tenant membership is created only under its own explicit onboarding/invitation rules**, not automatically from SSO.
- Global account disablement prevents new login everywhere; existing JWTs need a versioned revocation/enforcement mechanism and measured residual window. App-local block affects only that app. App suspension affects its client/notification/API policy, not global identity.
- Logout semantics: local app session termination vs Keycloak global logout are separate, with explicit remaining JWT window and refresh-token behavior.

## 4. Application lifecycle and admin
Canonical states: `REGISTERED → CONFIGURING → ACTIVE ↔ SUSPENDED → DECOMMISSIONED`. Re-activation from SUSPENDED passes readiness/reconciliation. Decommission does not delete shared realm or global users. Mutations use durable operations with `PENDING/RUNNING/PARTIALLY_ENFORCED/ENFORCED/FAILED` as appropriate; a `202` is not proof of enforcement.
Operator authentication uses isolated security domain, fresh MFA for privileged changes, two distinct approvers for critical operations, immutable audit and break-glass procedure.
App Common owns provisioning of **clients, scopes, audiences and policies within the existing shared realm**, not provisioning a realm per app. Reconciliation uses ownership markers and detects drift/foreign configuration.

## 5. Proposed API contracts
App Common internal REST base `/api/v1`:
- `POST /platform/applications`, `GET /platform/applications/{id}`, `POST /platform/applications/{id}/suspend`, `POST /platform/applications/{id}/reactivate`, `POST /platform/applications/{id}/decommission`.
- `GET /platform/operations/{id}`, `GET /platform/audit`, `POST /platform/approvals`, `POST /platform/clients/{id}/rotate`.
- `POST /notifications`, `GET /notifications/{id}`, `POST /notification-templates`, `POST /notification-templates/{id}/publish`, `POST /provider-events/{provider}`.
- Private recipient resolver is **consumer-owned** and app-scoped. Collectra onboarding, invitations, local access and identity linking are **Collectra-owned contracts**, not App Common endpoints.
- Mutation requests use `Idempotency-Key` with actor/endpoint scope, canonical fingerprint and retention (24h **fixture**, production policy pending); same key+different payload → `409`. Structured `application/problem+json` errors (RFC 9457), correlation ID, async operation ID.
- No endpoint grants cross-app data enumeration. Audience and service client binding is mandatory.

## 6. Notification service
R1 delivery channel is transactional email. A consumer backend submits template code, locale, app-scoped recipient reference, idempotency key and allowlisted parameters, **not arbitrary unverified recipient email**. App Common authenticates machine identity, binds application, persists request+outbox transactionally, resolves current verified recipient via app-owned authenticated endpoint immediately before sending, checks class/consent, renders immutable versioned template and sends.
States: `ACCEPTED → QUEUED → SUBMITTING → SUBMITTED`, then `DELIVERED/BOUNCED/FAILED` when evidenced; `DELIVERY_UNKNOWN` when provider acceptance is ambiguous. Provider acceptance ≠ inbox delivery. Deduplicate callbacks; bound retry and DLQ; no blind resend of `DELIVERY_UNKNOWN`.
Template governance: app ownership, locale fallback, variable schema, HTML escaping/sanitization, preview authorization and version pinning. User notification preferences/consent must be represented with explicit authoritative ownership. SMS/push remain future channels and require separate provider, consent and delivery contracts.

## 7. PostgreSQL canonical schema (proposed)
App Common database tables: `applications`, `application_clients`, `provisioning_jobs`, `enforcement_operations`, `idempotency_records`, `notification_requests`, `notification_attempts`, `notification_outbox`, `notification_provider_events`, `notification_templates`, `notification_template_versions`, `platform_approvals`, `platform_audit_events`. The old name `application_registry` is **superseded** by `applications`; no double table.
Unique constraints: application code; client ownership/realm client ID; idempotency tuple; provider event ID per provider; outbox dedup key; immutable template version; identity link unique `(issuer,sub)` in each app. Index active outbox lease/next attempt, operation state, audit correlation. Sensitive fields encrypted or minimized; DB roles separate for Keycloak, App Common and each consumer. Liquibase/Flyway choice and concrete DDL remain implementation decisions; no migration claimed.

## 8. Security and resilience
- Strict JWT signature/issuer/audience/client/scope checks and local authorization. Do not parse `sub` as Collectra UUID; map verified `(issuer,sub)` to preserved local UUID.
- No public access to operator/admin/provisioning API; no end-user token privilege escalation. End-user OIDC ingress is separately restricted.
- Cache JWKS with safe refresh; ordinary app APIs must not require live App Common availability. Unknown signing key and expired/stale policy fail according to explicit security rules (default deny for unresolved authorization).
- Per-app suspension enforcement propagates signed/authenticated policy versions to resource servers, with acknowledgements, max-staleness bound and fail-closed fallback; target 60s is **test fixture, not approved SLA**.
- Separate global disablement vs local Collectra block; preserve other apps when Collectra user is locally blocked.
- Secret rotation, least privilege, private networks, rate limits, MFA, security logs, PII minimization, backups, restore and failure drills.

## 9. Collectra legacy migration
Keep existing Collectra local UUIDs, tenant membership, foreign keys, roles and data. Link legacy account to global Keycloak `(iss,sub)` only after verified account ownership; never auto-merge by email. Atomic/recoverable mapping, revocation of legacy local tokens, dual-read/rollback plan with time-bound cutover. No resurrection of local passwords after cutover. A migration must not create a new tenant or change existing membership implicitly.

## 10. Nonfunctional gates and test environments
Use pinned Java/Spring Boot, PostgreSQL, Keycloak, SMTP fixture and browser runtime; concrete versions must be approved before implementation. Require real PostgreSQL concurrency fixtures, Keycloak integration, deterministic SMTP faults, browser E2E with two OIDC clients in the **same shared end-user realm**, fault injection, security tests, backup/restore drill and independent S2 review.
No performance or RTO/RPO SLA is approved yet; record measurable targets before production.

## 11. User journey and test traceability
The companion **APP-COMMON-R1 traceability matrix** maps **all US-01..US-24** to interface, authoritative state change and named executable test. Mandatory supplemental checks: SSO-01..12, TEN-01..10, INT-01..08, HARD-01..16, SPEC-01..10. A story is COMPLETE only when required positive, negative, failure and concurrency tests are actually GREEN, with CI run evidence and reviewer. Mock-only unit tests cannot satisfy real Keycloak/PostgreSQL/SMTP/browser gates.

## 12. Outstanding decisions and blockers
| ID | Decision | Status |
|---|---|---|
| DEC-R1-01 | SPA token storage vs BFF; session cookies and CSRF | OPEN |
| DEC-R1-02 | Keycloak version, operator realm bootstrap, MFA policy | OPEN |
| DEC-R1-03 | JWT lifetime, global revocation and app suspension enforcement SLA | OPEN |
| DEC-R1-04 | Notification SMTP provider, DELIVERY_UNKNOWN reconciliation | OPEN |
| DEC-R1-05 | Shared profile attribute ownership, verified email freshness | OPEN |
| DEC-R1-06 | Consent/preferences ownership, deletion/retention across apps | OPEN |
| DEC-R1-07 | Real deployment topology, secrets manager, network ingress | OPEN |
| DEC-R1-08 | Migration rollback and legacy token cutoff policy | OPEN |
| DEC-R1-09 | Backup RPO/RTO, throughput, rate limits, monitoring SLO | OPEN |
| DEC-R1-10 | Standalone App Common repository name/visibility and CI ownership | OPEN |

## 13. Delivery and release decision
**R1 is a specification consolidation, not a feature release.** No new branch, main merge, deployment or executable test claimed. Gate 1: independent S2 approves R1 contracts and open decision disposition. Gate 2: implement RED PostgreSQL/Keycloak/HTTP/SMTP tests for P0 journeys. Gate 3: minimal GREEN implementation. Gate 4: browser two-app SSO, failure injection, restore and security regression. Production remains **HOLD** until evidence exists.

## 14. Source precedence and migration from R0
This consolidated R1 is the forward implementation baseline. R0 remains historical, including superseded per-app realms and duplicate amendments; do not implement from its contradictory passages. ADR-IAM-007/008 and this R1 settle shared end-user realm, SSO and Collectra-only business tenancy. The traceability matrix is authoritative for US-01..24 mapping. Any future change must edit the canonical R1 sections and associated matrix **in place**, not append an override paragraph.
