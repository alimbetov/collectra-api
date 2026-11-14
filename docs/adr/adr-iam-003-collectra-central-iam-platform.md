# ADR-IAM-003 — Collectra as Central IAM Platform
Status: ACCEPTED DESIGN DIRECTION / IMPLEMENTATION NOT VERIFIED
Date: 2026-11-14
Branch: feat/iam-shared-platform

## Decision
Collectra is the **product and operational owner** of a reusable central identity, authentication, notification and operational platform for all future applications. This does **not** make the existing Collectra business API or its tenant/role tables the shared authorization authority.

Centralize: Keycloak deployment and realm lifecycle; authentication flows; credential policy; verified email; password recovery; MFA; sessions; application registration/provisioning; shared notification transport; security audit; monitoring; secret management; backups and recovery.

Decentralize: application-local user/profile records and IDs, business roles/permissions, tenants, memberships, domain authorization, business data, and app-specific audit. These remain in the corresponding application's database and services.

## Naming and deployable boundaries
- **Collectra Central IAM Platform (control plane):** platform operator API and Application Registry. Owns application onboarding and configuration, not business roles.
- **Keycloak (identity data plane):** one independently configured realm per application. Keycloak accounts are scoped to a realm; same email in two realms means two separate credentials/accounts unless a future explicitly approved federation ADR changes that rule.
- **Shared Notification Platform:** isolated application/realm routing, provider integrations, delivery status, retries, suppression, operational audit. Does not become a global address directory.
- **Collectra Business Application:** existing tenant, membership, user_accounts, platform-role and business workflows remain app-local. Distinguish Collectra business platform super-admin from central IAM operator.
- **Other applications:** their own API and data stores, local user mapping and authorization adapters. No dependence on Collectra business API for per-request authorization.
- Deployable processes may initially share infrastructure and repositories but must maintain separate module ownership, security principals, DB permissions and API boundaries. No implicit cross-schema joins.

## Identity and trust
- Authentication token issuer is the application's dedicated Keycloak realm; application backend validates issuer, audience, signature, expiry, client binding and token context.
- Each application resolves `(issuer, subject)` to its own local user ID, checks application-specific status, tenant membership and permissions. Email is contact information, not a global user key.
- No cross-realm SSO, account merge or automatic linking at this stage. Shared deployment and administration do not imply shared login or account.
- Central IAM operators may manage realm configuration and security incidents, but cannot assign Collectra TENANT_ADMIN, App-B business roles or access business data merely by being platform operators.
- Application administrators may manage their local business users and roles but cannot create realms or change global IAM security policy.
- Privileged IAM operations require fresh MFA, separation of duties, immutable audit and two-person approval for destructive or recovery operations.

## Service contracts
1. Central IAM management API: register application, configure approved clients/redirects/audiences, rotate secrets, suspend/decommission realm, retrieve deployment status. No end-user business role mutation.
2. Application onboarding: OIDC login + verified email; app-owned idempotent provisioning creates local user and optional tenant/membership; mapping `(issuer,subject)` is application-local.
3. Notification API: authenticated machine-to-machine app identity, application/realm derived from service credentials (not untrusted payload), recipient local ID resolved within app boundary, verified-email and consent checks, idempotent delivery and delivery evidence.
4. Security event API: issuer-scoped event metadata, integrity/replay protection, no raw credentials or action links. Apps consume only their own realm events.
5. Operations: health, audit, metrics, backup/restore, realm configuration export, upgrade/rollback and incident response.

## Resilience
- Central IAM outage blocks new login, password reset and MFA step-up, but may not automatically block already authenticated app API requests while JWT remains valid, local authorization is active, and cached signing keys are valid.
- No synchronous central IAM lookup is required for ordinary application business requests. High-risk operations may fail closed pending fresh assurance.
- A compromise or suspension of one realm must be contained by client, issuer, DB, secret and notification boundaries; a full shared Keycloak infrastructure compromise remains a shared blast-radius risk.
- Operational targets and test requirements from IAM-R1.3 remain in force.

## Explicit non-goals
- Global user database, universal user UUID, automatic cross-application SSO, shared business RBAC, shared tenants, cross-app profile merging, routing all business requests through central IAM, direct application SQL reads of Keycloak tables.

## Acceptance tests
- CENTRAL-01 platform operator creates an App-B realm/client through controlled provisioning without changing Collectra business accounts or roles.
- CENTRAL-02 same email in Collectra and App-B yields independent realm accounts and local application users.
- CENTRAL-03 valid App-B JWT cannot authorize Collectra API and vice versa.
- CENTRAL-04 central IAM operator cannot grant or inherit business role in either app.
- CENTRAL-05 App-B admin cannot modify central IAM realm configuration.
- CENTRAL-06 notification service cannot resolve or deliver another application's recipient.
- CENTRAL-07 ordinary app API remains authorized with valid cached JWT/JWKS and active local permissions during IAM outage; new login and step-up fail safely.
- CENTRAL-08 app suspension, secret rotation and decommissioning cannot modify another application's realm or business DB.
- CENTRAL-09 migration of existing Collectra users preserves UUIDs, FKs, tenant memberships and business permissions.
- CENTRAL-10 independent app service credentials cannot impersonate another application.

## Migration and sequencing
1. Preserve existing Collectra business auth behavior under migration flags; do not move business user/tenant tables into central IAM.
2. Implement Central IAM control-plane module, Application Registry and Keycloak realm-as-code with two independent test realms.
3. Implement Collectra application identity adapter and legacy credential-authority cutover with RED/GREEN tests.
4. Implement notification app-scoped M2M authentication, verified recipient resolution and SMTP E2E.
5. Implement App-B reference integration against actual app contract when available.
6. Run security isolation, concurrency, recovery and operational tests; independent S2 sign-off before production.

## Supersession
This ADR clarifies the phrase 'Collectra must not be the authorization center': **Collectra is the central IAM platform owner**, but **Collectra business authorization is not global**. It complements IAM-R1.2/R1.3. Where a prior document suggests that Collectra cannot be the central identity platform, this ADR supersedes that interpretation. No runtime or CI verification is claimed.
