# ADR-IAM-005 — Keycloak-issued JWT Bearer tokens across App Common and Collectra

Status: ACCEPTED DESIGN DECISION / NOT IMPLEMENTED
Date: 2026-11-14
Branch: feat/iam-shared-platform

## Decision
1. **Keycloak is the sole issuer of OAuth2 JWT access tokens.** App Common configures realms, OIDC clients, identity lifecycle, MFA, authentication policies and token policy through controlled Keycloak administration. App Common does not mint or re-sign end-user access tokens.
2. **Collectra is an OAuth2 Resource Server** and accepts access tokens via `Authorization: Bearer <access_token>`. Validate JWS signature against the exact realm JWKS, accepted signing algorithm allowlist, `iss`, `aud` containing the Collectra API audience, `exp`, `nbf` when present, and application-specific client/authorized-party policy where appropriate. Reject ID tokens, refresh tokens, wrong-realm tokens, wrong-audience tokens and unsigned tokens as API credentials.
3. **Collectra authorizes locally**: resolve `(issuer, subject)` to an active Collectra user and enforce local account status, tenant, membership, roles and permissions. Neither `sub` nor email is a Collectra local UUID; client-supplied tenant and roles are not authoritative.
4. **App Common APIs are separate OAuth2 Resource Servers** with dedicated audiences and permissions. Platform administration endpoints require platform-operator authorization and fresh MFA; service-to-service notification endpoints use machine identities and narrowly scoped permissions. A token valid for Collectra API alone must not authorize App Common API.
5. Browser/mobile login: OIDC Authorization Code + PKCE (S256), confidential BFF where appropriate, exact redirect URI allowlists, HTTPS, state/nonce and secure session/token handling. Service-to-service: OAuth2 Client Credentials with separate service clients, scoped audiences, credential rotation and no reuse of browser credentials.
6. Access token TTL and revocation strategy are versioned realm policy, not hard-coded as guaranteed immediate revocation. Collectra must re-check local status/authorization version on protected requests and explicitly define maximum residual JWT lifetime; high-risk operations require fresh MFA. JWKS cache/rotation must fail safely on unknown `kid`, invalid signature or unavailable trusted keys.
7. OAuth2 access token is a **bearer credential**, JWT is the token format. Never log full tokens, put them in URLs or persist plaintext tokens in business tables. Transport over TLS. Refresh tokens are used only at authorized OIDC token endpoints and never accepted as business API Bearer tokens.
8. Token issuance, exchange, logout, refresh and revocation use Keycloak-supported OIDC/OAuth2 endpoints. App Common is the control plane, not a mandatory online proxy for every Collectra business API request.

## Security contract matrix
| Request | Expected audience | Identity and permissions |
|---|---|---|
| User -> Collectra API | `collectra-api` | Collectra realm user + local Collectra authorization |
| Collectra service -> App Common Notification API | `app-common-notification-api` | Collectra service client + notification scope, bound application ID |
| Platform operator -> App Common Admin API | `app-common-admin-api` | App Common operator realm/context + platform operator role + MFA |
| App Common provisioning -> Keycloak Admin API | Keycloak admin service context | dedicated privileged service identity, least privilege |

Audience names above are normative proposed logical identifiers; actual Keycloak client IDs and mappers must be pinned in realm-as-code and integration fixtures.

## Negative and positive integration tests
- JWT-01 Valid Collectra realm JWT with correct audience, signature and active local membership -> allowed only to permitted Collectra endpoint.
- JWT-02 Valid signature but wrong `iss` -> 401.
- JWT-03 Correct issuer but wrong `aud` -> 401.
- JWT-04 Expired, not-yet-valid, altered-signature, unsigned, unsupported algorithm or unknown `kid` -> 401.
- JWT-05 ID token and refresh token passed to API -> rejected.
- JWT-06 App Common operator token cannot authorize Collectra API; Collectra user token cannot authorize App Common admin API.
- JWT-07 Valid JWT for blocked Collectra user or revoked membership -> denied by local authorization (401/403 per documented mapping).
- JWT-08 Service token from Collectra can send only application-scoped notifications; other application's service token cannot impersonate Collectra.
- JWT-09 Client Credentials token cannot invoke user-only endpoints; browser token cannot invoke privileged machine-only API.
- JWT-10 Key rotation and JWKS caching maintain valid-token verification without accepting untrusted signing keys.
- JWT-11 Role elevation requires step-up MFA; stale assurance token denied.
- JWT-12 Missing/malformed Bearer header, query-string token, leaked token in logs -> negative assertions.
- JWT-13 Authorization Code + PKCE and refresh/logout flow E2E in React; no raw password grant.
- JWT-14 During App Common control-plane outage, Collectra validates already-issued tokens locally under revocation policy; new login/step-up cannot bypass Keycloak.

## Supersession
This ADR complements ADR-IAM-004 and supersedes any prior requirement to issue application-signed local JWTs as the target state. Legacy Collectra tokens may exist only within a documented migration window with explicit issuer/audience separation and tests. No implementation or CI PASS is claimed.
