# IAM-R3 — User Lifecycle: implementation and verification specification

**Status:** APPROVAL CANDIDATE / IMPLEMENTATION NOT STARTED BY THIS DOCUMENT  
**Branch:** `feat/iam-shared-platform` (no new branches; do not merge main)  
**Product:** App Common / Shared IAM; Collectra is first real consumer; ZAKAZ is a future consumer.  
**Baseline:** `docs/qa/iam-user-lifecycle-acceptance.md`, ADR-IAM-007/008, IAM-R2 consumer snapshot and revocation logic.

## 0. Goal, boundaries, definition of done

Deliver an actual human user journey: register → receive email → verify → log in via OIDC PKCE → access permitted Collectra action → deny forbidden tenant action → refresh/logout/relogin → recover password → enroll/challenge TOTP → block/revoke → unblock → delete global identity and handle app-local personal data safely.

**Keycloak** owns credentials, verified email, global subject `(iss,sub)`, MFA, identity sessions and identity deletion. **App Common** owns app registration/provisioning, security state, authorized lifecycle orchestration, revocation/ACK, audit and recovery. **Collectra** owns app-local user UUID, memberships, business RBAC, financial records and retention. No cross-app shared business user ID or business DB. One shared **end-user** Keycloak realm, distinct clients and API audiences per application; an optional isolated privileged admin realm is permitted.

No reimplementation of passwords, reset tokens, OTP algorithms or Keycloak OIDC endpoints in App Common. Prefer Keycloak hosted registration, verification, recovery and MFA pages; App Common exposes only needed privileged control-plane operations. Explicitly separate app-local account deactivation from global identity disable/delete.

**Release rule:** Each scenario needs a test name, command, SHA, Keycloak/PostgreSQL/SMTP versions, CI run link, evidence and reviewer. `IMPLEMENTED` is not `VERIFIED`; `VERIFIED` is not `PRODUCTION_READY`. No percentage readiness without measured criteria.

## 1. IAM-R3.0 — baseline and contract freeze (prerequisite)

1. Inventory existing Collectra registration/login/reset/OTP endpoints, React routes, user_accounts mapping, tenant membership and authorization filters. Record exact migration flags, existing users, rollback and source of credentials. No simultaneous independent credential authorities for a migrated user.
2. Inspect real App Common runtime, Keycloak realm import, CI workflows and IAM-R2 dynamic consumer snapshot. Verify IAM-R2 CI and PostgreSQL GREEN first; do not assume.
3. Decide onboarding policy: public self-registration vs invitation. For the first release, **invitation or controlled registration is preferred** until rate limits, email delivery and abuse prevention pass. Existing realm has `registrationAllowed=false`; any change must be explicit, documented and tested.
4. Specify OIDC clients for Collectra React (Authorization Code + PKCE S256, exact redirect allowlist), Collectra API audience, App Common control plane and machine-to-machine clients. Existing `collectra-test` and `app-b-test` machine clients are not browser clients. For Flutter/ZAKAZ, define separate future public native clients; no implementation now.
5. Define global deletion policy: legal retention of financial data, PII inventory, erasure/anonymization and business-profile ownership; obtain product/legal approval before destructive automation. Test deletion only on disposable identities.

**Exit:** signed identity/authorization/deletion contract, verified IAM-R2 baseline, reproducible local environment, negative tests first.

## 2. IAM-R3.1 — Registration & Verification

**Implementation**
- Configure one shared user realm, unique verified email policy, Verify Email required action, SMTP sender and Keycloak verification template; do not confuse Keycloak transactional SMTP with Collectra KumoMTA business mail.
- Choose controlled invite/registration endpoint or Keycloak hosted self-registration; no insecure custom password collection.
- Apply consistent email normalization and unique identity policy; prevent automatic linking by email alone; for existing Collectra users, link only through explicit verified migration contract preserving local UUID/FKs.
- Block application access while `email_verified=false` through an enforced server-side policy, not merely UI checks. Treat social-provider email claims according to explicit trust policy.
- Implement generic anti-enumeration response, throttling and audit without logging action tokens or email content.

