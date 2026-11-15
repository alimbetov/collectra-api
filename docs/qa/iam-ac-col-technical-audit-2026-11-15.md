# IAM AC/COL technical audit — 2026-11-15

Branch: feat/iam-shared-platform
Release gate: HOLD.

## Source of truth
ADR-IAM-007 and dual-contour context guard. AC and COL are independent services and data owners; BFF is an optional browser adapter, not the IAM platform architecture.

## Confirmed source-level findings
- AC app-common is a separate Maven project with its own PostgreSQL datasource, Liquibase, OAuth2 client and resource-server security.
- COL remains a separate Spring Boot API with local identity and tenant membership.
- AC browser OIDC and JWT API use distinct filter chains; JWT audience validation is implemented for AC.
- AC BFF proxy is disabled by default. Its route policy now permits only GET /api/v1/identity/me; no administrative or mutation route is allowed.
- COL CurrentUserController relies on a local UUID principal, membership_id claim, ROLE_HUMAN and TenantContext; a raw Keycloak subject cannot be assumed to satisfy this legacy contract.
- React AuthProvider and http-client still use legacy access/refresh token flow; new BFF transport is opt-in and not integrated.
- AC browser OAuth2 client is configured as a public client; confidential BFF client and production cookie topology remain unqualified.
- Source-level fix: malformed proxy request path now fails closed; GET bodies rejected.

## Critical defects/gaps to resolve before React cutover
P0: COL Keycloak JWT to verified (issuer,sub) to local UUID and membership resolution, including credentialAuthority and account status; no email-only link and no role inheritance from AC.
P0: Validate global revocation enforcement and ACK between AC and COL with real PostgreSQL and Keycloak; check token residual window.
P0: Real Maven/TypeScript/Vitest builds, with logs and exact SHA; no CI evidence at audit time.
P0: Browser BFF and COL audience alignment. Current AC OAuth2 client token audience must be verified as collectra-api and COL decoder/authorization must accept only the intended audience.
P1: Durable OAuth2 authorized-client/session persistence, cookie/CSRF/origin and reverse-proxy hardening.
P1: React feature-flagged BFF migration after P0 gates; no refresh token in browser storage in BFF mode.
P1: Real dual-contour MacBook UAT with restart and failure cases.

## Verification
GitHub combined statuses and PR workflow runs for 252aad8 were empty. Attempted direct git ls-remote from execution environment failed due DNS/network. Therefore no Maven, npm or real Keycloak/PostgreSQL test has been executed by this audit. Do not infer GREEN from committed tests or workflows.

## Release criteria
Separate AC GREEN and COL GREEN, verified AC-COL revocation/ACK, negative tenant/RBAC and audience tests, browser OIDC/CSRF/logout tests, recovery, and independently recorded UAT logs. main merge and production cutover prohibited until acceptance.
