# IAM-R1.2 — Canonical multi-application identity contract
Status: DESIGN BASELINE / NOT IMPLEMENTED. Date: 2026-11-14.
Applies to Collectra and App-B (reference second independent application), with a reusable onboarding pattern for future apps.
Normative priority: this contract supersedes conflicting IAM-R0/R1.1 design statements. Existing source code remains authoritative for current behavior, not target behavior.

## 1. Isolation and shared infrastructure
- One Keycloak deployment with **one realm per application**: `collectra`, `app-b`; independent user stores, sessions, clients, MFA policies, login UX, social IdP configurations and verified email state. Same email can exist in both realms without automatic linking.
- Separate clients for React SPA and mobile/native clients in each realm, Authorization Code + PKCE S256. API audience per app. Cross-realm issuer, audience or token reuse is denied.
- Shared PostgreSQL server may host schemas `keycloak`, `identity`, `notification`, `platform_audit` with least-privilege runtime roles; applications own their **own** business schemas/databases and local IDs. The shared `identity` schema holds infrastructure metadata only, **not a global user directory or shared credentials**.
- Shared Notification Service is multi-app infrastructure, with `application_id` + realm issuer as mandatory routing/isolation keys. No cross-app email lookups. Email is not an identity key.
- App-B is a reference contract, not an assertion that its repository or client already exists. Its business role model is owned by App-B, never inherited from Collectra.

## 2. Canonical application registration saga (resolves AUD-01)
- Keycloak user created with mandatory email and pending VERIFY_EMAIL action. No application access while unverified.
- After trusted verified identity is presented, API creates a durable `registration_intent` in the **application-owned** schema: `id`, `issuer`, `subject`, `requested_slug` (nullable for non-tenant apps), `idempotency_key`, `state`, `expires_at`, `created_at`, `updated_at`, `last_error_code`. Unique active `(issuer,subject,application_id)` and `(application_id,idempotency_key)`. Slug reservation is a unique, time-bounded row (24h) created only for verified users; expired reservations are cleaned and cannot be reclaimed without rechecking uniqueness.
- States: `PENDING_VERIFICATION` (identity only, no app intent required), `VERIFIED`, `PROVISIONING`, `ACTIVE`, `FAILED_RETRYABLE`, `CANCELLED`. No distributed DB transaction with Keycloak: use idempotent application transaction and compensating retry. Never mark ACTIVE before tenant/user/membership commit.
- Collectra: atomically create tenant, local user, external identity mapping and TENANT_ADMIN membership/roles in one application DB transaction. Duplicate callback/HTTP retry returns same outcome. Slug conflict returns 409 without creating orphan tenant. Do not issue app-local JWT.
- App-B: atomically create app-local user/profile and initial app-owned permissions. No tenant or slug requirement unless App-B opts into tenancy. Never copy Collectra memberships or UUIDs.
- If provisioning fails, retry safely with same intent; alert on stuck PROVISIONING; no partial activation. App authorization requires ACTIVE state and verified email, not only valid Keycloak token.

## 3. Canonical external identity mapping (resolves AUD-02)
Application-owned table `external_identity_links` (Collectra may name it `collectra_external_identity`), columns:
`id UUID PK`, `application_id TEXT NOT NULL`, `local_user_id UUID NOT NULL`, `issuer VARCHAR(512) NOT NULL`, `subject VARCHAR(255) NOT NULL`, `provider VARCHAR(80) NOT NULL DEFAULT 'KEYCLOAK'`, `linked_at TIMESTAMPTZ NOT NULL`, `unlinked_at TIMESTAMPTZ NULL`, `created_by UUID NULL`, `status VARCHAR(20) NOT NULL`, `link_method VARCHAR(40) NOT NULL`.
- FK `local_user_id` to the application's user table; never to a global directory. Unique **active** `(application_id,issuer,subject)` via partial unique index WHERE unlinked_at IS NULL AND status='ACTIVE'.
- At R1.2, exactly one **active Keycloak identity** per local user per application, enforced by partial unique `(application_id,local_user_id)`. Multiple historical/inactive links retained for audit. Future multi-active linking requires separate ADR and migration.
- A Keycloak subject may not link to two local users in the same app. Never link by email alone. Linking requires authenticated ownership proof of both identities or a controlled recovery ceremony, plus audit and anti-replay.
- Provider is Keycloak for API mapping; Google/Apple/Microsoft remain internal to each Keycloak realm.

## 4. Principal and authorization (resolves AUD-03)
- Introduce `ApplicationPrincipal(applicationId, issuer, subject, localUserId, contextType, tenantId?, membershipId?, authTime, mfaLevel)`. Collectra adapter named `CollectraPrincipal`; App-B implements its own adapter.
- A trusted JWT decoder validates pinned realm issuer, signature/JWKS, audience, exp/nbf and client binding. Resolver maps `(issuer,subject)` to local user and checks local status, registration ACTIVE, verified email, tenant status and membership/role authorization on each protected request (or a versioned cache with bounded staleness).
- Never parse Keycloak JWT `sub` as a local UUID, forge the JWT subject, or trust client-provided `tenant_id`/`membership_id` as authorization. Refactor Collectra controllers and security context consumers to use `CollectraPrincipal`; legacy adapters remain separately scoped and audited.
- Collectra platform context (`tenant_id IS NULL`) and tenant context are distinct, selected server-side and checked against local platform roles/membership. App-B defines its own scopes; platform roles never transfer between apps.
- Unmapped identity gets 403, invalid token gets 401, authenticated but inactive app membership gets 403.

