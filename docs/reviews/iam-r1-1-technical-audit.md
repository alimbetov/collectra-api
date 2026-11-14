# Independent technical review — IAM-R1.1 / Collectra compatibility

Status: CHANGES REQUESTED / DESIGN HOLD. Review date: 2026-11-14.
Reviewed: docs/specs/iam-r1-1-email-recovery-mfa.md, iam-r1-1-collectra-compatibility.md, iam-r0-identity-migration-contract.md; selected source files AuthController, AuthService, PlatformAuthController, PlatformAuthService, AccountLifecycleController, UserLifecycleService, CurrentUserController, TenantInvitationController, UserAccount, OtpService, OtpChallenge.
Scope: static design and code inspection only. No runtime, CI, PostgreSQL, Keycloak, browser or SMTP verification. This is not independent external S2 approval.

## P0 — blocking decisions

**IAM-AUD-01: Email verification contradicts tenant onboarding lifecycle.**
R1.1 requires verified email before activation; COL-03 proposes creating tenant/user/membership only after verification, while current `AuthService.register` creates tenant and ACTIVE user and returns JWT immediately. No durable pre-tenant onboarding state, reservation of slug, expiry, idempotency or cleanup protocol is specified.
**Required:** define `PENDING_EMAIL_VERIFICATION -> VERIFIED -> PROVISIONING -> ACTIVE` onboarding state and durable registration intent keyed by realm+Keycloak subject+idempotency key. Decide slug reservation/expiration, tenant creation transaction, duplicate callbacks and compensating actions. No application JWT before ACTIVE.

**IAM-AUD-02: External identity uniqueness contracts conflict.**
R0 migration contract allows multiple approved links per local user; R1.1 DEC-01 demands unique `(issuer,local_user_id)` unless exception; compatibility document omits provider, unlinked_at, created_by and active-only uniqueness. A DB implementation cannot satisfy all versions simultaneously.
**Required:** canonical table definition in one document, active-only partial unique index on `(issuer,subject)`; explicit cardinality and linking policy; immutable audit trail, provider and unlink semantics; refer to same schema and constraints everywhere.

**IAM-AUD-03: Principal compatibility strategy is incomplete.**
Current controllers parse `JwtAuthenticationToken.getToken().getSubject()` as local UUID and `membership_id` claim as UUID. Resolving identity 'before authorization' is insufficient without a concrete adapter or systematically replacing these consumers.
**Required:** define `CollectraPrincipal(localUserId, issuer, subject, tenantId, membershipId, contextType, authMethod)`, conversion and per-request membership lookup, migration strategy for all call sites, and tests proving no Keycloak sub is interpreted as local ID. Never rewrite/forge JWT subject as a shortcut.

**IAM-AUD-04: Credential authority transition lacks atomic protocol.**
Current `UserAccount.password_hash` is non-null and `UserLifecycleService.changePassword/resetPassword` use local hashes. Documents propose nullable password_hash and authority flag, but no exact constraint, cutover transaction, fallback prevention, concurrent reset behavior, or failure recovery.
**Required:** migration DDL/backfill and `CHECK ((credential_authority='LEGACY' AND password_hash IS NOT NULL) OR (credential_authority='KEYCLOAK' AND password_hash IS NULL))`; prove transition sequencing and existing JWT invalidation; rollback after cutover must not restore legacy credentials.

**IAM-AUD-05: Administrator MFA policy is not enforceable as written.**
Platform/tenant admin roles are owned by Collectra, not Keycloak. A realm-level optional TOTP flow cannot guarantee MFA for locally privileged users, especially after role changes or platform/tenant context switches.
**Required:** server-enforced privileged step-up using trusted `acr/amr/auth_time` evidence from a pinned Keycloak flow, explicit max authentication age, role-change session invalidation, administrator bootstrap and break-glass workflow. No trust in self-asserted MFA claims.

## P1 — must resolve before implementation acceptance

**IAM-AUD-06: Email verified projection and lifecycle.**
`UserAccount.completeProfile` sets `emailVerifiedAt` without proof. Event ordering, loss, replay and two-phase email change are unspecified.
**Required:** remove implicit verification; specify trusted Keycloak event source, event IDs/versions, replay handling, authoritative read fallback and notification send-time check.

**IAM-AUD-07: Security session revocation semantics.**
Docs acknowledge five-minute JWT residual validity but demand 'revoked sessions' without a precise revocation SLA. Current `authorization_version` behavior must be maintained.
**Required:** define which actions require immediate API denial (blocked user, removed role, reset after compromise), server-side version/status checks, max stale window, Keycloak session logout tests and cache invalidation.

**IAM-AUD-08: Notification delivery model is overclaimed.**
SMTP acceptance does not prove inbox delivery; `DELIVERED` cannot be asserted from generic SMTP alone. Existing Outbox events contain IDs only and do not demonstrate a consumer capable of producing reset links.
**Required:** distinguish provider accepted from verified provider webhook delivery; use `UNKNOWN` where no delivery receipt exists; do not use application Outbox for Keycloak action-token transport.

**IAM-AUD-09: OTP ownership and API contract are under-specified.**
Existing OTP verify endpoint accepts only challenge ID and code; service has no purpose-scoped proof or verified delivery and no concurrency lock. Document says 'disabled by default' without defining routing behavior or use cases.
**Required:** specify 404/410 behavior while disabled; approve exact business purpose before enabling; challenge-to-action proof, atomic consume and cross-tenant tests.

**IAM-AUD-10: Mobile scope is ungrounded.**
Compatibility document asserts React/Flutter acceptance without inventorying actual Collectra mobile clients. Do not require a non-existent client to gate backend release.
**Required:** inventory deployed clients and version constraints; mark Flutter conditional unless actual Collectra client exists.

**IAM-AUD-11: Keycloak deployment and privileges are not specified to executable level.**
No pinned version, immutable realm config, client IDs, audiences, redirect URI patterns, SMTP/action-token configuration, DB schema privileges or HA/session topology.
**Required:** versioned deployment manifest, realm-as-code, explicit clients, role grants, upgrade/rollback rehearsal and environment-specific configuration matrix.

**IAM-AUD-12: Threat model and migration security coverage incomplete.**
No explicit CSRF state/nonce matrix, account recovery abuse scenarios, stolen invite, reset-vs-invite races, tenant slug squatting, social identity linking or two-node throttling test fixtures.
**Required:** threat model (STRIDE or equivalent), abuse-case tests, security event schema and rate-limit failure policy.

## P2 — quality / maintainability
- Documentation currently has multiple normative sources; add one canonical decision registry and supersession rules.
- Clarify HTTP contracts, deprecation timeline, errors, and UX flows for onboarding and recovery.
- Add metrics with numeric SLOs and alert thresholds, instead of generic 'measured'.
- Require tests for admin TOTP removal while logged in, pending email change and repeated Keycloak event replay.
- Count the test cases by stable IDs; earlier '30 tests' count is not a substitute for executable assertions.

## Required decision order
1. Resolve IAM-AUD-01..05 and publish one canonical identity, onboarding, principal and admin-MFA contract.
2. Resolve IAM-AUD-06..12 and establish exact migration/SMTP/realm configuration and test fixtures.
3. Write RED PostgreSQL + HTTP tests for current defects; independent S2 review.
4. Implement additive schema and adapters; Keycloak integration; browser E2E.
5. Release only with executable evidence and zero open P0/P1 findings.

Verdict: architectural direction is reasonable, but **NOT APPROVED FOR IMPLEMENTATION AS FINAL SPECIFICATION** until P0 contradictions are resolved. No code changes or tests executed in this review.
