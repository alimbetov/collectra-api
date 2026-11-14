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
