# ADR-IAM-007 — One realm, shared global identity and SSO across owned applications
Status: ACCEPTED PRODUCT ARCHITECTURE DECISION / IMPLEMENTATION NOT VERIFIED
Date: 2026-11-14
Branch: feat/iam-shared-platform

## Decision
App Common is an internal platform for the owner's applications, with **one shared Keycloak end-user realm** and **one global user identity directory owned by Keycloak**. A user registers once, receives a stable `(issuer,sub)`, and can authenticate to every connected application with SSO. The same Keycloak account is used across Collectra and future applications. App Common orchestrates the shared realm, application registry, authentication policy, account lifecycle, notifications and audits. Keycloak is the credential and global identity authority; App Common does not mint access tokens or duplicate password storage.

This decision **supersedes** the per-application realm, same-email-isolated-identity, and no-cross-app-SSO provisions of ADR-IAM-004, ADR-IAM-006, APP-COMMON-R0 and IAM-R1.2/R1.3. ADR-IAM-005 remains authoritative for Keycloak-issued JWT and API validation, with issuer now common across applications and audience/client/scope isolation mandatory.

## User model
- Global identity: Keycloak user `sub` (stable within shared realm); shared account, email verification, credentials, MFA, password reset and global login session.
- Each application maintains its own `application_user` / local UUID, profile, memberships, tenants, permissions and app-specific consent. A local row links to the shared `(issuer,sub)` with unique active mapping per application and subject. One global user may have different local IDs and business roles in different applications.
- **Eligibility:** all globally registered users may sign into any connected app, subject to app availability and explicit account suspension. First login provisions the local app profile through a verified, idempotent, application-owned onboarding flow. No cross-app business permission is inherited.
- App-specific membership/organization/paid capability and protected action still require local authorization; being globally signed in does not grant tenant or admin rights.
- Same email in the same shared realm is one account only when verified under Keycloak's configured uniqueness and linking policy. Never merge legacy local accounts across applications by email alone; migration requires verified ownership and trusted identity linking.
- Global account disablement blocks new authentication; previously issued JWTs require documented resource-server enforcement policy. App-specific blocking must not globally disable the user.

## OAuth2 / OIDC
- Shared issuer: one configured Keycloak realm issuer for all business end-user clients.
- Separate OIDC clients and resource audiences per app: `collectra-api`, `qrm-api`, `keruenix-api`, etc. JWT for one API is never accepted by another unless explicit multi-audience authorization is approved.
- Browser/mobile Authorization Code + PKCE; Keycloak SSO session can issue a new app-specific token without reentering credentials, subject to MFA and consent policies. Client-specific redirect URI allowlists, PKCE, token TTL, allowed origins and scopes.
- Machine-to-machine clients and App Common operator realm remain separated by service audience and policy. **One shared end-user realm does not require reusing that realm for privileged App Common operators**; operator identities should remain in an isolated admin realm for blast-radius containment.
- Logout semantics must distinguish app-local logout vs global Keycloak SSO logout. Global logout terminates IdP sessions but already issued JWTs can remain valid until expiry unless apps enforce revocation/session status.

## Cross-app isolation and threats
Common realm increases blast radius of compromised accounts, compromised clients and mistaken identity links. Enforce per-app audience, client/scope allowlist, local user status/authorization, confidential client secrets, rate limits, MFA step-up and app-specific risk controls. App Common must not become a synchronous dependency for every business request. Never place sensitive app business roles in shared realm tokens as global authority.

## Migration
Collectra existing local UUID, tenant, memberships, foreign keys and business roles remain unchanged. Map verified legacy Collectra account to shared `(issuer,sub)`; atomic credential authority cutover and local session revocation, no email-only auto-link. Other apps implement their own verified migration. Cross-app legacy account collisions require explicit ownership resolution, never automatic account merging.

## Required end-to-end journeys
- SSO-01 Register once in Collectra -> login to second app with same Keycloak account -> app-local profile provisioned; same `sub`, distinct app-local UUID.
- SSO-02 JWT audience `collectra-api` rejected by second app API, and vice versa, despite same issuer/sub.
- SSO-03 First login to second app without an existing local profile provisions one record idempotently under concurrent requests.
- SSO-04 Global password reset and MFA enrollment apply to same global identity across applications; local roles unchanged.
- SSO-05 App-specific block denies only that app; other app login/business access unaffected.
- SSO-06 Global identity disablement prevents new login across apps; existing token residual window bounded by approved revocation policy.
- SSO-07 App-local logout vs global SSO logout semantics demonstrated in two browsers and two apps.
- SSO-08 Existing Collectra legacy local UUID, tenant memberships and permissions preserved after linking.
- SSO-09 Malicious app cannot access other app's business API or elevate shared account permissions.
- SSO-10 Same email legacy collision across two apps triggers verified linking/review, never implicit merge.
- SSO-11 Operator API access is rejected for ordinary shared-realm end-user token.
- SSO-12 Shared realm key rotation and outage behavior tested across at least two app resource servers.

## Supersession map
- ADR-IAM-004: retain separate products, ownership, local RBAC, service isolation; replace 'separate realm for each app', 'no cross-realm SSO' and 'same email in different realms' assumptions.
- ADR-IAM-006: retain internal-only platform and private operator APIs; replace independent consumer realms with shared end-user realm.
- APP-COMMON-R0: replace per-app realm provisioning with per-app **client/audience/permission** provisioning inside shared realm; do not delete or reconfigure the shared realm when an individual app is decommissioned.
- IAM-R1.2/R1.3: global identity now shared, app-local onboarding and migration preserved; any App-B isolated-realm fixture becomes a second client/application fixture in the shared realm.
- ADR-IAM-005: Keycloak remains sole JWT issuer; shared issuer plus strict per-API audience validation.

## Verification status
Documentation decision only. No new App Common runtime, Keycloak configuration, two-app SSO E2E, PostgreSQL tests or independent S2 approval claimed.
