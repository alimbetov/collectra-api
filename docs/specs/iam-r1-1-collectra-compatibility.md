# IAM-R1.1 — Collectra Functional Compatibility Contract

Status: REVIEWED AGAINST SELECTED SOURCE FILES / IMPLEMENTATION NOT STARTED.
Companion: iam-r1-1-email-recovery-mfa.md.
Scope: preserve existing Collectra behavior while replacing credential authority.

## 1. Source-grounded baseline
- `AuthController` exposes `POST /api/v1/auth/tenants/register`, `/login`, `/login/by-slug`, `/refresh`, `/logout`, `/logout-all`.
- `PlatformAuthController` exposes distinct `/api/v1/platform/auth/login|refresh|logout|logout-all`.
- `AccountLifecycleController` exposes invitation acceptance and password forgot/reset.
- `TenantInvitationController` exposes create/list/revoke, guarded by `ROLE_HUMAN` and `USER_INVITE`.
- `CurrentUserController` exposes profile, change password, sessions and session revocation.
- `UserLifecycleService` issues local JWTs upon invitation acceptance, sends `UserInvitationCreated` and `PasswordResetRequested` outbox events, and revokes local refresh sessions after password changes.
- `UserAccount` has local UUID, tenant_id, required email and password_hash, role/status, authorization_version, email_verified_at. `completeProfile()` currently sets `emailVerifiedAt=now()` without a verification challenge.
- `AuthService` issues local JWTs and refresh sessions; local subject is the local user UUID. `PlatformAuthService` uses tenant_id IS NULL and platform roles.

## 2. Compatibility decisions
### COL-01 — Preserve local user identity and business authorization
- Preserve `user_accounts.id`, tenant membership IDs, historical FKs, role tables, permission checks and authorization_version semantics.
- Resolve trusted Keycloak `(issuer, subject)` to a local user UUID **before** evaluating Collectra authorization. Never interpret Keycloak `sub` as a local UUID or trust a client-provided local user ID.
- Keep local tenant membership and platform-role authorization as separate contexts. Reject absent/inactive membership or inactive tenant; do not authorize from Keycloak roles alone.
- Legacy JWTs and Keycloak JWTs require explicitly separate validation chains during migration, with no issuer confusion.

### COL-02 — Password and user schema migration
- Add an additive, unique `collectra_external_identity(issuer, subject, user_account_id, linked_at, status)` mapping; enforce no automatic email-based linking.
- Keep `password_hash` required for legacy accounts until a staged migration. For migrated accounts, migrate to nullable `password_hash` with a DB CHECK requiring a valid credential authority marker; never write a fake usable password.
- Introduce `credential_authority=LEGACY|KEYCLOAK` with backfill and constraints; each user has exactly one active authority. Password reset/change endpoints must dispatch only to the designated authority.
- `email_verified_at` may be set only from a verified Keycloak identity event or verified legacy proof, never by profile completion. Repair pre-existing verification timestamps through a reviewed backfill; do not automatically trust old timestamps.

### COL-03 — Tenant registration and invitations
- Tenant registration remains a business operation; after Keycloak email verification, provision the tenant, user and TENANT_ADMIN membership atomically/idempotently. Never issue application access tokens for unverified users.
- Invitations remain tenant-scoped and preserve invited role IDs and expiry/revocation. Keycloak identity creation or linking must require authenticated acceptance of the invitation token and verification of the invited email; a matching email alone does not grant membership.
- Existing invitation acceptance must not return local access/refresh tokens for Keycloak users. Client moves to OIDC login, then completes server-side invitation claim.
- Retry and race tests must cover duplicate tenant registration, duplicate invitation acceptance and linking an identity already bound to another local user.

### COL-04 — Platform administrators
- Keep `tenant_id IS NULL` platform accounts and platform-specific role checks. Require TOTP MFA for platform super-admin and tenant admins.
- Platform sessions and tenant sessions remain separate authorization contexts even within the same Collectra realm. Never allow a platform token to be used as a tenant membership token.
- Platform bootstrap and admin recovery require independent approval and audit; no self-assignment of PLATFORM_SUPER_ADMIN via Keycloak profile.

