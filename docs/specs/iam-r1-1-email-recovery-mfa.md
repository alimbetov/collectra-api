# IAM-R1.1 — Email Verification, Password Recovery & MFA

Status: DRAFT / NOT IMPLEMENTED. Repository: alimbetov/collectra-api. Branch: feat/iam-shared-platform.
Scope: isolated Keycloak realms per application; existing Collectra legacy auth preserved until migration gates pass.

## 1. Goal
Provide mandatory verified email, secure password recovery, and optional TOTP MFA for every independent application realm. Prevent cross-realm account/session sharing. Prove user journeys through backend and browser tests.

## 2. Responsibilities
- Keycloak: identity, credentials, email verification, reset-password actions, login and TOTP MFA.
- Collectra API: tenant membership, business roles, issuer+subject to local user mapping, authorization. Preserve user_accounts UUIDs and historical foreign keys.
- Notification Platform: business email, SMS/push later, delivery attempts and status. Keycloak transactional emails may use dedicated SMTP configuration; do not make Keycloak password reset depend on an unfinished Notification API.
- PostgreSQL: dedicated keycloak schema and runtime permissions; no global cross-application identity table.

## 3. Mandatory registration and verified email
- Email required for local registration, invitation acceptance and social login completion in each realm.
- Configure Verify Email and required action; verify delivery before activation and ordinary email notifications.
- Do not treat presence of an email claim as proof of verification. Provider claims require trust mapping and a documented policy; missing/unverified email triggers completion and verification.
- Same email in separate realms represents independent accounts. No automatic cross-realm linking or SSO.
- Email changes require verification of the new address before use as ordinary notification destination.
- Normalize email consistently. Reject missing/invalid email at API boundaries; retain per-realm uniqueness policy.
- Verification emails are permitted before verification; marketing requires separate consent.

## 4. Forgot password and reset
- Provide a public Forgot Password UX with generic, enumeration-resistant response, regardless of whether an account exists.
- Keycloak issues expiring single-use reset action links; never return reset tokens to production clients or log them.
- Rate-limit by account, IP, realm and risk signals with distributed enforcement; include anti-abuse monitoring.
- Reset validates action token and policy, changes credential, and revokes sessions according to explicit security policy; test actual access-token residual lifetime.
- Invalid, expired, reused and cross-realm links fail without account takeover.
- Notify the verified email address after successful password change without disclosing secrets.
- Existing /api/v1/auth/password/forgot and /reset remain under a migration flag until clients switch; do not silently leave two active password authorities.

## 5. OTP and MFA
- Use Keycloak TOTP as first MFA factor, with enrollment, challenge, recovery and revocation flows; evaluate passkeys/WebAuthn as follow-up.
- Legacy OtpService is not proof of login MFA. Disable use of developmentCode/expose-otp in non-local environments.
- Any business-action OTP must be purpose-, realm-, subject- and tenant-bound, single-use, short-lived, delivered over an actual verified channel and consumed atomically.
- Store only protected OTP verification material; use constant-time comparison and distributed abuse limits. Add row-locking/optimistic concurrency tests.
- Never reuse an OTP challenge across actions or applications.

## 6. Integration contracts
- React/Flutter: Authorization Code + PKCE, login, verify-email pending screen, forgot-password request, reset flow, TOTP enrollment and challenge, logout.
- Backend: validate issuer, signature, audience, expiry; map (issuer, subject) to Collectra UUID, then enforce local membership and RBAC.
- Notification API: require application/realm context, resolve verified recipient, deny cross-realm lookup, suppress ordinary messages to unverified addresses, record delivery attempts.
- SMTP: secrets only from runtime secret store; configure sender, SPF/DKIM/DMARC, bounce/complaint processing, retry policy.
- Outbox events must not contain raw OTP or password reset tokens; Keycloak reset delivery may use its own SMTP.

