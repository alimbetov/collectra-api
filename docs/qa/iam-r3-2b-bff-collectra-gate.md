# IAM-R3.2B — BFF-to-Collectra implementation slice

Branch: feat/iam-shared-platform. Status: IN PROGRESS, NOT VERIFIED. Production: HOLD.

Implemented:
- Conditional App Common REST proxy /api/v1/iam/collectra/api/v1/**; default disabled.
- Server-side OAuth2 authorized-client access token; no browser Authorization header forwarded.
- Method, path, upstream URL, request/response headers, timeout and payload restrictions.
- Tenant UUID selector forwarded, never interpreted as authority. Collectra must check membership and RBAC.
- React opt-in tokenless BFF transport with CSRF bootstrap; not wired into legacy AuthProvider.
- Unit/contract tests added but not executed.

Release blockers:
1. Maven and Vitest/typecheck on exact SHA; no PASS evidence.
2. Browser E2E with real Keycloak and PostgreSQL, including invalid state, PKCE, CSRF, token expiry, wrong tenant and logout.
3. Security review of path decoding, query encoding, HTTP method semantics and upstream behavior. Narrow route allowlist before production.
4. Confirm collectra-api audience and verified local identity cutover.
5. Durable server-side session and authorized-client storage for multi-instance deployment.
6. Explicit feature-flagged React AuthProvider integration; legacy sessionStorage refresh must not be used in BFF mode.
7. Real callback/cookie topology and Keycloak outage/restart tests.
8. IAM-R3.1 SMTP/REG evidence remains open.

No release approval or test PASS claimed. No main merge.