### COL-05 — Existing endpoint migration contract
| Existing route | During migration | After cutover |
|---|---|---|
| `/api/v1/auth/tenants/register` | feature-flagged legacy route; new OIDC onboarding separate | business onboarding only, no password/JWT issuance |
| `/api/v1/auth/login`, `/login/by-slug` | legacy accounts only | 410 Gone after deprecation window |
| `/api/v1/auth/refresh`, `/logout` | legacy token families only | Keycloak/OIDC session lifecycle |
| `/api/v1/platform/auth/login|refresh|logout` | legacy platform accounts only | OIDC platform authentication with local platform role check |
| `/api/v1/auth/invitations/accept` | legacy flow only | authenticated OIDC invitation claim |
| `/api/v1/auth/password/forgot|reset` | legacy credential authority only | Keycloak-hosted recovery |
| `/api/v1/identity/me/change-password` | legacy only | Keycloak account-management action |
| `/api/v1/identity/me/sessions` and DELETE | local legacy sessions, clearly labeled | Keycloak session management via secured backend mediation |
| `/api/v1/auth/otp/*` | disabled by default pending business-purpose review | purpose-scoped business OTP only, not MFA |

### COL-06 — Notification and email verification
- Existing `UserInvitationCreated` and `PasswordResetRequested` outbox events are **not proof of email delivery**. Audit consumers and recipient lookup before activation.
- Keycloak verification/recovery uses Keycloak SMTP. Collectra notifications use Notification Service and require verified recipient in the Collectra realm.
- No ordinary notification to an unverified email. Never use untrusted request email as recipient without identity verification.
- Email updates must synchronize Keycloak verified email to Collectra projection with audit, idempotency, and race-safe notification recipient resolution.

## 3. Mandatory Collectra regression matrix
- COL-T01 existing user_accounts UUID and all FKs unchanged through migration.
- COL-T02 tenant registration creates exactly one tenant and TENANT_ADMIN membership after verification.
- COL-T03 unverified user cannot access tenant API or claim membership.
- COL-T04 invited user can claim only correct tenant, invited email and role IDs.
- COL-T05 invitation reuse/concurrent acceptance cannot duplicate memberships.
- COL-T06 `USER_INVITE` permission remains enforced; non-inviter denied.
- COL-T07 platform admin requires local platform role and TOTP; no tenant access without membership.
- COL-T08 legacy refresh rotation and logout work only for LEGACY identities during transition.
- COL-T09 Keycloak subject cannot be parsed directly as local UUID; resolver maps correctly.
- COL-T10 wrong issuer/audience and inactive tenant/membership are denied.
- COL-T11 password reset and change cannot cross credential authorities.
- COL-T12 profile update does not mark email verified.
- COL-T13 same email in two realms does not link accounts.
- COL-T14 notification recipient stays within Collectra realm and requires verification.
- COL-T15 legacy and OIDC users can coexist without role escalation.
- COL-T16 rollback never silently restores old passwords for migrated identities.
- COL-T17 authorization_version invalidates applicable cached local authorizations after role/status changes.
- COL-T18 platform and tenant session contexts cannot be interchanged.

## 4. Delivery order
1. Implement regression tests for COL-T01..T18 against existing behavior and desired changes; classify RED intentionally.
2. Add additive Liquibase identity mapping and credential-authority constraints, including backward compatibility.
3. Implement issuer-aware principal resolution and tenant/platform security adapters behind flags.
4. Implement Keycloak registration, invitation claim, email verification and administrator MFA.
5. Integrate React/Flutter journeys and notification recipients; verify real SMTP with a test server.
6. Run PostgreSQL concurrency, security and Browser E2E, preserve evidence, conduct independent S2 review.
7. Canary and controlled cutover; do not merge to main until explicit approval.

## 5. Acceptance
No loss of Collectra business identity, permissions, invitations, tenant/platform separation, audit or sessions without an explicitly approved replacement. Every new Keycloak flow must have an executable functional proof. No claims of PASS without running tests on the exact SHA.