**RED → GREEN tests**
- REG-01 successful registration creates exactly one Keycloak identity.
- REG-02 verification message delivered via **real Keycloak SMTP → Mailpit**; valid one-time link sets verified flag.
- REG-03 unverified account denied protected Collectra endpoint; verified account succeeds with local membership.
- REG-04 missing/invalid email, duplicate email, concurrent duplicate registration rejected/idempotent; no duplicate local profile.
- REG-05 expired, reused, altered verification links rejected; resend cooldown/rate limits enforced.
- REG-06 existing-user linking cannot hijack an account by matching email.
- REG-07 Keycloak/SMTP outage, retry and restart: no unintended activation or duplicate accounts.

**Gate:** real browser + Keycloak + Mailpit + PostgreSQL evidence, no P0/P1.

## 3. IAM-R3.2 — Authentication, Authorization & Sessions

**Implementation**
- React: standards-based Authorization Code + PKCE S256 and state/nonce; strict redirect URI/origin; choose a secure BFF with server-side session/HttpOnly cookies where feasible; never store long-lived tokens in localStorage.
- APIs: verify JWKS signature, issuer, `aud`, expiration, not-before, allowed client/scopes and revocation/local user state; fail closed on missing identity or business membership.
- Idempotent `(issuer,subject)` to Collectra local user mapping; preserve existing local IDs and business history.
- Separate Collectra business roles from App Common platform scopes; global SSO does not imply Collectra membership.
- Refresh/logout: define session termination, refresh rotation/reuse behavior, residual JWT access window and global vs app-local logout. Record Keycloak version-dependent behavior.

**Tests**
- AUTH-01 real browser login, callback/PKCE/state validation, no code/token leakage.
- AUTH-02 valid JWT accepted; wrong issuer/audience/signature/expired token rejected.
- AUTH-03 cross-tenant and missing-role requests return 403; no cross-app privilege inheritance.
- AUTH-04 duplicate first-login and parallel callback create one local mapping.
- SESSION-01 refresh succeeds per policy; expired/reused refresh rejected.
- SESSION-02 local logout and global logout; SSO across two distinct clients in same realm when second client is available; do not claim ZAKAZ integrated.
- SESSION-03 account disabled while token still unexpired: actual API denial measured, not inferred from Keycloak logout.
- SEC-01 redirect URI tampering, CSRF, state/nonce/PKCE downgrade and browser back-button tests.

**Gate:** real HTTP + Playwright + API security matrix PASS.

## 4. IAM-R3.3 — Recovery, Email Change & MFA

**Implementation**
- Keycloak hosted Forgot Password/UPDATE_PASSWORD with one-time expiring action links, neutral response for unknown account, no reset token in App Common database/logs.
- Password policy, retry/rate limits, session invalidation and audit. Define exact access JWT residual lifetime and how revocation is enforced.
- Email change as verified two-phase transition; unverified new email cannot receive ordinary business notifications or become account recovery authority.
- Keycloak TOTP enrollment, challenge, reset/recovery policy; mandatory step-up for privileged operators. No development OTP exposure in non-local environments.
- Abuse controls by IP, account and device/risk where appropriate; monitor brute-force events.

**Tests**
- REC-01 known/unknown user responses indistinguishable, rate limiting and enumeration checks.
- REC-02 actual Keycloak reset mail to Mailpit; valid link succeeds, expired/replayed/cross-account link fails.
- REC-03 old password rejected, new accepted; old refresh/session invalidated as policy requires.
- EMAIL-01 new address unverified until action complete; old address handling, concurrency and rollback.
- MFA-01 TOTP enrollment and login; wrong code, replay, expired code, lost factor and privileged recovery.
- SEC-02 no passwords/OTP/action links in logs, traces, events or GitHub artifacts.

**Gate:** actual Keycloak SMTP and browser recovery/MFA PASS.

## 5. IAM-R3.4 — Account Management, Revocation & Deletion

