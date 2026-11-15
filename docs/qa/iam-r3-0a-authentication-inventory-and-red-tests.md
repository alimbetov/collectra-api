# IAM-R3.0A — Collectra authentication inventory, migration map and RED test baseline

**Status:** SOURCE AUDIT COMPLETED FOR LISTED FILES / RED TESTS COMMITTED, NOT EXECUTED  
**Branch:** `feat/iam-shared-platform`  
**Assets:** AC = App Common / Shared IAM; COL = Collectra Business Platform; INT = cross-asset acceptance track.  
**Source:** actual files in `alimbetov/collectra-api`; see path references below. Do not infer production configuration from code defaults.

## 1. Authority boundaries and cutover invariants

- Keycloak is the eventual sole authority for global credentials, verification, password recovery, MFA and SSO. AC controls global access state, app registry and revocation orchestration. COL controls tenant signup, invitations, local user UUID, membership, roles and business authorization.
- Shared `(iss,sub)` is **not** Collectra local user ID; never join by email alone.
- Legacy Collectra credentials remain only for explicitly scoped pre-cutover users. Never allow a migrated principal to log in, refresh or reset a legacy credential. Production KEYCLOAK_ONLY must fail startup rather than silently default to legacy JWT.
- Keep platform operator privileges distinct from tenant roles. Do not derive Collectra permissions from Keycloak claims.
- Account deletion must not cascade into invoices, payments or immutable audit records.

## 2. Endpoint and component inventory (observed in source)

| Asset | Source / endpoint or component | Current behavior | Target ownership and migration action | Test gate |
| --- | --- | --- | --- | --- |
| COL | `identity/api/AuthController.java` POST `/api/v1/auth/tenants/register` | Creates tenant and initial user with email/password via `AuthService` | Preserve tenant creation; separate Keycloak identity provisioning and verified linking; avoid duplicate tenant on retry | MIG-01, COL-02 |
| COL | same POST `/api/v1/auth/login` | Tenant UUID + email/password | Legacy-only during migration; migrated users use OIDC | MIG-04 |
| COL | same POST `/api/v1/auth/login/by-slug` | Tenant slug + email/password | Tenant slug becomes business workspace selector, not password authority | MIG-04, BFF-03 |
| COL | same POST `/api/v1/auth/refresh` | Local refresh token | Reject migrated users; BFF server refresh with Keycloak | MIG-04, BFF-02 |
| COL | same POST `/api/v1/auth/logout`, `/logout-all` | Legacy session revocation | Define local/global logout, revoke appropriate session authority | BFF-03 |
| COL | `identity/api/PlatformAuthController.java` POST `/api/v1/platform/auth/login`, `/refresh`, `/logout`, `/logout-all` | Separate platform admin credentials and sessions | Privileged operator migration and MFA/step-up; preserve platform-vs-tenant authorization | MIG-04, COL-01 |
| COL | `identity/api/AccountLifecycleController.java` POST `/api/v1/auth/invitations/accept` | Invitation acceptance takes a password and issues local tokens | Keep invitation/role assignment in COL; Keycloak credential/verification authority | MIG-01, REG-06 |
| COL | same POST `/api/v1/auth/password/forgot`, `/reset` | Local password recovery | Keycloak hosted recovery for migrated identities; no dual reset authority | MIG-04, REC-02 |
| COL | `identity/api/TenantInvitationController.java` POST/GET/DELETE `/api/v1/identity/invitations` | Tenant-scoped invite/list/revoke with `USER_INVITE` | Remain COL business operations, not global Keycloak user management | COL-02 |
| COL | `identity/api/OtpController.java` POST `/api/v1/auth/otp/challenges`, POST `/challenges/{id}/verify` | Tenant/subject/purpose business OTP challenge | Audit business OTP separately; never mistake it for Keycloak login TOTP MFA | MFA-01 |
| COL | `identity/api/CurrentUserController.java` | Current app-local identity and membership | Keep local profile; verify `(iss,sub)` link and active membership | COL-01 |
| COL | `platform/api/PlatformUserController.java` and `identity/api/PlatformAdministratorController.java` | Platform user administration | Audit existing create/disable/delete semantics before connecting global block/delete | DELETE-02 |
| COL | `shared/security/SecurityConfig.java` | Legacy HS256 JWT decoder when Keycloak flag false/missing; stateless CSRF disabled | Explicit credential authority modes and fail-closed production config; BFF CSRF handled separately | MIG-05, BFF-02 |
| COL | `shared/security/KeycloakJwtDecoderConfiguration.java` | Keycloak JWT decoder configuration | Strict issuer/audience, algorithms and token claims | AUTH-02 |
| COL | `shared/security/KeycloakDomainAuthorizationFilter.java` | Links Keycloak `(iss,sub)` to local user; derives tenant and roles from COL DB | Preserve domain ownership; fail closed on missing link, wrong tenant and inactive membership | COL-01 |
| COL | `shared/security/VerifiedIdentityLinkRepository.java` | Verified identity-link repository | Enforce unique/verified linking and collision handling | MIG-01..03 |
| COL | `shared/security/GlobalRevocationGate.java` | PostgreSQL ACTIVE snapshot with 30-second default freshness, fail closed | Preserve bounded staleness, outage behavior, replay resistance | INT-01 |
| COL | `shared/security/RevocationSnapshotConsumer.java`, `RevocationSnapshotStore.java` | Consumer-side security snapshot processing | Actual authenticated HTTP ACK and recovery, not simulated | INT-01 |
| COL | `frontendweb/src/pages/auth/LoginPage.tsx` | Collectra form for tenant/platform email/password | OIDC redirect + PKCE/BFF; tenant context remains app-local | BFF-01 |
| COL | `frontendweb/src/features/auth/model/auth-context.tsx` | Local token bootstrap, login, refresh/logout flows | BFF session bootstrap, logout and guarded routes | BFF-02..03 |
| COL | `frontendweb/src/shared/auth/token-storage.ts` | Refresh token in `sessionStorage`, access token in memory | No browser-visible refresh token in target BFF | BFF-02 |
| COL | `frontendweb/src/features/auth/api/auth.api.ts` | Calls legacy auth endpoints | Switch migrated-user path to BFF/OIDC endpoints under rollout flag | BFF-01..03 |
| AC | `app-common/infra/realm-shared.json` | `registrationAllowed=false`; `verifyEmail=true`; machine test clients have `standardFlowEnabled=false` | Explicit controlled registration, real web OIDC client and SMTP verification | REG-01..07, BFF-01 |
| AC | `app-common/.../registry/PlatformRegistry.java` | Inserts app `REGISTERED` record | R4 asynchronous Keycloak client provisioning, separate gate | APP-01 |
| AC | `app-common/.../revocation/KeycloakAdminClient.java` | Disable user and logout | R3.4 controlled enable/delete saga with audit and retries | UNBLOCK-01, DELETE-01..03 |
| AC | `app-common/.../revocation/SubjectAccessService.java`, `EnforcementReconciler.java` | Global block + consumer obligations | Dynamic N-consumer snapshot, actual ACK and recovery proof | INT-01 |