## 5. Credential authority cutover (resolves AUD-04)
- `credential_authority` on Collectra `user_accounts`: `LEGACY` or `KEYCLOAK`, backfill LEGACY. `password_hash` nullable **only** for KEYCLOAK identities, with CHECK `(credential_authority='LEGACY' AND password_hash IS NOT NULL) OR (credential_authority='KEYCLOAK' AND password_hash IS NULL)`.
- Link and verify Keycloak account before a single DB transaction flips authority to KEYCLOAK, clears legacy hash, revokes local refresh sessions, increments authorization_version and records audit. Reject concurrent legacy reset/change by locking user row and checking authority in the same transaction. New OIDC sessions may be admitted only after commit.
- After flip, legacy login/reset/change always deny for that user. Never automatically restore legacy password hash on rollback; recover through Keycloak or controlled re-enrollment. Existing short-lived legacy JWTs require bounded invalidation policy.
- App-B starts KEYCLOAK-only unless importing legacy users; it does not inherit Collectra's password_hash schema.

## 6. Administrator MFA enforcement (resolves AUD-05)
- Keycloak realm flow enforces TOTP for admin authentication where configured, but application is authoritative for privileged roles. On each privileged action, application checks local role plus trusted Keycloak authentication evidence (`acr/amr/auth_time` as supported by pinned Keycloak version), and requires step-up if insufficient.
- Target privileged step-up freshness <= 10 minutes; privileged session invalidated or re-evaluated immediately on role change. Missing or untrusted MFA evidence = deny.
- Collectra PLATFORM_SUPER_ADMIN and TENANT_ADMIN require MFA; App-B's privileged roles are declared by App-B policy and also require MFA by default.
- Administrator MFA recovery: identity-verified two-person approval, recorded audit, no email-only bypass. Backup codes only if pinned Keycloak supports and validates them.

## 7. Email verification, events and notification consistency (AUD-06,08)
- Keycloak owns email_verified. App-local `verified_email_projection` is an optional cache with issuer, subject, email, verified, eventVersion/time and lastSyncedAt. Verify source signature, deduplicate events and handle out-of-order delivery; if freshness cannot be established, fail closed for ordinary email and privileged activation.
- Never set `email_verified_at` from `completeProfile()`. Two-phase email change: retain old verified destination until new address is verified, then switch atomically. On suspected compromise, suspend business email.
- Keycloak transactional SMTP is separate from Notification Service. Provider acceptance is not inbox delivery. Use `PROVIDER_ACCEPTED` or `UNKNOWN` when delivery confirmation unavailable; `DELIVERED` only with trusted provider receipt.
- Notification recipient lookup uses authenticated application+realm and local user ID, rechecks verified address at send time, and never accepts arbitrary destination email from end-user API.

## 8. OTP, revocation, clients and operations (AUD-07,09,10,11,12)
- Keycloak TOTP is login MFA. Legacy business OTP endpoints disabled by default (404/410); enabling requires a purpose-bound, delivered, atomic proof and distributed rate limits.
- Blocking a local user or removing a privilege must deny the next protected API request; enforce local status/version at authorization time. Password reset revokes Keycloak refresh/SSO sessions; document residual access JWT lifetime (target <=5m), and add immediate deny for high-risk actions.
- React Collectra is in scope. Flutter Collectra is conditional until a client inventory proves it exists; App-B clients likewise require inventory.
- Keycloak version/digest, realm exports, client IDs, redirect allowlists, TLS/SMTP config, DB grants, action-token lifetimes, health checks, backups and restore drills must be pinned as implementation deliverables, not claimed already complete.
- Threat model includes issuer confusion, PKCE/state/nonce abuse, stolen invitation, reset token replay, account linking, slug squatting, social login email spoofing, event replay and distributed OTP brute force.

## 9. Multi-app acceptance matrix
- MA-01 same email registers independently in Collectra and App-B; distinct Keycloak subjects, local IDs, passwords, sessions and notification recipients.
- MA-02 App-B JWT rejected by Collectra and Collectra JWT rejected by App-B.
- MA-03 password reset in Collectra never affects App-B account, sessions or credentials; reciprocal test.
- MA-04 verified email in Collectra does not activate App-B account; reciprocal test.
- MA-05 privileged roles and MFA policies cannot leak across applications.
- MA-06 Collectra tenant creation and App-B non-tenant registration both idempotent under retries/concurrency.
- MA-07 same `(issuer,subject)` cannot map to two local users; no email-only auto-link.
- MA-08 notification lookup and delivery cannot cross app/realm; suppress unverified recipients.
- MA-09 local user block and role revocation immediately deny app-specific protected requests without disabling independent other-app account.
- MA-10 SMTP failure, Keycloak outage, duplicate events and partial provisioning recover without cross-app activation.
- MA-11 Collectra legacy user migrates without changing business FKs or restoring legacy passwords.
- MA-12 admin step-up rejects valid JWT without fresh trusted MFA evidence.

## 10. Delivery gates
A. PostgreSQL RED tests: mapping partial unique indexes, registration saga idempotency, authority cutover, row locks, legacy UUID/FK preservation.
B. Two local Keycloak realms with test SMTP and realm-as-code; execute MA-01..MA-05.
C. Collectra principal adapter and app-specific App-B reference adapter/contract; execute MA-06..MA-09.
D. Notification integration, event replay and outage tests; execute MA-08..MA-10.
E. Browser E2E, security review, restore drill, rollback rehearsal and evidence for MA-11..MA-12.
No PASS claim without exact SHA, commands and execution artifacts. No main merge until user authorization.

## 11. Deployment inputs still required
Public hostnames, production SMTP provider/credentials, Keycloak pinned version and immutable digest, secret store and per-app OAuth provider credentials. These are external inputs, not unresolved ownership or isolation rules. Production approval remains blocked until provided and tested.