**Implementation**
- Separate `APP_DEACTIVATED`, `GLOBAL_BLOCKED`, `GLOBAL_ACTIVE` and `DELETION_REQUESTED/IN_PROGRESS/COMPLETED/FAILED_RETRYABLE` semantics; avoid conflating business access with global identity state.
- Global block: authenticated privileged request, audit, transactional state/outbox, Keycloak disable/logout, versioned consumer snapshot, authenticated consumer ACK. `ENFORCED` only when actual obligations fulfilled; no simulated second consumer.
- Unblock: explicit privileged policy, monotonic version, authorized Keycloak enable, app-local state reconciliation, stale BLOCK events cannot override newer ACTIVE state; tests for concurrent block/unblock.
- Deletion: strongly authenticated or independently approved operator action, re-auth/step-up and confirmation, account suspension first, durable deletion saga with retries, Keycloak delete, revocation, local unlink/anonymization subject to approved retention; no physical deletion of financial ledger or audit records. Avoid unbounded identity PII in tombstones; prevent orphaned foreign keys and unsafe re-registration/relinking.
- Data subject request policy: retention windows, erasure exceptions, audit, backups and support recovery. No automatic destructive operation against live customer data.

**Tests**
- BLOCK-01 global block prevents new login and old JWT access according to explicit revocation policy.
- BLOCK-02 two real consumer ACKs, missing ACK stays PROPAGATING; duplicate/stale ACK, binding epoch, restart and rebind.
- UNBLOCK-01 authorized unblock restores eligible access; stale events cannot re-block.
- UNBLOCK-02 forbidden actor cannot unblock; app-local block remains app-local.
- DELETE-01 disposable user deletion removes Keycloak identity and denies new login and old tokens.
- DELETE-02 app-local PII cleanup with referential integrity and immutable financial history.
- DELETE-03 duplicate delete, crash after Keycloak side effect, Keycloak outage, DB outage and retry/recovery.
- DELETE-04 pending legal hold/retention policy prevents impermissible erasure; security audit is preserved without raw secrets.
- RACE-01 concurrent block/unblock/delete and event reorder preserve monotonic security state.

**Gate:** PostgreSQL transaction/concurrency tests, real Keycloak Admin API tests, recovery and audit PASS; independent security/privacy review.

## 6. IAM-R3.5 — Full E2E & release certification

**Environment:** disposable Docker Compose/Testcontainers stack with pinned Keycloak, PostgreSQL, Mailpit, App Common, Collectra API and React; RabbitMQ/KumoMTA where a Collectra business notification is part of the scenario. Do not use mock Keycloak, mock SMTP or mocked HTTP ACK as acceptance evidence. Separate unit tests may use mocks.

**Golden Journey**
1. Register a synthetic new identity; observe Keycloak record and Mailpit verification message.
2. Verify email; prove unverified access was denied and verified access is allowed only after proper app-local membership.
3. Login via React PKCE; access permitted Collectra API and reject cross-tenant and wrong-audience calls.
4. Refresh/logout/relogin; verify session/token behavior.
5. Request password reset, use SMTP link, login with new password; enroll TOTP and verify challenge.
6. Block globally, verify Keycloak disabled, old JWT rejected and all registered required consumers ACK (or operation remains PROPAGATING).
7. Unblock and login; delete disposable identity; verify Keycloak removal, token denial, app-local PII policy and financial-record preservation.
8. Repeat failures: SMTP unavailable, Keycloak unavailable, PostgreSQL restart, duplicate events, consumer outage and replay.
9. For future ZAKAZ: separate acceptance extension with same `(iss,sub)`, independent app-local profile and distinct `aud`; not part of the current PASS claim.

**CI**
- Existing main workflow must run `app-common` Maven and PostgreSQL tests as required job, Collectra backend and frontend tests. Confirm workflow glob matches actual `*IT` and `*Test` classes and does not silently skip tests; prefer explicit Surefire/Failsafe configuration or verifiable discovery.
- Add lifecycle Keycloak/SMTP integration job and Playwright E2E with exact versions, artifact upload and run summary.
- Mandatory negative tests and release smoke must run on the exact candidate SHA. Do not weaken security assertions to obtain GREEN.
- CI failure, missing evidence, skipped required tests or mock-only proof blocks release.

