# IAM-R3.2 — REST BFF session slice (not release qualified)

Branch: feat/iam-shared-platform. Production switch: HOLD.

## Implemented endpoints
- GET /api/v1/iam/login -> redirects to Spring Security /oauth2/authorization/collectra.
- GET /api/v1/iam/callback -> Spring Security OAuth2LoginAuthenticationFilter handles authorization code, saved request state, PKCE and ID token; deliberately NOT a custom REST controller method.
- GET /api/v1/iam/session -> authenticated OIDC session metadata, no tokens.
- POST /api/v1/iam/session/refresh -> OAuth2AuthorizedClientManager, refresh token server-side, no tokens returned.
- POST /api/v1/iam/logout -> invalidates local HttpSession and clears security context.
- POST /api/v1/iam/logout/global -> OidcClientInitiatedLogoutSuccessHandler, then local session invalidation.
- GET /api/v1/iam/oidc/config -> public metadata.

Security chain: separate stateful OIDC browser routes from stateless JWT resource-server routes; CSRF enforced on POST endpoints. OAuth2 client registration uses authorization_code, public client, issuer discovery and secure session cookie (secure=true default).

## P0/P1 gaps before acceptance
1. **NO real integration test or compilation evidence**. Run app-common Maven test and Keycloak/Browser E2E. Spring Security API integration and CSRF/logout behavior must be validated.
2. **BFF-to-Collectra API forwarding is NOT implemented**. Current React still sends legacy bearer token and stores legacy refresh token in sessionStorage. Do not enable the new flow for React until authenticated proxy/API delegation is designed and tested.
3. Default HttpSession and OAuth2AuthorizedClientRepository are not production durable across instances. Configure Spring Session with Redis or JDBC, session fixation/rotation and restart recovery.
4. Validate authorization-code replay, mismatched state, PKCE downgrade, nonce, expired sessions, CSRF, cookie flags, login CSRF and cross-origin requests.
5. Validate real Keycloak client import, correct redirect URI for App Common host (not React host). The current realm collectra-web redirect URI is http://localhost:5173/auth/callback; BFF expects /api/v1/iam/callback on App Common host. Align the client configuration and CORS/host strategy before E2E.
6. Verify global logout redirects and revocation semantics, and that local logout does not imply global SSO logout.
7. Existing Collectra domain JWT gate must reject legacy authority for Keycloak tokens; complete JWT/API tests.
8. IAM-R3.1 real SMTP/REG-01..07 remains open.

Status: code slice implemented, verification NOT RUN, IAM-R3.2 IN PROGRESS. No main merge.
