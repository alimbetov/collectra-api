# Identity + RBAC REST API

Контроллеры проверяют атомарные permissions/scopes. Имя роли не используется в бизнес-методах.

## Platform super admin

При старте backend гарантирует наличие платформенной учётки `super-admin` с паролем
`Alimbetov_Ruslan`, если bootstrap включён в конфигурации `collectra.security.platform-bootstrap`.
Это не tenant-пользователь: у него нет tenant slug, membership и tenant permissions.

На frontend нужно выбирать режим входа `Платформа`. Режим `Tenant` ожидает slug рабочего
пространства и email и вызывает `/api/v1/auth/login/by-slug`, поэтому `super-admin` в этом режиме
входить не должен. Platform-режим вызывает `/api/v1/platform/auth/login`, сохраняет отдельный тип
сессии и открывает `/platform`.

| Method | Endpoint | Subject | Required authority |
|---|---|---|---|
| POST | `/api/v1/auth/tenants/register` | Public | Rate limit |
| POST | `/api/v1/auth/login` | Public | Rate limit |
| POST | `/api/v1/auth/refresh` | Refresh session | Token rotation |
| POST | `/api/v1/auth/logout` | Refresh session | Token possession |
| POST | `/api/v1/auth/logout-all` | User | Authenticated |
| POST | `/api/v1/platform/auth/login` | Public | Platform super admin credentials + rate limit |
| POST | `/api/v1/platform/auth/refresh` | Platform refresh session | Token rotation |
| POST | `/api/v1/platform/auth/logout` | Platform refresh session | Token possession |
| POST | `/api/v1/platform/auth/logout-all` | Platform user | Authenticated |
| GET | `/api/v1/platform/me` | Platform user | `ROLE_PLATFORM_SUPER_ADMIN` |
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