## 7. Delivery discipline and acceptance report

Implement each stage **RED tests → minimum GREEN implementation → regression → independent S2 review**. Keep changes in `feat/iam-shared-platform`, avoid unrelated refactors and new branches, do not merge to main. Each stage updates a separate readiness scorecard for **App Common** and **Collectra**; no combined score.

For every scenario record: ID, code path, expected behavior, test class, test layer, result, exact commit, CI URL, environment, evidence and defect reference. Classify `NOT_IMPLEMENTED`, `IMPLEMENTED_UNVERIFIED`, `FAILED`, `BLOCKED`, `VERIFIED`. If a stage cannot pass, report the blocker, never fabricate test evidence.

**Final acceptance:** R3.1–R3.5 all VERIFIED; zero unresolved P0/P1; no unverified destructive deletion; independent S2 APPROVE; documented production deployment/rollback and incident procedures. Until then App Common user lifecycle remains **NOT FULLY VERIFIED**.


## 8. Mandatory two-asset ownership and separate release gates (normative)

**This section is normative for all IAM-R3 stages.** There are **two independent product assets**, not one product with an IAM module:

1. **App Common / Shared IAM Platform (AC)** — reusable identity/security infrastructure. It must run and be qualified without Collectra's business database, receivables modules or tenant-specific business roles. Keycloak remains its external identity authority.
2. **Collectra Business Platform (COL)** — commercial receivables product and first IAM consumer. It owns business profiles, tenant membership, financial workflows and historical records, and must not become a credential issuer or App Common's platform administration layer.

**Integration (INT)** is a third *acceptance track*, **not** a third product. ZAKAZ is a later independent consumer; its readiness is never included in Collectra or App Common scores.

### 8.1 Per-stage ownership matrix

| Stage | AC — deliver and test independently | COL — deliver and test independently | INT — real cross-boundary proof |
| --- | --- | --- | --- |
| R3.1 | Keycloak user provisioning, verification, SMTP, security policy | First-login local profile and tenant onboarding, verified-email access policy | New identity -> verified email -> Collectra local profile, no email-based account takeover |
| R3.2 | Shared realm, OIDC clients, issuer/audience policies, global sessions | React PKCE/BFF, resource-server JWT validation, local RBAC | Browser login -> scoped Collectra API, forbidden audience/tenant denied |
| R3.3 | Keycloak reset/TOTP/email change, security audit, abuse limits | UX entry points and local profile notification address synchronization | Password reset and MFA via Keycloak, Collectra session behavior verified |
| R3.4 | Global block/unblock/delete orchestration, revocation snapshots/ACK, Keycloak admin API | Local account disable, unlink/anonymization and financial-record retention | Block/unblock/delete propagation and retry across service boundaries |
| R3.5 | AC standalone deployment, PostgreSQL migrations, Keycloak/SMTP E2E, rollback | COL business E2E, migrations, React/browser and business data regression | Exact-SHA full journey, outage/recovery, independent consumer simulation prohibited for final two-consumer gate |

### 8.2 Independent scorecards

**AC-RC:** `REGISTRATION`, `VERIFICATION`, `AUTHENTICATION`, `SSO_SESSIONS`, `RECOVERY`, `MFA`, `ACCOUNT_SECURITY`, `DELETION`, `APP_REGISTRY`, `REVOCATION_ACK`, `OPERATIONS`. Each gate has `status | SHA | test ID | CI URL | environment | reviewer`.

**COL-RC:** `IAM_CLIENT_INTEGRATION`, `LOCAL_USER_MAPPING`, `TENANT_RBAC`, `REACT_LOGIN_UX`, `BUSINESS_E2E`, `FINANCIAL_DATA_INTEGRITY`, `MAIL_DELIVERY`, `OPERATIONS`. Same evidence requirements.

**INT-RC:** `OIDC_CONTRACT`, `AUDIENCE_ISOLATION`, `GLOBAL_BLOCK_PROPAGATION`, `LOCAL_BLOCK_ISOLATION`, `ACCOUNT_DELETION_BOUNDARY`, `FAILURE_RECOVERY`. These are integration gates only.

