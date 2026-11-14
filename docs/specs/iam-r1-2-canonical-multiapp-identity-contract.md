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

## 12. Final architecture hardening decisions (IAM-R1.3 addendum, normative)

This section resolves A1–A6 from the final systems architecture review. It supersedes conflicting earlier sections. Status remains DESIGN BASELINE, not implementation proof.

### A1 — Availability, isolation and disaster recovery
- Realm isolation is **logical**, not fault isolation. All realms sharing one Keycloak cluster, database instance, network or SMTP provider share those failure domains. Document this in the operational risk register.
- Initial production target: Keycloak >=2 replicas across failure zones where supported, reverse proxy with health checks, PostgreSQL managed HA or primary+standby with tested failover, separate DB LOGIN roles for Keycloak, IAM metadata and Notification. Avoid cross-schema privileges; use migration-only DDL credentials.
- Targets (subject to infrastructure sizing validation): IAM availability 99.9% monthly, RPO <=15 minutes, RTO <=60 minutes. Backups encrypted, at least daily full + WAL/PITR sufficient for RPO; retention >=30 days subject to retention policy. Restore drill before launch and quarterly thereafter.
- During Keycloak outage, existing signed access JWTs may be validated using cached JWKS **only while cryptographically valid and local authorization remains ACTIVE**; no new login, reset or MFA step-up. Fail closed for operations needing fresh authentication. Do not extend token TTL or bypass verification. SMTP outage must not activate unverified accounts.
- Capacity, DB connection budgets, session cache topology and key rotation are deployment deliverables with load tests, not assumed from the target.

### A2 — Single authoritative onboarding state owner
- Keycloak owns `email_verified` and pending `VERIFY_EMAIL`. **No application `registration_intent` exists before verified identity** in R1.3. Therefore `PENDING_VERIFICATION` is not a persisted application state.
- Application intent states are `VERIFIED -> PROVISIONING -> ACTIVE | FAILED_RETRYABLE | CANCELLED`. Only trusted issuer/subject with current verified email may create an intent. `ACTIVE` means the local user, tenant (if applicable), identity link and membership committed.
- Use a unique `(application_id,issuer,subject)` registration key and an independent idempotency key scoped to application + actor + operation. Repeated requests return the same intent/result. Define explicit 409 for slug collision; reserve slug transactionally with 24-hour expiry and uniqueness against both active tenants and unexpired reservations.
- The intent and business provisioning occur in application-owned PostgreSQL; a transactional outbox schedules retries and reconciliation. Never claim a distributed transaction with Keycloak. A failed local transaction leaves the intent retryable and no authorized local account.
- App-B's onboarding has no tenant/slug unless App-B's own contract explicitly opts in.

### A3 — Identifier ownership and trust boundaries
| Identifier | Authority | Trust rule |
|---|---|---|
| `applicationId` | Platform Application Registry | server-resolved from registered OIDC client/audience and deployment config; never blindly accepted from payload |
| `issuer` | Keycloak realm | exact allowlist, signature and audience validation |
| `subject` | Keycloak realm | valid only together with issuer |
| `localUserId` | application-owned DB | resolved from active identity mapping |
| `tenantId` | Collectra | server-selected and checked against membership |
| `membershipId` | Collectra | resolved from active tenant membership, not a JWT/client assertion |
- No global user IDs, roles, tenants or automatic email matching. Notification routing uses authenticated application identity and tenant-scoped server-side recipient resolution.
- Platform operator authorization is separate from application end-user permissions; application administrators cannot self-provision realms or change shared SMTP credentials.

### A4 — Recoverable credential migration protocol
- Persist `identity_migration_job(id, local_user_id, issuer, subject?, state, attempt_count, last_error_code, created_at, updated_at)` with unique active job per local user. States: `PREPARED -> KEYCLOAK_IDENTITY_READY -> OWNERSHIP_VERIFIED -> CUTOVER_COMMITTED` or `RETRYABLE_FAILURE / MANUAL_REVIEW`.
- Keycloak identity provisioning is idempotent by persisted job correlation; after timeouts reconcile by trusted Keycloak ID and job marker, **never by email alone**. If Keycloak user exists but local commit fails, retry reconciliation, do not provision another identity.
- Acquire application user row lock for final cutover; in one DB transaction set credential_authority=KEYCLOAK, clear password_hash, revoke local refresh sessions, increment authorization_version, activate identity link and audit. Legacy password operations lock/check authority in the same transaction.
- A migration may not commit before ownership proof and verified email. On failed cutover keep legacy authority only if it was never committed; after commit never restore old password hashes. Detect and alert on stranded jobs.
- Validate local JWT invalidation policy on exact runtime: revoked refresh sessions are not sufficient to invalidate already issued access JWTs. For protected requests, enforce local account status and authorization version; maximum residual window must be bounded and measured.

### A5 — Privileged MFA assurance contract
- Keycloak is the MFA credential authority, but **application business roles define privileged operations**. A realm-wide optional MFA policy does not satisfy mandatory admin MFA.
- For `PLATFORM_SUPER_ADMIN`, `TENANT_ADMIN` and App-B-declared privileged roles, require server-verified assurance equivalent to a completed second factor, with `auth_time` no older than **10 minutes** for high-risk actions. Define a pinned realm-specific mapping of Keycloak authentication flow outcomes to signed `acr/amr` claims; never assume generic claim presence implies MFA.
- If claims are missing, stale, ambiguous or cannot be independently verified against pinned flow, deny the privileged action and trigger Keycloak step-up. After role promotion/demotion, immediately re-evaluate local privileges and require fresh step-up; never trust cached admin status.
- Administrator MFA reset: two authorized approvers, identity verification, immutable audit, recovery session with limited scope, forced MFA re-enrollment. No email-only bypass or self-approval.
- Acceptance includes normal user promoted to admin while logged in, admin loses TOTP, stale token after demotion, and spoofed/absent `amr`.