**Inventory completeness boundary:** the table identifies the inspected API and security entry points. Before marking R3.0A fully VERIFIED, perform repository-wide route extraction and inspect implementation bodies of `AuthService`, `PlatformAuthService`, `UserLifecycleService`, `OtpService`, migrations, config profiles, all React HTTP call sites and tests. Verify whether any additional legacy password authority or migration flag exists. This audit is not a claim that every code path was reviewed.

## 3. RED contract implementation

**File:** `src/test/java/io/collectra/api/shared/security/IamR30MigrationRedContract.java`

This JUnit 5 test class is deliberately named `*RedContract`, not `*Test`, so ordinary Surefire test discovery does not unexpectedly break current CI. It uses only existing JUnit/AssertJ dependencies and tests three missing implementation contracts:

| ID | Executable RED test | Expected failure until implementation |
| --- | --- | --- |
| RED-MIG-01 | `explicitCredentialAuthorityModesMustExist` | No production `CredentialAuthorityMode` enum with LEGACY, MIGRATION, KEYCLOAK_ONLY |
| RED-MIG-02 | `startupMustValidateCredentialAuthorityConfiguration` | No production `CredentialAuthorityStartupValidator.validate` |
| RED-MIG-03 | `migratedIdentitiesMustHaveExplicitLegacyCredentialDenialPolicy` | No production `LegacyCredentialCutoverPolicy.denyLegacyCredentialForMigratedIdentity` |

Run from repository root:

```bash
mvn --batch-mode --no-transfer-progress -Dtest=IamR30MigrationRedContract test
```

**Expected result now:** Maven test failure with the missing contract identified. This is a *design-level executable RED*, not yet a PostgreSQL/Keycloak migration integration test. Do not mark RED observed until actually executed; do not mark GREEN when only these three class/method existence checks pass.

## 4. Next mandatory executable behavioral RED tests (R3.0B)

Add PostgreSQL Testcontainers tests using actual Collectra Liquibase migrations and test identities. Test `(issuer,subject)` unique link and immutable local UUID/FK, same-email collision denial, concurrent linking, idempotency and rollback. Add actual HTTP tests for login, refresh, reset and invitation acceptance in LEGACY, MIGRATION and KEYCLOAK_ONLY modes. Add startup context tests for fail-closed missing issuer/audience, Keycloak outage and accidental downgrade. Add Playwright browser tests for PKCE, session cookie and no browser refresh token. These must be real behavioral tests, not class existence checks.

**Acceptance sequence:** RED observed → implement minimum GREEN → run existing security and tenant regression → real Keycloak/PostgreSQL/React E2E → separate AC/COL/INT reports → independent S2 review. Do not switch production auth or delete users in R3.0A.

## 5. Status and evidence

- Source audit and migration map: **DOCUMENTED / PARTIAL inventory pending deeper method, migration and route scan**.
- RED JUnit source: **COMMITTED / NOT EXECUTED**.
- PostgreSQL behavioral RED: **NOT IMPLEMENTED**.
- Existing production behavior: **UNCHANGED**.
- CI / Keycloak / browser tests on current SHA: **NOT RUN**.
- R3.0A release gate: **OPEN**, no production cutover.
