# IAM-R3 — End-to-end user lifecycle acceptance (registration through deletion)

Status: GAP AUDIT / ACCEPTANCE SPEC — NOT VERIFIED
Baseline: feat/iam-shared-platform @ 2ecdaa484ed4a37e2ba5f1c75bfcb8ba956b04bf
Date: 2026-11-15

## Goal
Prove real user registration, email verification, login, authorization, refresh, logout, password recovery, MFA, account block/unblock, and deletion for the shared Keycloak realm and Collectra as first consumer. Future ZAKAZ is not yet integrated. Do not label a capability READY merely because Keycloak supports it in principle.

## Current evidence and gaps
- `app-common/infra/realm-shared.json`: `registrationAllowed=false`, `verifyEmail=true`, `resetPasswordAllowed=true`, `bruteForceProtected=true`. This is **not** an end-user self-registration configuration. Existing `collectra-test` and `app-b-test` clients are machine-only (`standardFlowEnabled=false`); they do not prove browser Authorization Code + PKCE.
- App Common Java sources provide registry, JWT resource-server security, enrollment, revocation, dispatcher, reconciliation, Keycloak disable/logout and HTTP ACK. No complete public user registration, email change, account deletion, unblock or credential recovery API is evidenced in this source inventory. Keycloak-native flows may supply some capabilities but require configuration and browser proof.
- `docs/qa/iam-macos-smoke-report.md`: SMOKE-01–06, 08 PASS, SMOKE-07 BLOCKED; these do not constitute the full lifecycle.
- `docs/qa/mail-macos-smoke-report.md`: KumoMTA -> local Mailpit PASS for business email. Keycloak verification/reset email delivery is a **separate** SMTP configuration and acceptance gate.
- Historical `docs/specs/iam-r1-1-email-recovery-mfa.md` uses superseded per-app realms; current ADR-IAM-007/008 require **one shared end-user realm** with per-app clients/audiences.

## Ownership
- Keycloak: global identity, credentials, email verification, SSO sessions, TOTP and identity deletion.
- App Common: controlled Keycloak configuration/provisioning, application registry, security state/propagation, operator audit, deletion orchestration and integration contracts as implemented.
- Collectra: app-local user UUID, tenant memberships, business roles, historical financial records and lawful retention.
- No cascade deletion of invoices, payment ledgers or audit history. Deletion policy must define anonymization, retention and erasure of personal data; global identity deletion and app-local profile deletion are distinct.

## Mandatory real-user acceptance journeys
| ID | Journey | Pass criteria |
| --- | --- | --- |
| REG-01 | New account self-registration | Browser flow creates one Keycloak user; duplicate/concurrent signup cannot create duplicate active identity |
| REG-02 | Email verification | Real verification email in local SMTP mailbox, one-use/expiry, `email_verified=true`; unverified identity cannot perform protected app action |
| REG-03 | Invalid/duplicate email and abuse | No enumeration, rate limits, no leaked action tokens |
| AUTH-01 | Browser login with PKCE | Authorization Code + PKCE, correct issuer, signature, audience, scopes, app-local user mapping |
| AUTH-02 | Local business authorization | Collectra tenant membership and RBAC enforced, foreign tenant denied |
| AUTH-03 | Token misuse | Wrong audience/client, expired, forged and stale tokens rejected |
| SESSION-01 | Refresh and app-local logout | Refresh rotation/expiry and app logout policy observed; old refresh token unusable as required |
| SESSION-02 | Global logout | IdP session terminated, fresh authorization requires login; residual JWT window measured |
| REC-01 | Forgot password | Same public response for existing/missing user, actual Keycloak SMTP mail, expired/replayed link denied |
| REC-02 | Password change/reset | Old credential rejected, new credential accepted, session/refresh revocation verified |
| MFA-01 | TOTP enrollment and login | Valid OTP accepted, invalid/replay rejected; recovery and operator step-up covered |
| USER-01 | Global account block | Keycloak disable/logout and Collectra old-token denial; versioned durable consumer ACK; no false ENFORCED |
| USER-02 | App-local block | Collectra denied without globally disabling Keycloak user |
| USER-03 | Global unblock | Explicit authorized, audited unblock with monotonic state and consumer recovery; no stale event re-block |
| USER-04 | Email change | New address verified before use, old address handling documented, no takeover |
| DELETE-01 | Delete global identity | Strongly authenticated/approved deletion, logout and token invalidation, Keycloak user gone, replay-safe operation |
| DELETE-02 | App-local cleanup | Profile unlink/anonymization under retention policy, financial history preserved, no orphan foreign keys |
| DELETE-03 | Deletion recovery | Crash, duplicate command and partial Keycloak/DB failure are retry-safe and auditable |
| SSO-01 | Second real consumer | Same `(iss,sub)`, separate local profile; cross-app audience denied; two real ACKs |
| OPS-01 | Dependency outages | Keycloak/DB/SMTP down, restart and retry, fail-closed security decisions |

## Test layers
1. Unit: validators, state transitions, mapping, error handling, no credential leakage.
2. PostgreSQL Testcontainers: idempotent local profile, lifecycle transitions, outbox, immutable consumer snapshot, concurrency and deletion saga.
3. Real Keycloak + local SMTP (Mailpit): registration/verify/reset/MFA/disable/logout/delete; capture anonymized HTTP, DB and SMTP evidence.
4. Playwright browser: registration -> verify -> login -> Collectra authorized action -> logout -> recovery -> block -> delete.
5. Independent second API consumer: audience isolation, security event/ACK, outage recovery; no simulated ACK.
6. CI: app-common `mvn clean verify`, Collectra Maven and frontend tests on **same SHA**; browser E2E as explicit required release job.

## Safe execution
Run destructive DELETE tests only against disposable Keycloak realm, PostgreSQL and SMTP containers; never use production users or persistent real accounts. Do not enable public registration until abuse controls, email delivery and verified-user authorization are proven. Use synthetic emails and short-lived test identities. Record exact SHA, versions, run IDs, test reports, and evidence; no screenshots/tokens containing secrets.

## Exit criteria
All mandatory journeys PASS on exact candidate SHA; no P0/P1 defects; independent security S2 APPROVE; tested backup/restore, migration and rollback. Until then: **LIFECYCLE_NOT_VERIFIED**. ZAKAZ onboarding remains a separate subsequent gate.
