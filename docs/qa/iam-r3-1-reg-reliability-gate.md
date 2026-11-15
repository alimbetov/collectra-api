# IAM-R3.1 — REG-01…REG-07 reliability gate

Branch: feat/iam-shared-platform
Status: **IMPLEMENTED — CONDITIONAL; NOT VERIFIED**.

## Implemented
- Privileged onboarding requires PLATFORM_SUPER_ADMIN, existing local UUID, and legacy authority.
- PostgreSQL reserves unique local user and preallocated Keycloak subject before remote create.
- Retry reuses the same reservation; Keycloak HTTP 409 is accepted only when the exact reserved subject and email match.
- Keycloak owns verification mail and UPDATE_PASSWORD action; Collectra never accepts action tokens or passwords.
- Activation checks Keycloak enabled/emailVerified and matching email; writes verified identity link and cutover in one local transaction.
- JWT admission requires both verified link and committed KEYCLOAK credential authority.

## Required acceptance evidence (NOT YET EXECUTED)

| Case | Required real-system proof | Current evidence |
|---|---|---|
| REG-01 | One Keycloak identity and one local reservation after registration/retry | Code only |
| REG-02 | Keycloak SMTP -> Mailpit, click one-time link, emailVerified=true | Not executed |
| REG-03 | Unverified JWT denied; verified and migrated JWT accepted | Unit test added, not executed |
| REG-04 | Duplicate/concurrent onboarding, invalid email, Keycloak duplicate | Unique constraint and retry code; integration test missing |
| REG-05 | Expired/replayed/tampered verification link, resend throttling | Keycloak-managed; not executed |
| REG-06 | No email-only auto-link or cross-account subject substitution | Subject-bound code; integration test missing |
| REG-07 | SMTP/Keycloak/PostgreSQL outage, retry, restart, orphan reconciliation | Partial retry design; not executed |

## Known release blockers
1. Keycloak support for preassigned user IDs must be verified against the pinned Keycloak version. If unsupported, implement an outbox/saga reconciliation flow before production.
2. Repeated onboarding calls currently resend email without cooldown; add rate limiting and durable send state.
3. Crash after Keycloak creation and before its HTTP response requires actual restart/retry proof.
4. No live SMTP credentials, Mailpit evidence, PostgreSQL/Testcontainers GREEN or independent S2 review is recorded here.
5. Public new-user self-registration is not implemented; current API is privileged migration of existing users.
6. Do not use this endpoint for an arbitrary email claim; the operator must separately verify ownership/authority for the target local UUID.

## Gate
Do not label REG-01…REG-07 PASS or production-ready without executable results. Do not merge to main without explicit authorization.
