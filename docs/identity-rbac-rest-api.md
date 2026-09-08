# Identity + RBAC REST API

Контроллеры проверяют атомарные permissions/scopes. Имя роли не используется в бизнес-методах.

| Method | Endpoint | Subject | Required authority |
|---|---|---|---|
| POST | `/api/v1/auth/tenants/register` | Public | Rate limit |
| POST | `/api/v1/auth/login` | Public | Rate limit |
| POST | `/api/v1/auth/refresh` | Refresh session | Token rotation |
| POST | `/api/v1/auth/logout` | Refresh session | Token possession |
| POST | `/api/v1/auth/logout-all` | User | Authenticated |
| POST | `/api/v1/auth/otp/challenges` | User | Authenticated + policy |
| POST | `/api/v1/auth/otp/challenges/{id}/verify` | Challenge holder | Public + rate limit |
| GET | `/api/v1/identity/users` | User | `USER_READ` |
| GET | `/api/v1/identity/roles` | User | `ROLE_READ` |
| POST | `/api/v1/identity/roles` | User | `ROLE_CREATE` |
| PUT | `/api/v1/identity/memberships/{id}/roles` | User | `ROLE_ASSIGN` |
| PATCH | `/api/v1/identity/memberships/{id}/status` | User | `USER_BLOCK` |
| POST | `/api/v1/integration/service-clients` | User | `SERVICE_CLIENT_CREATE`; tenant задаёт secret |
| POST | `/api/v1/integration/service-clients/{id}/rotate-secret` | User | `SERVICE_CLIENT_ROTATE_SECRET`; tenant задаёт новый secret |
| POST | `/api/v1/integration/service-token` | Service client | Client credentials + IP/rate policy |
| GET | `/api/v1/audit/security-events` | User | `AUDIT_READ` |

Integration API operations use authorities in the form `SCOPE_integration:imports:read`. Human JWT
cannot call service-only operations and service JWT cannot call human permission endpoints.

`clientSecret` принимается только при создании или ротации, должен занимать 32–72 UTF-8 байта и
никогда не возвращается в response. В базе хранится только адаптивный BCrypt-хеш. Ответственность за
создание, защищённое хранение и передачу исходного secret несёт tenant.