### A6 — Application Registry and provisioning contract
- Implement a small internal **Application Registry module** in shared IAM infrastructure; do not create a new network microservice at R1.3. It stores `application_id`, realm issuer, lifecycle state, allowed client IDs/audiences, owner, notification policy, config version and audit timestamps. No credentials, passwords or global user directory.
- States: `REGISTERED -> CONFIGURING -> ACTIVE -> SUSPENDED -> DECOMMISSIONED`. Only platform operators with two-person approval may activate, suspend or decommission applications. Decommission requires retention/export approval, token/client disablement, notification suppression and documented data deletion.
- Provisioning workflow: register app -> reserve unique realm identifier -> provision Keycloak realm and OIDC clients from versioned config -> configure SMTP/MFA/policies -> validate issuer/audience and isolation -> register Notification Service routing -> activate after tests. All operations idempotent and audited; partial failures leave CONFIGURING, never ACTIVE.
- Each app owns its local profile, permissions, user IDs and business schema. Registry is **control plane**, not an online dependency for every business request; distribute versioned configuration to runtime with safe invalidation and suspension semantics.
- Client secrets, SMTP credentials and social IdP secrets remain in secret store. Rotate and revoke by application without changing other realms. Decommission must never cascade-delete another application's users.

## 13. Operational and functional release tests added by IAM-R1.3
- HA-01 Keycloak node loss with valid cached JWKS: existing authorized API request works; new login/step-up fails safely if whole service unavailable.
- HA-02 PostgreSQL failover, backup restore, RPO/RTO measured and recorded.
- HA-03 SMTP outage: pending verification cannot activate; retry and alert observed.
- ONB-01 duplicate verified registration requests yield one intent, tenant and admin membership.
- ONB-02 slug reservation collision/expiry and concurrent claims are deterministic.
- ONB-03 failed provisioning leaves retryable intent and no partial ACTIVE account.
- ID-01 malicious client-supplied applicationId/tenantId/membershipId cannot change authorization.
- MIG-01 timeout after Keycloak identity creation reconciles to same subject; no email-based auto-link.
- MIG-02 concurrent legacy reset and credential cutover yield one valid credential authority.
- MFA-01 user promoted to admin requires new step-up; old JWT alone is insufficient.
- MFA-02 spoofed/absent/stale assurance claims and demoted admin denied.
- APP-01 App-B provisioning does not change Collectra realm, clients, roles or notifications.
- APP-02 SUSPENDED app denied new auth and notifications according to published suspension SLA.
- APP-03 failed realm provisioning cannot activate application; retry is idempotent.
- APP-04 secret rotation/decommission does not affect other applications.
- Every case requires actual integration evidence, SHA, commands, assertions and CI artifacts. Production gate: zero open P0/P1, independent S2 review, restore drill and threat-model signoff.

## 14. Remaining prerequisites and approval
- Pin actual Keycloak version/digest and prove the exact MFA claim mapping, token revocation and realm configuration through integration tests.
- Choose actual infrastructure topology, DB privileges, secret store, sender domain and SMTP provider; measure stated SLO/RPO/RTO targets.
- Inventory actual Collectra React routes and any mobile clients, and define App-B's real business contract when its repository becomes available.
- The architecture decision set is complete for an implementation baseline; **no implementation, production security or test PASS is implied**.

## 15. Product boundary amendment — App Common / Collectra (normative)
**ADR-IAM-004 is authoritative for product ownership and deployment.** The two named products are **App Common** (independent central IAM, notification and operations platform with its own repository, API, database ownership, admin UI and CI/CD) and **Collectra** (independent business application and first consumer). App Common is **not** a module inside `collectra-api`; its future repository is not yet provisioned. All references to 'Collectra Central IAM Platform' as the platform product are superseded. The `App-B` realm in test matrices is a **test fixture for future-app isolation**, not a committed third product. App Common operates Keycloak and application onboarding; Collectra retains local users, tenants, roles, permissions and business data. See `docs/adr/adr-iam-004-app-common-platform-boundary.md` for normative deployment, trust and acceptance contracts.

## 16. OAuth2 JWT Bearer token contract (normative)
ADR-IAM-005 is authoritative: Keycloak exclusively issues signed JWT Access Tokens; App Common administers realms, clients, MFA, policies and lifecycle without minting end-user JWTs. Collectra and App Common APIs are separate OAuth2 Resource Servers with distinct issuer/audience allowlists. Collectra validates cryptographic token claims and resolves issuer+subject to local users, tenants, memberships and business permissions. App Common APIs validate their own audiences and enforce operator/service scopes. Browser uses Authorization Code + PKCE; services use Client Credentials. Reject wrong-audience/issuer, expired, invalid-signature, ID/refresh tokens and cross-application use. See `docs/adr/adr-iam-005-keycloak-jwt-bearer-contract.md` for JWT-01..JWT-14 acceptance tests. Legacy application-signed JWTs are migration-only, never the target design.
