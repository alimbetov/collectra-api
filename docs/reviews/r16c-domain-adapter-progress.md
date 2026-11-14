# R1.6C — Domain Authorization Adapter progress
Status: CODE ADDED / TESTS NOT EXECUTED / LIVE HTTP E2E NOT RUN / S2 HOLD

## Added to existing Collectra API
- KeycloakDomainAuthorizationFilter placed after bearer JWT authentication and before existing AuthorizationVersionFilter.
- For validated Keycloak issuer+subject: verified identity link -> active local user -> tenant selected with X-Collectra-Tenant-ID -> active local tenant+membership -> DB-derived role and permission authorities.
- Platform access requires no tenant selector, platform user and DB-confirmed super-admin; Keycloak role claims are ignored.
- Derived JwtAuthenticationToken preserves original signed Keycloak issuer/subject/token but overlays locally validated tenant_id, membership_id, authorization_version, token_type; authentication name is local UUID. Existing authorization-version filter validates local principal and membership. TenantContextFilter reads derived verified tenant_id.
- New PostgreSQL versioned revocation snapshots; gate denies missing, BLOCKED, stale snapshots and DB errors (DB errors deny via outer filter). Freshness defaults 30s.
- Feature flag COLLECTRA_KEYCLOAK_ENABLED remains false; no legacy behavior change intended. Added basic unit tests (not executed).

## Critical remaining gaps
- No authenticated, durable revocation snapshot consumer/replication worker. Therefore new Keycloak mode is fail-closed and cannot successfully authorize a user until verified ACTIVE snapshots are provisioned. Do NOT manually seed ACTIVE snapshots in production.
- Local snapshot freshness must be refreshed via authenticated App Common reconciliation; BLOCKED must be monotonic, and ACK must be emitted only after committed local update. No such pipeline exists yet.
- This is NOT an E2E success. Real Keycloak, real Collectra HTTP and App-B API not run; no CI evidence.
- Security review required for role resolution, tenant selection header, platform account binding, and revocation source trust.
- Existing legacy JWTs are NOT covered by global Keycloak revocation; cutover plan and legacy session invalidation required.
- Tests must prove wrong audience, forged tenant selection, inactive tenant/membership, stale/missing/BLOCKED snapshots, duplicate/out-of-order versions, Keycloak offline and unchanged local UUIDs.
- Independent S2 review and PostgreSQL Testcontainers acceptance are mandatory.

Branch feat/iam-shared-platform; no merge to main.