A passing COL test cannot set AC to VERIFIED, and a passing AC test cannot set COL to VERIFIED. A failed INT gate blocks the integrated release without erasing independently verified capabilities.

### 8.3 Architecture invariants enforced by tests

- AC boots with its own PostgreSQL schema/database and Keycloak without Collectra's business DB; migrations cannot reference Collectra business tables.
- COL does not store passwords, generate Keycloak reset credentials or assign global platform privileges.
- `(iss,sub)` identifies the global principal; each application owns its local identity and authorization.
- AC's app registration and security-event contracts have no hardcoded Collectra business identifiers and support an arbitrary registered consumer set.
- COL tenant restrictions are not inherited by other consumers, and global operator privileges never imply Collectra business access.
- Deleting a global identity cannot cascade-delete invoices, payments, immutable audit or legally retained financial records.

### 8.4 Release and extraction discipline

Maintain separate build/test artifacts, versions, changelogs, deployment configuration, backups, monitoring and rollback plans for AC and COL. They may temporarily share the repository and working branch, but this is **not** evidence of independent production deployment. Track `AC-RC`, `COL-RC`, and `INT-RC` separately. No merge to `main` without explicit approval. Future ZAKAZ integration must use the same public contracts, with its own release gate.


## 9. R3.0 Compatibility & Gap Closure — mandatory implementation plan

**Priority: P0. Must complete before R3.1 feature rollout.** The following findings are grounded in current source inventory, not new runtime execution.

### 9.1 Existing implementation and target conflicts

| Source | Observed behavior | Required transition | Proof |
| --- | --- | --- | --- |
| `src/main/java/io/collectra/api/identity/api/AuthController.java` | Legacy tenant register, login/by-slug, refresh, logout, logout-all | Preserve business tenant onboarding but move credentials to Keycloak; version or gate legacy endpoints | Existing users and tenant creation still work; Keycloak-only rejects legacy credentials |
| `src/main/java/io/collectra/api/shared/security/SecurityConfig.java` | Legacy HS256 decoder by default; Keycloak enabled via flag; CSRF disabled in stateless API | Explicit `LEGACY/MIGRATION/KEYCLOAK_ONLY` modes; fail startup on invalid production configuration; BFF CSRF protection | Startup matrix, downgrade refusal, issuer/audience negatives, CSRF E2E |
| `frontendweb/src/pages/auth/LoginPage.tsx` | React sends email/password to Collectra API for tenant/platform login | Replace migrated-user path with OIDC authorization redirect and callback; preserve tenant selector as business context | Playwright old/new account journeys |
| `frontendweb/src/features/auth/model/auth-context.tsx` and `frontendweb/src/shared/auth/token-storage.ts` | Browser refresh token stored in sessionStorage, access token in memory | BFF opaque session cookie, server-side refresh tokens, login/logout/session bootstrap and HTTP client migration | Browser has no refresh token; cookie flags, CSRF and logout tests |
| `src/main/java/io/collectra/api/shared/security/KeycloakDomainAuthorizationFilter.java` | Keycloak JWT mapped to verified local user, tenant membership, roles from Collectra DB | Preserve business authorization; explicit verified link and no implicit membership | Missing link, wrong tenant header, inactive membership, role-change tests |
| `src/main/java/io/collectra/api/shared/security/GlobalRevocationGate.java` | Fail-closed local snapshot, freshness window | Preserve monotonic event application and bounded staleness; prove outage behavior | Old JWT rejected, stale cache and DB outage tests |
| `app-common/src/main/java/io/collectra/platform/registry/PlatformRegistry.java` | Only creates REGISTERED record | Separate R4 provisioning reconciliation and ACTIVE gate; do not claim app onboarding ready | Actual Keycloak client provisioning tests |
| `app-common/infra/realm-shared.json` | Public registration disabled; machine test clients without standard browser flow | Explicit invitation-first policy and real browser client config | Real Keycloak hosted verification and PKCE |
| `app-common/src/main/java/io/collectra/platform/revocation/KeycloakAdminClient.java` | Disable/logout only | Add authorized unblock and deletion orchestration as distinct operations | Real Keycloak Admin API, audit, retry and deletion tests |
| `docs/qa/iam-macos-smoke-report.md` | Historical SMOKE-01..06/08 PASS, SMOKE-07 BLOCKED | Re-run on exact candidate SHA, second real consumer required | CI and macOS reports with environment IDs |

