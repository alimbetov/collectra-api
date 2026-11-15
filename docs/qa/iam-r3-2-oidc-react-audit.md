# IAM-R3.2 — React / Keycloak / JWT audit and first code slice

Branch: feat/iam-shared-platform
Status: IN PROGRESS; NOT VERIFIED.

## Confirmed repository facts
- React LoginPage calls legacy /api/v1/auth/login/by-slug or /api/v1/platform/auth/login with password.
- AuthProvider bootstraps by checking refresh token; refresh token is persisted in browser sessionStorage.
- HTTP client refreshes through legacy Collectra endpoints, not Keycloak OIDC.
- Shared Keycloak realm has verifyEmail and machine clients; no browser client existed before this slice.
- Collectra JwtDecoder validates issuer, collectra-api audience and verified identity link; domain filter derives tenant membership and RBAC from Collectra.

## Implemented in this slice
- Added distinct collectra-web public client for local development, authorization code enabled, S256 PKCE required, implicit and password grants disabled.
- Exact localhost:5173 callback/origin allowlist; no wildcard redirects.
- Added Vitest contract covering PKCE, redirects, audience, and machine-client isolation.

## Security blockers before enabling browser login
1. Implement actual authorization redirect, cryptographic state/nonce/PKCE verifier generation and strict callback validation; never send password to Collectra in Keycloak mode.
2. Prefer server-side BFF and HttpOnly Secure SameSite session cookie; current sessionStorage refresh token must be retired in Keycloak mode.
3. Add backend session callback/token exchange with state binding and CSRF protections if BFF is chosen; verify audience, issuer and app-local membership on protected APIs.
4. Existing KeycloakDomainAuthorizationFilter must fail closed for users whose credential authority has not been cut over, including correct handling of invalid tenant UUID headers and missing issuer.
5. Realm import and actual Keycloak client settings must be verified against pinned runtime; production HTTPS callback URLs are not configured.
6. Run browser E2E and real Keycloak/PostgreSQL API security matrix; no observed test results in this slice.

## Gate
Do not enable production OIDC cutover or claim AUTH/SESSION/SSO PASS. IAM-R3.1 SMTP and REG evidence remain open independently.