## 7. Delivery stages and exit gates
1. IAM-R1.1A: baseline tests for existing OTP, reset tokens, rate limiter, event consumer and exposed development secrets; document migration risks.
2. IAM-R1.1B: deploy Keycloak with isolated realms, SMTP, Verify Email, Forgot Password, TOTP; configuration reproducible and secret-free in Git.
3. IAM-R1.1C: OIDC resource-server integration and React flows behind flags; preserve legacy users and business IDs.
4. IAM-R1.1D: Notification recipient verification and app isolation; delivery proof through SMTP test server.
5. IAM-R1.1E: dual-path migration tests, independent security review, rollback, then disable legacy credential authority.

## 8. Mandatory automated tests
- REG-01 missing email rejected; REG-02 invalid email rejected; REG-03 unverified user not activated; REG-04 verified email activated; REG-05 provider missing/unverified email; REG-06 same email in two realms remains isolated.
- REC-01 existing/nonexisting email indistinguishable externally; REC-02 reset mail delivered; REC-03 expired token rejected; REC-04 token reuse rejected; REC-05 concurrent redemption only one success; REC-06 cross-realm reset denied; REC-07 refresh sessions revoked and access-token behavior documented; REC-08 rate limits.
- OTP-01 valid TOTP succeeds; OTP-02 invalid/expired TOTP denied; OTP-03 MFA enrollment/recovery; OTP-04 legacy challenge atomic consume; OTP-05 wrong purpose/subject/tenant denied; OTP-06 distributed rate limits.
- NOTIF-01 verified recipient delivered; NOTIF-02 unverified recipient suppressed; NOTIF-03 no cross-application recipient access; NOTIF-04 SMTP failure and retry; NOTIF-05 no secrets in logs/events.
- SEC-01 wrong issuer denied; SEC-02 wrong audience denied; SEC-03 inactive membership denied; SEC-04 disabled account denied; SEC-05 CSRF/redirect URI/PKCE checks.
- Run Java unit tests, PostgreSQL Testcontainers, Keycloak integration tests and React Browser E2E in CI. Capture exact commit, commands, reports and failures. No PASS claim without execution.

## 9. Operational acceptance
- End-to-end: register -> receive verification email -> verify -> login -> MFA (if enabled) -> authorized Collectra action -> receive ordinary notification -> forgot password -> reset -> login with new password.
- Repeat same flow in second realm with same email; no shared account, session, or recipient data.
- Migration and rollback rehearsed with existing users and UUID/FK integrity; independent security sign-off.
- Measured email delivery and retry, audit events, alerting, and tested secret rotation.
- Definition of Done: executable evidence for all mandatory cases, no P0/P1 findings, production-safe configuration. Otherwise status remains PARTIAL / NOT VERIFIED.

## 10. Explicit exclusions
- SMS as primary MFA, cross-realm SSO, global user identity merge, mass migration of existing user IDs, removal of legacy endpoints before verified cutover.

## 11. Approved implementation decisions (R1.1 contract v1.1)

These decisions supersede ambiguous statements above. They are **design defaults adopted for implementation**, not evidence of production readiness. Changing a security-sensitive default requires an ADR and tests.

### DEC-01 — Account and realm boundaries
- One Keycloak deployment, one realm per independent application, separate OIDC clients for browser and mobile; no cross-realm identity federation or SSO.
- Within one realm, normalized email must be unique. Do not automatically link local/social accounts by matching email. Linking requires an authenticated, explicit, audited flow.
- No shared global user ID. Collectra preserves existing `user_accounts.id` and business foreign keys; mapping unique on `(issuer, subject)`, and unique on `(issuer, local_user_id)` unless an approved migration needs multiple historical identities.
- A second realm with the same email is an independent user. Keycloak credentials are never copied between realms.

### DEC-02 — Email policy and activation
- Every human user must have a syntactically valid, nonblank email and `email_verified=true` before completing the first application session or receiving ordinary notifications. Pending-verification users can only access verification/recovery flows.
- Use Keycloak `VERIFY_EMAIL` required action and `verifyEmail=true` for each realm. Treat third-party provider `email_verified` as untrusted by default: send Keycloak's own verification email, including Google, until an explicit provider trust decision is separately reviewed.
- Per-realm email uniqueness is case-insensitive after trimming and Unicode normalization policy; do not perform Gmail-specific dot/plus alias stripping.
- Email change is a two-phase process: keep the previously verified notification address until new email is verified, then atomically switch. If old email is compromised, suspend ordinary email notifications during the change; support recovery by an audited admin procedure.
- Email verification link lifetime: **15 minutes**; resend cooldown **60 seconds**, max **5 per hour per account** and **20 per hour per IP** (starting thresholds, load-tested before launch). Never log links or codes.
- Verify-email and security/recovery messages are permitted without marketing consent. Marketing requires separate affirmative consent.

