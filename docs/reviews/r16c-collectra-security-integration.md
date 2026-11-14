# R1.6C — Collectra Spring Security integration audit and safe first slice
Status: IMPLEMENTED FOUNDATION / KEYCLOAK MODE NOT ACCEPTED / E2E RED / S2 HOLD

## Verified code reality
- Collectra currently issues HS256 JWTs itself (issuer collectra-api, local UUID sub), validates via shared secret, and enforces authorization_version plus local tenant membership.
- Keycloak shared realm uses different global (issuer,sub). Treating sub as local UUID would break identity and authorization.
- Existing authorization filter requires legacy token_type, tenant_id, membership_id and local user UUID.

## Changes in this slice
- Liquibase 054 creates verified (issuer,subject)->local UUID mapping, no change to user_accounts IDs, organization IDs, membership FKs or roles.
- VerifiedIdentityLinkRepository only selects VERIFIED mappings. No email fallback or JIT membership.
- KeycloakIdentityGate denies wrong issuer, missing link or non-ACTIVE local user.
- Feature-gated Keycloak decoder verifies JWKS signature, issuer, exp and collectra-api audience plus verified identity link. Disabled by default.
- Legacy JWT decoder remains default. External Keycloak roles, permissions and token_type are not converted into Collectra authorities.
- Unit tests added for missing link, wrong issuer, external role injection; NOT executed.

## Explicit limitations
- Turning on COLLECTRA_KEYCLOAK_ENABLED=true is NOT an accepted migration. Current AuthorizationVersionFilter rejects Keycloak tokens because it still expects legacy token_type, local subject, tenant/membership claims. This is intentional fail-closed until a separate domain principal and tenant-selection authorization adapter is implemented.
- Keycloak decoder currently invokes a local identity-link database read on token validation; no distributed revocation cache or global subject BLOCKED lookup is wired into Collectra.
- No actual second App-B resource server; no Keycloak E2E; no loss-of-event/Keycloak-outage proof; no S2 approval.
- Identity link creation must require proof of existing account ownership + Keycloak authentication; this slice intentionally provides read-only mapping, not unsafe self-linking.
- A Keycloak-mode switch is not dual-token support. Enabling it disables legacy JWT validation; rollout needs planned BFF/session migration and rollback.

## Next mandatory RED tests
1. Real PostgreSQL migration 054 retains all existing UUIDs, organizations, memberships and FKs; email collisions never create links.
2. Real Keycloak signed user token: wrong issuer/audience/unverified link denied.
3. New domain adapter: linked global user resolves local user and membership by authenticated tenant selection; no tenant inferred from Keycloak role claims.
4. Same issued Keycloak JWT accepted by Collectra before block, rejected after global block, independent of Keycloak availability while snapshot fresh.
5. Duplicate/reordered/lost events, stale cache and partitions; enforce bounded freshness and fail closed.
6. Actual App-B uses same global subject with its own audience; cross-audience JWT rejected.
7. S2 review, reproducible CI logs, measured propagation; do not merge or deploy until proven.

Branch: feat/iam-shared-platform; main unchanged.
