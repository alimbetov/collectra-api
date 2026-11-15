# IAM-R3 final specification review and controlled implementation kickoff

**Review date:** 2026-11-15  
**Status:** CONDITIONAL APPROVAL for R3.0B test-first work; **HOLD** for production credential cutover.  
**Assets:** App Common (AC), Collectra (COL); INT is a separate integration acceptance track.

## Decision

The IAM-R3 specification is sufficiently coherent to begin **test-first migration hardening**, but not sufficient to change production login or declare release readiness. Maintain one shared Keycloak end-user realm; AC owns global identity orchestration and revocation, COL owns local user IDs, tenants, memberships and financial history. No new branches, no main merge.

## Final review findings

| ID | Severity | Finding | Resolution / gate |
| --- | --- | --- | --- |
| REV-01 | P0 | Legacy tenant and platform password login, refresh and reset remain operational | Implement server-controlled authority mode with explicit no-downgrade startup policy, then behavioral HTTP tests |
| REV-02 | P0 | Current RED HTTP tests are standalone MockMvc with mocked services; no runtime mode exists | Keep as proposed contracts only; add full-context HTTP tests, real PostgreSQL and Spring Security |
| REV-03 | P0 | Current PostgreSQL RED asserts a specific `user_accounts.credential_authority` column, but schema ownership is not yet approved | Treat this as **design proposal**, not immutable acceptance criterion; approve durable state model (column or dedicated table), then test behavior |
| REV-04 | P0 | Verified identity link table already enforces both global subject and local user uniqueness, but identity ownership proof is external to this table | Test proof-based linking, collision denial, concurrency, immutable local UUID and replay |
| REV-05 | P0 | Legacy refresh token reuse and password reset must not remain valid for migrated identities | Negative tests for tenant and platform login/refresh/reset, plus session family revocation |
| REV-06 | P1 | Browser sessionStorage refresh token conflicts with target BFF | R3.2B server session migration with browser E2E |
| REV-07 | P1 | AC registry REGISTERED state is not proof of Keycloak client provisioning | R4 separate gate; never count as R3 ready |
| REV-08 | P1 | Historical smoke evidence not tied to current SHA; CI evidence missing | Exact-SHA Maven/Testcontainers/Keycloak/Playwright runs and S2 review |
| REV-09 | P1 | Keycloak verification/reset SMTP differs from KumoMTA business delivery | Distinct AC and COL test gates |
| REV-10 | P1 | Global identity deletion and COL business retention are separate responsibilities | Approved deletion saga and legal retention gate |

## Controlled implementation sequence

1. **R3.0B-1:** verify current SQL 054 identity-link invariants on PostgreSQL. Extend fixture to cover two-way uniqueness, revoked links, required verified timestamp and FK preservation. This is started in `IamR30bIdentityMigrationPostgresRed.java`.
2. **R3.0B-2:** run explicit RED commands, capture Surefire XML, actual failures, Docker/PostgreSQL versions and SHA. Do not mark tests GREEN without execution.
3. **R3.0B-3:** approve migration state schema and authorization policy, including per-user authority, per-application rollout, old JWT lifetime and token-family invalidation. Prefer DB-backed durable state and fail-closed policy. The legacy JWT decoder must never be enabled by a missing property in production.
4. **R3.0B-4:** add real Spring Boot HTTP + PostgreSQL tests for migrated tenant/platform users (login, refresh, reset, invitation acceptance) and negative downgrade. Do not use request-supplied mode.
5. **R3.0B-5:** only after observed RED, implement minimal production migration mode/guards and transactional persistence; keep legacy mode for nonmigrated users during explicitly scoped migration.
6. **R3.0B-6:** verify COL business regression, AC standalone security, INT cross-asset revocation, and independent S2 APPROVE before enabling production cutover.

## Test evidence commands

```bash
mvn --batch-mode --no-transfer-progress -Dtest=IamR30MigrationRedContract test
mvn --batch-mode --no-transfer-progress -Dtest=IamR30bIdentityMigrationPostgresRed test
mvn --batch-mode --no-transfer-progress -Dtest=IamR30bLegacyHttpCutoverRed test
```

The deliberate RED classes are not discovered by ordinary Surefire naming. Do not add them to mandatory CI as permanent failing tests. Convert accepted behavioral cases into normal `*Test` / `*IT` suites once GREEN is implemented and qualified.

## Evidence and release status

- **AC:** lifecycle incomplete; not release qualified.
- **COL:** legacy auth operational in code; migration not qualified.
- **INT:** previous smoke is historical, not proof for current SHA.
- **Current action:** test code and docs committed only. No Maven/Docker execution, no CI run verification, no production code/migration changes. No claim of observed RED or GREEN.


## Implementation kickoff — R3.0B executable evidence

Committed in the existing branch:

- `src/test/java/io/collectra/api/IamR30bFullContextCutoverRed.java`: real `@SpringBootTest`, full Spring Security filter chain, existing Liquibase schema and PostgreSQL Testcontainers via `AbstractIntegrationTest`. It creates a real tenant user through the existing service and expects KEYCLOAK_ONLY to reject legacy password login and legacy refresh over HTTP. This is **expected RED** until a server-configured authority policy exists.
- `.github/workflows/iam-r30b-red.yml`: **manual-only** workflow that runs isolated structural, PostgreSQL, standalone HTTP and full-context RED tests, captures Surefire XML, Maven log, SHA and run ID as an artifact. A nonzero Maven exit is **not sufficient** to prove behavioral RED: reviewer must confirm compilation succeeded, Docker/Testcontainers started, and the intended assertion failed rather than an infrastructure error.

**Execution status:** not run from this session; no observed RED, no GREEN, no production cutover. To trigger, use GitHub Actions → IAM R3.0B RED qualification (manual) → Run workflow on `feat/iam-shared-platform`. GitHub may require a workflow to be present on the default branch for manual dispatch; if unavailable, execute the Maven command locally on the feature branch and attach exact-SHA Surefire logs. Do not merge into main merely to enable workflow dispatch.

**Acceptance blockers before production code:** verify RED results, approve durable credential-authority state design, add per-user migrated-state negative tests for login/refresh/reset and concurrency on full Liquibase schema, and obtain S2 review.


## R3.0B implementation update — first production cutover guard

Implemented in `feat/iam-shared-platform`:
- `CredentialAuthorityMode` enum: LEGACY / MIGRATION / KEYCLOAK_ONLY.
- `CredentialAuthorityStartupValidator`: fail startup when MIGRATION or KEYCLOAK_ONLY is selected without Keycloak enabled.
- `LegacyCredentialEndpointGate`: in KEYCLOAK_ONLY, return HTTP 410 on POST legacy registration, tenant login, refresh, invitation acceptance, forgot/reset password and platform login/refresh. Mode is server configuration only. Existing LEGACY default behavior remains unchanged.
- Full-context RED fixture configured to start with Keycloak JWT mode and dummy issuer/JWKS addresses; no actual Keycloak connectivity is claimed.

**Not yet implemented:** per-user MIGRATION guard, durable authority state, legacy JWT denial at token validation boundary, old token family revocation, real OIDC/BFF, Keycloak registration and SMTP. The HTTP filter does not by itself prevent an already-issued legacy JWT from being accepted in a misconfigured deployment; issuer validation and startup consistency require separate negative tests. A KEYCLOAK_ONLY production rollout is **HOLD**.

**Execution:** Maven/Docker and workflow not run or verified in this session. Earlier deliberate RED tests may now differ; re-run and inspect exact-SHA Surefire output before declaring GREEN.