### DEC-03 — Password recovery
- Keycloak is the sole credential authority **after cutover**. Use Keycloak Forgot Password / UPDATE_PASSWORD action email and hosted action pages; the application must not generate its own Keycloak reset tokens.
- Reset action link lifetime: **15 minutes**, single use. Configure the relevant Keycloak action-token lifespan for the pinned version and verify it in integration tests.
- Return the same user-facing response for known/unknown accounts. Do not expose account existence via response status/body, and test timing/rate-limit side channels.
- Reset invalidates active user sessions/refresh tokens via the Keycloak-supported mechanism; validate exact behavior for pinned version. Existing JWT access tokens may remain usable until expiry: set access token lifetime **5 minutes** and enforce a stricter session revocation mechanism if immediate invalidation is a product requirement.
- New password policy: minimum **12 characters**, maximum supported **at least 128**, block common/compromised passwords where supported, prohibit reuse of last **5** passwords; never truncate input silently. Verify Keycloak configuration capabilities before asserting enforcement.
- Legacy reset endpoints are **disabled for newly migrated users** and retained only for explicitly flagged legacy identities; no concurrent password authorities for one user.
- Security notification on successful reset uses the verified contact address; no password or reset token in payload.

### DEC-04 — MFA
- Initial MFA: **TOTP**, 6 digits, 30-second step, SHA-1 interoperability profile unless the pinned authenticator/client matrix supports a stronger algorithm; drift window at most **one time step** each side. Keycloak enforces replay protection and throttling as supported by the pinned version, with integration proof.
- MFA is **mandatory for platform administrators and tenant administrators**, optional for ordinary users at R1.1; enforcement occurs in Keycloak authentication flows, not only in React.
- Recovery uses **single-use backup codes if supported by the pinned Keycloak version**, otherwise an identity-verified administrative reset requiring two-person approval and audit. Never allow email alone to bypass administrator MFA.
- Re-enrollment and disabling MFA require fresh authentication and step-up verification. Record event metadata without TOTP secret/code.

### DEC-05 — Legacy business OTP
- Existing `OtpService` is **not** an MFA implementation. Keep it behind a disabled-by-default feature flag until a concrete business purpose is approved.
- Any enabled business OTP must bind `issuer, tenantId, subjectId, purpose, challengeId`, enforce a five-minute lifetime and max five failed attempts, store only a keyed verifier, consume atomically under row lock or optimistic version check, and produce a one-time action-scoped proof.
- OTP creation and verification throttles must be distributed (e.g. Redis with atomic operations); in-memory limits are not acceptable for multi-instance production. Suppress `developmentCode` in all non-test environments and fail startup if unsafe exposure flags are enabled.
- Delivery via verified email is required for email OTP. No delivery means challenge creation fails or remains non-usable; no silent success.

### DEC-06 — Notification and SMTP boundaries
- Keycloak uses its **own configured SMTP transport** for verification, recovery, and MFA/security emails; do not block IAM on Notification Service availability.
- Notification Service sends application/business messages only, resolves recipients from a realm-scoped trusted directory, and re-checks verification status at send time. Never accept an arbitrary client-supplied destination as proof of recipient ownership.
- Queue states: `QUEUED`, `SENDING`, `PROVIDER_ACCEPTED`, `DELIVERED`, `FAILED_RETRYABLE`, `FAILED_FINAL`, `SUPPRESSED`; `DELIVERED` only with provider evidence. Track bounces, complaints and suppression lists by realm.
- Retry transient provider failures with exponential backoff and jitter, maximum **5 attempts**, then DLQ/manual reprocessing. Idempotency key scoped to `(realm, notificationId, channel)`. Never include raw reset links, OTPs or credentials in application Outbox.
- SMTP secrets through environment/secret store, TLS enforced, sender domain authenticated (SPF, DKIM, DMARC). Email delivery must be demonstrated with a test SMTP server before real-provider rollout.

