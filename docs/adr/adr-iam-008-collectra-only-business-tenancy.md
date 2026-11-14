# ADR-IAM-008 — Shared App Common identity; business tenancy required only in Collectra
Status: ACCEPTED PRODUCT ARCHITECTURE DECISION / NOT IMPLEMENTED
Date: 2026-11-14
Branch: feat/iam-shared-platform

## Decision
App Common is the owner's internal common platform for all owned applications. It manages **one shared Keycloak end-user realm**, one global user identity, SSO, authentication policy, MFA, credential recovery, application registry, OAuth2 client provisioning, notifications (email R0; SMS/push later), security/audit and operational capabilities.

**Only Collectra is required to implement business-data tenant isolation in the current scope.** Other consuming applications must not inherit Collectra's tenant, organization, membership or role model by default. They may have their own application-specific domain authorization when needed, without any mandatory tenant abstraction.

This ADR refines ADR-IAM-007 (shared realm/SSO), supersedes all requirements that mandate business tenancy in App Common or in every consuming application, and preserves ADR-IAM-005 (Keycloak-issued JWT and strict resource audience validation).

## Ownership and isolation matrix
| Concern | App Common / Keycloak | Collectra | Other applications |
|---|---|---|---|
| Global identity, verified email, credentials, MFA, SSO | Keycloak authority; App Common control plane | Consumer | Consumer |
| OAuth2 clients, audience, platform policy | App Common | Own resource-server validation | Own resource-server validation |
| App user profile / preferences | Optional shared minimal identity only | Local business profile | Optional local profile |
| Business tenant / organization / membership | No business tenancy | Required, enforced locally | Not required by shared platform |
| Business roles and permissions | No shared business roles | Local roles and memberships | Application-defined, if needed |
| Email notifications | Shared transport, template/recipient scope per application | Producer and recipient resolver | Producer and recipient resolver if needed |
| Global user disablement | Shared identity control; enforcement across apps | Respect global state | Respect global state |
| Application-specific access block | App registry and local app policy | Local account/tenant checks | App-local checks if applicable |

## Critical distinction
No tenant isolation requirement for other apps **does not** waive security isolation between application APIs: distinct OAuth2 clients, audiences, allowed scopes, redirect URIs, service identities, secrets, and notification recipient access remain mandatory for every application. A JWT for Collectra API is not a bearer credential for another API even though `iss` and `sub` are shared.

The user can authenticate to all connected apps without registering again. First-time entry to each app must be idempotent and safe: if an app requires local profile creation, create it under app ownership; if it does not, it can operate directly with shared identity plus local authorization rules. Collectra tenant membership is never inferred from SSO.

## Notification service
R0: app-scoped transactional email through authenticated service clients, durable outbox, recipient verification and app-specific templates. Future channels SMS and push require separate ADR, provider integration, consent policy and delivery acceptance tests. Shared notification service does not mean one app can enumerate or message another app's recipients.

## Acceptance
- TEN-01 One Keycloak global account logs into Collectra and a second app using the same `(iss,sub)`, without second registration.
- TEN-02 Collectra enforces tenant isolation for data and operations; cross-tenant request is denied even with valid SSO JWT.
- TEN-03 Second app authenticates and operates without Collectra tenant ID or membership.
- TEN-04 Collectra JWT audience is rejected by second app API and vice versa.
- TEN-05 User can be locally denied Collectra tenant access without disabling shared Keycloak identity or second app.
- TEN-06 App Common Notification API accepts authorized per-app email requests but rejects cross-app recipient access.
- TEN-07 Disabling a global Keycloak user prevents fresh sign-in to all apps; residual JWT enforcement is documented and tested.
- TEN-08 New app onboarding creates a client/audience in existing shared realm, never a new end-user realm.
- TEN-09 Decommissioning an app preserves shared realm, global user accounts and other apps' clients.
- TEN-10 Other apps are not required to implement Collectra tenant/membership tables or claims.

## Status and precedence
Documentation decision only. No runtime changes, Keycloak deployment or E2E tests performed. ADR-IAM-008 and ADR-IAM-007 take precedence over per-app realm or universal business tenancy statements in older ADRs and specs.
