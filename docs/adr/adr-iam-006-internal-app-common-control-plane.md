# ADR-IAM-006 — App Common is an internal shared control plane
Status: ACCEPTED PRODUCT-SCOPE DECISION / NOT IMPLEMENTED
Date: 2026-11-14
Branch: feat/iam-shared-platform

## Decision
App Common is the owner's **internal centralized service for the owner's own applications**. It is not a public multi-customer SaaS product, open registration service, marketplace or customer-facing identity directory. Collectra is the first planned consumer; other applications are potential future consumers, not already integrated.

App Common remains a **separate repository, deployable, infrastructure data owner and administrative control plane**. It centrally manages application registration/provisioning, Keycloak realms/OIDC clients/policies, platform operator access, shared notification transport, operational audit and common security configuration.

Each consuming application retains local user profiles and IDs, business tenants, memberships, roles, permissions, data and business authorization. The same email across applications does not automatically establish a shared identity. Keycloak remains JWT access token issuer. A centralized control plane does not require one realm or cross-app SSO.

## Internal-only security boundary
- Platform administration endpoints, Keycloak admin APIs, provisioning endpoints, metrics and databases are reachable only through approved private network/VPN/zero-trust access; no direct public exposure.
- Consumer backends access App Common service APIs over authenticated private network with audience-restricted OAuth2 Client Credentials and optional mTLS. Authorization binds service client to registered application ID and allowed operations.
- End-user browser/mobile authentication may require internet-reachable **Keycloak OIDC authorization endpoints** or an authenticated gateway, depending on client access. Internal-only App Common does **not** mean public clients can reach only private addresses. Expose only the minimal OIDC endpoints required; never expose Keycloak Admin Console publicly by default.
- Public business APIs (if any) are owned by the respective consumer application, not proxied through App Common. App Common is not a synchronous authorization dependency for ordinary business requests.
- Operator accounts are distinct from consumer application admins. No self-service creation of external customer organizations; app registration requires owner/operator approval.
- Cross-application SSO, centralized global identity, shared business roles, external third-party developer access and public billing are **out of scope** unless separately approved.

## Scope refinements
- `Application Registry.owner` is an internal accountable team/person, not an external customer or billable tenant.
- Multi-application isolation remains mandatory even when every application has the same owner: separate realms, audiences, service credentials, secrets, notification recipient scopes and local business data.
- Retain two-realms isolation test fixture. Avoid unnecessary public SaaS functions: external tenant onboarding, subscription/billing, public API keys and arbitrary external application registration.
- Use private DNS/service discovery and environment-specific ingress policy. Define a deployment-specific Keycloak OIDC exposure profile for web/mobile consumers.
- App Common operator UI is private-only and subject to MFA, least privilege and audit.

## New acceptance cases
- INT-01 Internet-origin unauthenticated access to App Common operator and provisioning endpoints is denied at ingress.
- INT-02 Registered Collectra machine identity accesses only its approved App Common service APIs; another app cannot impersonate it.
- INT-03 Consumer user JWT cannot call App Common platform operator API.
- INT-04 App registration without authorized internal operator is denied; no public self-service signup.
- INT-05 Browser/mobile OIDC flow succeeds through explicitly approved Keycloak ingress without exposing admin endpoints.
- INT-06 App Common outage does not block ordinary Collectra requests with valid JWT and fresh local authorization/policy.
- INT-07 Same email in two app realms does not merge identity or business roles.
- INT-08 Public business application endpoint cannot be used as a proxy to App Common admin functions.

## Precedence
This ADR refines ADR-IAM-004 and APP-COMMON-R0 scope. It does not alter ADR-IAM-005 token issuance. Existing multi-application security requirements still apply; references to App Common as a public SaaS, public app onboarding or globally shared user database are non-normative.

## Verification status
Architecture decision recorded. No runtime, ingress, deployment, test or independent S2 approval claimed.
