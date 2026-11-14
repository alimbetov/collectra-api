# ADR-IAM-004 — App Common is the central platform; Collectra is a client application

Status: ACCEPTED DESIGN DECISION / IMPLEMENTATION NOT VERIFIED
Date: 2026-11-14
Working branch: feat/iam-shared-platform

## Canonical naming and ownership
There are two named products in the current scope:
1. **App Common** — an independent, separately deployable central platform with its own API, infrastructure database ownership, administrative UI and operational lifecycle. Owns IAM/Keycloak orchestration, application registry, identity policy, authentication infrastructure, verification, recovery, MFA configuration, notification transport, platform audit, monitoring, secrets integration, backup and restore.
2. **Collectra** — a business application consuming App Common services. Owns its business users/profiles and UUIDs, tenants, memberships, RBAC, business authorization, domain entities and business data. Existing Collectra business functionality is not moved into App Common.

**App Common is not a module inside `collectra-api`.** Target: independent repository, independent CI/CD and deployment. The actual new repository URL/name is to be assigned when created; this ADR does not assert that a repository exists or that code has been moved. This specification remains in `collectra-api` temporarily as a migration/coordination contract.

## Trust and application boundaries
- App Common manages a dedicated Keycloak realm for Collectra and one separate realm for each future business application. App Common's own operator access uses an independently secured administration identity context; do not reuse Collectra business PLATFORM_SUPER_ADMIN as an App Common operator.
- Authentication and credential lifecycle are centralized through App Common-managed Keycloak. Keycloak is the credential authority; App Common is the control plane. Collectra verifies OIDC JWTs against its allowed issuer/audience and maps `(issuer,subject)` to its own local user ID.
- Collectra enforces local account status, tenant/membership, business roles and permissions. No shared global user directory, local-user UUID, tenant table, or business RBAC in App Common.
- No automatic cross-realm SSO, account merge or email-based linking. Same email in different realms does not imply the same identity.
- App Common's Notification Service authenticates each application as a machine client, derives application/realm scope from trusted credentials, resolves recipient ownership through an app-scoped contract, and does not expose cross-app recipient data.
- Normal Collectra business requests must not synchronously call App Common. Valid signed JWTs and Collectra's own authorization remain sufficient under the defined revocation policy; fresh authentication/step-up fails closed when central IAM is unavailable.

## Target deployment boundary
```text
                      App Common (separate repository/deployment)
                +----------------------------------------------+
                | Admin UI | Platform API | Application Registry|
                | Keycloak realm management | Notification API |
                | Audit | Monitoring | Secret/Backup integration|
                +----------------------+-----------------------+
                                       |
                         Keycloak / PostgreSQL / SMTP
                                       |
                        OIDC / machine-to-machine APIs
                                       |
                  Collectra (separate repository/deployment)
                  Local user mapping, tenant, RBAC, business API
```
- Separate runtime identities and database permissions; avoid direct cross-service SQL joins.
- App Common infrastructure data and Collectra business data have separate logical ownership; physical PostgreSQL co-location is allowed initially only with strict isolation and tested backup/restore boundaries.
- Future applications connect via versioned OIDC and service API contracts without importing Collectra's domain model.

## Required implementation changes
1. Create an independent App Common repository and deployable project, with explicit API, database migrations, security configuration, admin UI and CI/CD. Do not assume it already exists.
2. Move/copy only platform-owned specifications and infrastructure code after ownership review; preserve history and do not delete or rewrite Collectra's business migrations.
3. Implement App Common Application Registry, Keycloak realm-as-code provisioning, notification isolation and platform-operator RBAC.
4. Integrate Collectra as the first application through an issuer-aware principal resolver and local RBAC; migrate legacy credentials with tested cutover.
5. Verify end-to-end with a second **test application/realm**; `App-B` is a test fixture, not a third named production product.
6. Run PostgreSQL, Keycloak, SMTP, HTTP, browser, resilience and migration tests before any production cutover.

## Acceptance
- AC-01 App Common and Collectra build, deploy, upgrade and roll back independently.
- AC-02 Collectra business users/tenants/roles remain in Collectra; App Common cannot grant Collectra business permissions.
- AC-03 App Common provisions a Collectra realm/client without modifying Collectra business schema.
- AC-04 Valid Collectra token rejected for App Common operator API and vice versa.
- AC-05 Notification tenant/realm isolation and verified recipient enforcement proven.
- AC-06 App Common outage does not require synchronous dependency for ordinary authorized Collectra business requests.
- AC-07 Test application realm isolation proves extensibility without assuming a deployed third product.
- AC-08 Legacy Collectra UUIDs, foreign keys and sessions remain safe through tested migration.
- AC-09 Admin MFA, least privilege, secret rotation and restore drill demonstrated.
- AC-10 Independent CI/CD and versioned integration contracts documented and tested.

## Supersession and status
This ADR supersedes ADR-IAM-003's product naming/ownership where it calls Collectra the central platform. It also supersedes IAM-R1.2/R1.3 wording suggesting the central IAM control plane is a Collectra module. All unchanged security, onboarding, mapping, MFA, notification and test requirements remain applicable. App Common is the **platform**, Collectra is its **first business consumer**. Documentation approval does not imply implementation or successful tests.
