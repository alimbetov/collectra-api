# RBAC coverage matrix

Статус отражает фактически работающий код в ветке `feature/identity-rbac`, а не наличие имени роли в миграции.

| Actor / role | Model | Authentication | Authorization | REST management | Automated evidence | Status |
|---|---|---|---|---|---|---|
| `PLATFORM_SUPER_ADMIN` | Platform role seeded | No bootstrap/login flow | Platform boundary enforced | `/platform/me` entry point | Controller security contract | Foundation |
| `TENANT_ADMIN` | UserAccount + TenantMembership | Password, access/refresh JWT | All 13 Identity/Integration/Audit permissions | Users, roles, service clients, audit | JWT claims, cross-tenant, custom-role smoke tests | Implemented |
| `TENANT_USER` | UserAccount + TenantMembership | Invitation, activation, password login and refresh JWT | `USER_READ`, `ROLE_READ` | Profile, password recovery and session management | Lifecycle integration tests | Implemented |
| Custom tenant role | Tenant-owned Role | Human JWT after login/refresh | Selected permission set | Create and assign supported; update absent | Assignment and authorization-version smoke test | Partial |
| `SERVICE_CLIENT` | Separate non-human entity | clientId + tenant-owned secret → service JWT | Scopes | Create/list/block and zero-downtime rotation | Secret non-disclosure and human-endpoint denial smoke test | Implemented |

## Security invariants

- `TENANT_USER` is always a human account with a membership.
- A technical integration is always a `ServiceClient`; it has no password login, membership, UI session or refresh token.
- Tenant endpoints authorize atomic permissions with `@PreAuthorize`.
- Identity, audit and service-client administration require a human JWT before permissions are evaluated.
- `/platform/**` requires `ROLE_PLATFORM_SUPER_ADMIN` at HTTP and method-security boundaries.
- Future business `/integration/**` endpoints require a service JWT; service-token issuance remains explicitly public.
- Integration endpoints authorize `SCOPE_...` authorities.
- Network restrictions belong to API Gateway/WAF; ServiceClient has no IP allowlist.
- Tenant identity comes only from the verified JWT.
- Role or scope changes increment `authorization_version` and invalidate older access tokens.
- Secrets are accepted only on create/rotation and are stored only as adaptive hashes.

## Remaining work

1. Platform super-admin bootstrap, platform permissions and time-bound support grants.
2. Email delivery adapter for invitation and password-reset notifications.
3. Role update/delete API with protection of system roles and the last tenant admin.
4. Audited scope updates and credential revocation API.
5. Scope-protected business endpoints for notifications and import batches.
6. Redis-backed distributed rate limiting for production.