### 9.2 Work packages, acceptance and order

**R3.0A — Inventory & migration contract (P0):** enumerate all auth, platform-auth, invitation, OTP, password recovery, registration, user deletion and tenant onboarding endpoints and their React consumers. Record DB tables/FKs, current JWT issuers and token storage, migration flags, users and credentials. Create endpoint compatibility matrix `existing API → owner → target → flag → rollback → test`. Acceptance: no unclassified auth endpoint and explicit mapping preserving existing user UUID and tenant memberships.

**R3.0B — Credential authority cutover (P0):** implement explicit runtime modes `LEGACY`, `MIGRATION`, `KEYCLOAK_ONLY`; deny accidental fallback/downgrade in non-local environments; link identities only after proof of both account ownerships; no email-only linking. Legacy refresh and password reset disabled for migrated identities. Verify concurrent migration, collisions, rollback before cutover, and incident procedure after legacy token revocation. Acceptance: no account has two simultaneously active credential authorities after cutover.

**R3.2B — React BFF migration (P0):** deliver confidential client + PKCE, server-side encrypted token/session store, secure HttpOnly SameSite cookies, CSRF, proxy/TLS trust and session rotation; refactor React login, auth-context, HTTP client, guards and logout. Acceptance: no browser refresh token, no regression in tenant/platform business authorization.

**R3.4B — Deletion and retention (P0):** define deletion state machine and legal hold policy; split Keycloak identity deletion, Collectra profile deactivation/anonymization and retained financial records; ensure retry-safe saga and secure audit. Acceptance: no orphaned FKs, no unapproved financial deletion, old sessions denied.

**R3.5B — Two-asset qualification (P0):** execute independent `AC-RC`, `COL-RC`, `INT-RC` suites and report each status separately, including independent deployability of App Common. Acceptance: exact-SHA evidence, no mock-only integration proof, S2 approval.

**R4 — App provisioning and shared notification service (P1, separate release):** implement ADR-IAM-009 onboarding and notification contracts in independently deployable components. Do not silently count them as IAM-R3 delivered. Keycloak security emails must work in R3 without depending on unimplemented shared notifications.

### 9.3 Explicit migration and regression test matrix

- `MIG-01` legacy user + verified Keycloak principal preserves Collectra UUID, tenant ID, membership, roles and historical FKs.
- `MIG-02` same email with different principal never auto-links; collision fails closed.
- `MIG-03` simultaneous linking attempts create one active link; retry is idempotent.
- `MIG-04` migrated user cannot authenticate via legacy password/refresh/reset.
- `MIG-05` `KEYCLOAK_ONLY` fails startup when issuer/audience/clients missing; cannot silently revert to legacy.
- `MIG-06` pre-cutover rollback preserves business data; post-cutover recovery cannot reactivate revoked legacy tokens.
- `BFF-01` PKCE/state/nonce and exact redirect allowlist; reject tampered callback.
- `BFF-02` HttpOnly/Secure/SameSite cookie and CSRF; no refresh token in browser storage or JavaScript-visible API.
- `BFF-03` local logout versus global logout, session rotation and token refresh after browser restart.
- `COL-01` cross-tenant header, inactive membership, changed roles, unlinked identity, disabled account and blocked subject all denied.
- `COL-02` tenant registration/invitation and existing Collectra business Golden Journey unaffected by IAM cutover.
- `AC-01` App Common starts and runs migrations without Collectra business database.
- `INT-01` cross-app issuer/audience isolation and real revocation ACK, including consumer outage/recovery.

**Evidence policy:** RED → minimal GREEN → full regression → S2. All test claims must state exact SHA and execution environment. This document does not certify implementation.