### DEC-07 — Security and browser contracts
- React and Flutter use Authorization Code + PKCE (S256); no implicit grant, no resource-owner-password grant. Use system browser for mobile; exact redirect URI allowlists, secure cookies/session handling as appropriate, no tokens in URLs or browser localStorage.
- Backend validates JWKS signature, `iss`, `aud`, expiry, intended client and realm, and resolves Collectra RBAC from its own database. An authenticated Keycloak identity without active Collectra membership gets **403**.
- Restrict CORS and enforce HTTPS outside local development; CSRF defense for cookie-based endpoints. Keycloak logout must terminate relevant SSO sessions and application sessions.

### DEC-08 — Migration, rollback and operations
- Migration order: inventory legacy accounts -> introduce mapping -> stage Keycloak identity -> verify email -> link by an authenticated/approved process -> switch per-user flag -> observe -> disable legacy path.
- Do **not** bulk-link by email. Do not rewrite `user_accounts.id` or historical foreign keys. Keep migration idempotent and auditable.
- Rollback before credential cutover: restore legacy routing. After credential cutover: **do not silently reactivate old passwords**; use controlled account recovery or explicit re-enrollment.
- Pin Keycloak container version/digest and document supported PostgreSQL, provider and action-token settings. Backup and restore both Keycloak and Collectra mapping databases; test restore on disposable infrastructure.
- Production rollout: canary realm/test tenants first, then phased rollout. Stop rollout on P0 security failures, cross-realm leakage, broken password recovery, or missing notification verification.

## 12. API and event contracts

- `POST /api/v1/auth/password/forgot` and `POST /api/v1/auth/password/reset`: legacy-only under explicit feature flag; migrated users are redirected to Keycloak-hosted recovery UX, never proxied raw reset tokens.
- `GET /api/v1/users/me` (or existing equivalent): authenticated application profile, includes email verification status only if derived from trusted realm identity, not client input.
- Notification request requires server-resolved `applicationId/realm`, authenticated actor, recipient local ID, template ID, purpose and idempotency key. Arbitrary `toEmail` is not accepted on end-user requests.
- Security events: `EmailVerificationRequested`, `EmailVerified`, `PasswordResetRequested`, `PasswordResetCompleted`, `MfaEnrolled`, `MfaReset`, `NotificationSuppressed`. Events carry identifiers, realm and timestamp only; never codes, secrets, action links or raw tokens. Keycloak event integration is required before claiming these events exist.

## 13. Expanded acceptance and release gates

- **GATE-A (baseline):** record existing routes, DTOs, SQL schema, OTP lifecycle, reset lifecycle, legacy tests and defects. Reproduce negative concurrency cases before fixes.
- **GATE-B (Keycloak):** two isolated realms, required email, SMTP verification, recovery, TOTP, admin MFA, anti-enumeration and realm separation proved with actual Keycloak version.
- **GATE-C (integration):** OIDC issuer/audience and business RBAC checks; browser and mobile flows; no automatic linking by email.
- **GATE-D (notifications):** verified-only business email, provider acceptance vs delivery semantics, retries, DLQ, bounce suppression, tenant/realm isolation.
- **GATE-E (migration):** UUID/FK preservation, legacy/Keycloak mutually exclusive credential authority per account, concurrency and rollback rehearsals, independent security approval.
- Add tests for simultaneous email-change/send race, repeated verification link, reset token logging, admin MFA recovery, OTP brute-force across two nodes, and mail-provider outage.
- Capture CI job URL, SHA, exact commands, test counts and evidence for each gate. No release while any P0/P1 security defect is open.

## 14. Remaining external prerequisites (not undecided architecture)

The implementation needs actual SMTP provider credentials and sender domain, Google/Apple provider credentials if social login is enabled, public HTTPS IAM hostnames, environment secrets, and a chosen pinned Keycloak version. These are deployment inputs; use placeholders in Git and never commit secrets. Until provided, integration can be verified using local Keycloak + test SMTP and social-login stubs, but production readiness cannot be claimed.
