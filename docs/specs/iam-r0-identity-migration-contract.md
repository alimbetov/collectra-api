# IAM-R0.2 — Collectra identity migration contract

Status: PROPOSED / IMPLEMENTATION BLOCKED UNTIL SECURITY REVIEW
Baseline: main@0b3f553d0c09fe77346341efa9dffa280958ee5f

## Existing schema evidence

- `001-foundation.sql`: `user_accounts(id, tenant_id, email, password_hash, role, status)`, `refresh_sessions(user_id, tenant_id, token_hash, family_id, revoked_at)`.
- `002-identity-rbac.sql`: `tenant_memberships`, `roles`, `membership_roles`, `platform_user_roles`, `authorization_version`.
- `006-platform-identity.sql`: platform users may have NULL tenant_id; sessions distinguish PLATFORM and TENANT contexts.
- No existing table or FK is to be dropped or rewritten by IAM-R0.

## Proposed mapping (NOT YET MIGRATED)

`collectra_external_identity` in the **current Collectra-owned schema**, not the shared `identity` schema:

- `id UUID PRIMARY KEY`
- `user_account_id UUID NOT NULL REFERENCES user_accounts(id)`
- `issuer VARCHAR(512) NOT NULL`
- `subject VARCHAR(255) NOT NULL`
- `provider VARCHAR(80) NOT NULL` (e.g. KEYCLOAK; external Google identities remain managed by Keycloak)
- `linked_at TIMESTAMPTZ NOT NULL`
- `unlinked_at TIMESTAMPTZ NULL`
- `created_by UUID NULL`

Constraints:
- Unique active `(issuer, subject)` across the Collectra identity map.
- A user may have multiple links only by explicit approved account-linking flow.
- No automatic link by matching email, phone, or social-provider identifier.
- Validate issuer against the exact Collectra realm URL; reject other application realms and wrong audience.
- Never persist raw access tokens, refresh tokens, OTP or provider secrets in the mapping.

## Migration sequence

1. Inventory all login, platform and tenant user paths and the frontend token lifecycle.
2. Add mapping table additively; write RED tests for duplicate issuer/subject, wrong realm, disabled user, tenant mismatch, account linking, session revocation and rollback.
3. Introduce Keycloak authentication behind disabled feature flag, preserving legacy flow during verified migration.
4. For existing users, require a secure ownership proof / account activation. Password hashes are not assumed compatible with Keycloak.
5. Maintain existing `user_accounts.id` and business foreign keys. Resolve authenticated subject to local account, then apply existing membership, status and permission checks.
6. Make legacy JWT acceptance explicit, bounded and auditable; prevent fallback from a failed OIDC authentication into legacy authorization.
7. After full backend, browser and cross-tenant regression, disable legacy login and revoke or expire legacy sessions using a documented cutover.
8. Rollback must restore the prior auth routing without dropping new mapping or changing existing user IDs.

## Mandatory tests

- Same email in Collectra and App B maps to independent accounts.
- App B issuer and token rejected by Collectra even when email/subject coincide.
- Same `(issuer, subject)` cannot map to two Collectra users.
- Unverified email does not link accounts.
- Disabled user, revoked membership and stale authorization version deny access.
- Platform user cannot inherit tenant privileges.
- Concurrent first login cannot create duplicate mapping.
- Revoked/expired legacy refresh session cannot be reactivated.
- Existing customer, invoice, collection and communication business relations remain intact.

Gate: NO Keycloak migration or login cutover before independent security review and executable PostgreSQL + HTTP E2E evidence.

## Mandatory verified email for every application user (IAM-R0.3)

- Each realm (Collectra, App B, future applications) MUST require a non-empty email for user registration and account activation. Keycloak realm email verification is mandatory; social-provider email claims must be evaluated for verified status, not trusted merely because present.
- Social providers that return no email, an unverified email, or a private relay address require an explicit completion/verification flow before normal application activation. A verified Apple private relay address is acceptable if it is deliverable and the application's sender is properly configured.
- No automatic identity linking across realms or on email equality. Email is contact information, never a cross-application identity key.
- Keep an application-scoped verified email projection only where required, with a defined synchronization owner and update protocol. Keycloak is the authentication identity authority; application business records remain local.
- Email change requires verification of the new address before using it for ordinary notifications; do not silently replace the verified destination. A user who loses access to their email must have a controlled recovery path.
- Notification API MUST resolve recipient addresses in the authenticated application/realm context and MUST reject or suppress ordinary email delivery to an unverified or missing address. Verification emails are an explicit exception. Marketing messages additionally require purpose-specific consent and suppression checks.
- Do not grant access to a newly created application account until the registration and verified-email policy for that realm has passed.
- Avoid global email uniqueness across realms. Within each realm, adopt a documented email uniqueness/account-linking policy and test its behavior for local and social registration.
- Require explicit delivery observability: accepted/queued/sent is not proof of inbox delivery.

### Mandatory acceptance tests

1. Email/password registration without email is rejected.
2. Google/Apple login without a verified email enters verification/completion, not an active account.
3. Verified social email permits activation without bypassing application membership checks.
4. Same verified email in Collectra and App B creates two independent realm identities.
5. Email change does not redirect notifications until new address verification succeeds.
6. Notification API cannot read another application's recipient email.
7. Ordinary notifications to missing/unverified email are suppressed; verification email remains allowed.
8. Revoked/blocked user and marketing opt-out policies are enforced separately from email verification.

## IAM-R0.3 implementation evidence and outstanding integration

- Added `VerifiedEmailPolicy` and `VerifiedEmailPolicyTest` for issuer-scoped, verified-email eligibility.
- These are **pure unit-level primitives only**, not wired into current registration or message delivery. Do not mark mandatory email or email verification as enforced in production.
- The lightweight syntax check is not a replacement for Jakarta Bean Validation `@Email` at the API boundary or Keycloak's verified email state.
- Pending: configure Keycloak per-realm `registrationEmailAsUsername`/required email and verify-email flow; decide supported provider claim mapping; implement recipient resolution and verification-message exception; apply policy in Notification API; test email change, account disable, and application isolation through HTTP E2E.
- Pending: execute Maven tests/CI on exact SHA; no test execution is claimed by this commit.
