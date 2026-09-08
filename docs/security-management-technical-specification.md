# Collectra Security Management — техническое задание

Статус: `DRAFT FOR REVIEW`  
Этап 1: проектирование без изменения runtime-кода  
База: `main` после PR #6  
Стек: Java 17, Spring Boot 3.5, Spring Security, PostgreSQL, Liquibase, Redis

## 1. Цель

Завершить базовый контур Security/RBAC перед разработкой бизнес-модулей Collectra:

1. запустить первого `PLATFORM_SUPER_ADMIN`;
2. реализовать platform login и JWT без tenant context;
3. управлять platform administrators;
4. обновлять и удалять custom tenant roles;
5. предоставить каталог permissions;
6. дать tenant administrator управление пользовательскими сессиями;
7. отзывать сессии при блокировке membership;
8. заменить локальный rate limit на Redis;
9. закрыть аудит изменений доступа;
10. подготовить scope-защиту реальных integration API.

Не входят: Keycloak, SSO, MFA, support-доступ к tenant, IP allowlist, API Gateway/WAF, UI и
бизнес-реализация импорта/уведомлений.

## 2. Простая целевая модель

| Actor | Хранение | JWT `token_type` | Контекст |
|---|---|---|---|
| Tenant user | `user_accounts` + `tenant_memberships` | `tenant_user` | tenant + membership |
| Platform administrator | `user_accounts` + `platform_user_roles` | `platform_user` | без tenant |
| Service client | `service_clients` | `service` | tenant + scopes |

Правила:

- tenant access существует только через активный `TenantMembership`;
- platform access существует только через `platform_user_roles`;
- platform JWT не содержит `tenant_id`, `membership_id` и tenant permissions;
- service client не является пользователем и не получает refresh token;
- контроллеры проверяют permissions/scopes, а не названия custom roles;
- tenant ID берётся только из проверенного JWT;
- изменение прав увеличивает `authorization_version` и инвалидирует старый access JWT;
- passwords, refresh tokens и client secrets хранятся только как hash;
- cross-tenant ID возвращает `404`, не раскрывая наличие чужого объекта.

В этой итерации `UserAccount` относится либо к tenant, либо к platform. Одинаковый email может
существовать в обоих контекстах как две независимые учётные записи. Объединение identity пока не нужно.

## 3. Изменения базы данных

Существующие changesets `001`–`005` не изменять. Добавить:

- `006-platform-identity.sql`;
- `007-security-management.sql`;
- `008-service-scope-catalog.sql`.

### 3.1 Platform account и sessions

```sql
ALTER TABLE user_accounts ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE refresh_sessions ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE refresh_sessions ALTER COLUMN membership_id DROP NOT NULL;

ALTER TABLE refresh_sessions
    ADD COLUMN context_type VARCHAR(20) NOT NULL DEFAULT 'TENANT';

ALTER TABLE refresh_sessions
    ADD CONSTRAINT ck_refresh_context CHECK (
        (context_type = 'TENANT' AND tenant_id IS NOT NULL AND membership_id IS NOT NULL)
        OR
        (context_type = 'PLATFORM' AND tenant_id IS NULL AND membership_id IS NULL)
    );

CREATE UNIQUE INDEX uk_platform_user_email
    ON user_accounts (lower(email)) WHERE tenant_id IS NULL;

CREATE INDEX idx_refresh_membership_active
    ON refresh_sessions (membership_id, created_at DESC)
    WHERE revoked_at IS NULL;
```

Существующее поле `user_accounts.role` не использовать в новой авторизации. Удалять его сейчас не
нужно: это отдельная cleanup-миграция после проверки legacy-кода.

### 3.2 Каталог service scopes

```sql
CREATE TABLE service_scopes (
    code VARCHAR(120) PRIMARY KEY,
    module VARCHAR(60) NOT NULL,
    description VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
```

Начальные scopes:

- `integration:imports:read`;
- `integration:imports:write`;
- `integration:notifications:write`;
- `integration:notifications:status:read`.

В `permissions` добавить `SERVICE_CLIENT_UPDATE` и назначить его `TENANT_ADMIN`.

Новые таблицы для administrators, custom roles и audit не нужны: используются существующие
`platform_user_roles`, `roles`, `role_permissions`, `membership_roles`, `security_audit_events`.

## 4. Bootstrap первого PLATFORM_SUPER_ADMIN

### Конфигурация

```yaml
collectra.security.platform-bootstrap:
  enabled: ${COLLECTRA_PLATFORM_BOOTSTRAP_ENABLED:false}
  email: ${COLLECTRA_PLATFORM_BOOTSTRAP_EMAIL:}
  password: ${COLLECTRA_PLATFORM_BOOTSTRAP_PASSWORD:}
```

В production bootstrap по умолчанию выключен. Значения передаются через Kubernetes Secret/secret
manager и не логируются.

### Реализация

Добавить `PlatformAdminBootstrap implements ApplicationRunner` и
`PlatformAdministratorService.bootstrap(email, password)`.

Алгоритм одной транзакции:

1. Если `enabled=false`, ничего не делать.
2. Проверить email и пароль по общей password policy.
3. Если активный platform administrator уже существует, ничего не создавать.
4. Создать `UserAccount` с `tenantId=null`, BCrypt hash и статусом `ACTIVE`.
5. Назначить существующую роль `PLATFORM_SUPER_ADMIN` через `platform_user_roles`.
6. Записать audit `PLATFORM_ADMIN_BOOTSTRAPPED`.

Параллельный запуск защищает уникальный индекс email. При конфликте повторно проверить наличие
administrator и завершиться без ошибки. Advisory lock для MVP не нужен.

После первого запуска оператор выключает bootstrap и удаляет password из окружения. Повторный запуск
не изменяет существующего пользователя или пароль.

## 5. Platform authentication

| Method | Endpoint | Доступ | Результат |
|---|---|---|---|
| POST | `/api/v1/platform/auth/login` | Public + rate limit | access + refresh token |
| POST | `/api/v1/platform/auth/refresh` | Platform refresh token | новая token pair |
| POST | `/api/v1/platform/auth/logout` | Platform refresh token | `204` |
| POST | `/api/v1/platform/auth/logout-all` | `ROLE_PLATFORM_SUPER_ADMIN` | `204` |
| GET | `/api/v1/platform/me` | `ROLE_PLATFORM_SUPER_ADMIN` | profile |

Login request принимает только `email` и `password`. Tenant ID отсутствует.

### JWT contract

```json
{
  "iss": "collectra-api",
  "aud": ["collectra-api"],
  "sub": "user-uuid",
  "token_type": "platform_user",
  "roles": ["PLATFORM_SUPER_ADMIN"],
  "authorization_version": 3,
  "iat": 0,
  "exp": 0
}
```

Изменения кода:

- `JwtService.issuePlatform(user, roles)`;
- `PlatformAuthService` и `PlatformAuthController`;
- `SecurityConfig.extractAuthorities()` поддерживает `platform_user` как `ROLE_HUMAN` + platform roles;
- `AuthorizationVersionFilter` проверяет active user, platform role и version.

Tenant refresh принимает только session `context_type=TENANT`, platform refresh — только `PLATFORM`.
Rotation, reuse detection и family revocation переиспользуют текущую логику.

## 6. Управление platform administrators

| Method | Endpoint | Назначение |
|---|---|---|
| GET | `/api/v1/platform/administrators` | список administrators |
| POST | `/api/v1/platform/administrators` | создать administrator |
| PATCH | `/api/v1/platform/administrators/{id}/status` | block/unblock |
| PUT | `/api/v1/platform/administrators/{id}/password` | установить пароль |
| DELETE | `/api/v1/platform/administrators/{id}/role` | снять platform role |

Все операции требуют `ROLE_PLATFORM_SUPER_ADMIN`.

Правила:

- platform email уникален без учёта регистра;
- нельзя заблокировать или лишить роли последнего активного administrator;
- self-block и self-role-removal возвращают `409 LAST_ADMIN_PROTECTED`;
- block, password update и role removal отзывают platform refresh sessions пользователя;
- block и role removal увеличивают `authorization_version`;
- password не возвращается в response и audit;
- создание сразу принимает password; temporary password для MVP не вводится.

Сервис: `PlatformAdministratorService`. Для `platform_user_roles` достаточно `JdbcTemplate`; отдельная
JPA entity пока не нужна.

## 7. Custom tenant roles

| Method | Endpoint | Authority |
|---|---|---|
| PUT | `/api/v1/identity/roles/{id}` | `ROLE_UPDATE` |
| DELETE | `/api/v1/identity/roles/{id}` | `ROLE_UPDATE` |

Update request:

```json
{
  "code": "COLLECTOR",
  "permissions": ["USER_READ", "AUDIT_READ"]
}
```

`RbacService.updateRole/deleteRole`:

- ищет роль только по `id + tenantId`;
- запрещает изменение system role и роли другого tenant;
- нормализует code в uppercase и проверяет уникальность внутри tenant;
- проверяет существование каждого permission;
- заменяет permissions одной транзакцией;
- увеличивает `authorization_version` затронутых пользователей;
- возвращает `409 ROLE_IN_USE` для назначенной роли;
- возвращает `409` при optimistic-lock conflict.

Автоматически снимать назначенную роль при delete запрещено.

## 8. Каталог permissions

`GET /api/v1/identity/permissions` требует `ROLE_HUMAN + ROLE_READ` и возвращает `code`, `module`,
`description`, отсортированные по `module`, затем `code`.

Pagination не нужна до 500 записей. Tenant не изменяет permissions через REST; каталог меняется только
Liquibase migration. Класс: `TenantPermissionController`, источник: `PermissionRepository`.

## 9. Административное управление sessions

| Method | Endpoint | Authority |
|---|---|---|
| GET | `/api/v1/identity/memberships/{id}/sessions` | `USER_READ` |
| DELETE | `/api/v1/identity/memberships/{id}/sessions/{sessionId}` | `USER_UPDATE` |
| DELETE | `/api/v1/identity/memberships/{id}/sessions` | `USER_UPDATE` |

`SessionAdministrationService` сначала находит membership по `id + tenantId`, затем session по
`sessionId + userId + membershipId`.

Response: `id`, `createdAt`, `expiresAt`, `lastUsedAt`, `revokedAt`, `sourceIp`, `userAgent`. Refresh
token/hash не возвращается. Повторный revoke идемпотентен.

Отзыв refresh session не инвалидирует выданный access JWT. Для немедленного прекращения всего доступа
administrator блокирует membership.

## 10. Блокировка membership

`RbacService.changeMembershipStatus(..., active=false)` выполняет одной транзакцией:

1. проверка tenant ownership и последнего `TENANT_ADMIN`;
2. status membership → `BLOCKED`;
3. revoke всех refresh sessions membership;
4. увеличение `UserAccount.authorization_version`;
5. audit `MEMBERSHIP_BLOCKED`.

После commit старый access JWT получает `401`. Unblock не восстанавливает sessions — пользователь
выполняет новый login.

## 11. Redis rate limiting

Добавить `spring-boot-starter-data-redis`, Redis в `compose.yaml` и настройки:

```yaml
spring.data.redis:
  host: ${REDIS_HOST:localhost}
  port: ${REDIS_PORT:6379}
  password: ${REDIS_PASSWORD:}

collectra.security.rate-limit:
  key-prefix: collectra:security:rate-limit
  fail-closed: true
```

Интерфейс:

```java
public interface RateLimiter {
    void check(String policy, String identity, int limit, Duration window);
}
```

`RedisRateLimiter` использует атомарный Lua script: `INCR`, при первом запросе `EXPIRE`, возврат TTL.
Identity преобразуется HMAC-SHA256 с `rate-limit-pepper`, чтобы email/clientId не были видны в keys.

| Operation | Limit |
|---|---:|
| tenant login | 5 / 15 min |
| platform login | 5 / 15 min |
| password forgot | 3 / 30 min |
| OTP verify | 5 / 10 min |
| service token | 10 / min |

Превышение: `429 RATE_LIMIT_EXCEEDED` + `Retry-After`. Production работает fail-closed для
login/token/OTP. `InMemoryRateLimiter` остаётся только для unit/local profile.

Fixed window достаточно. Sliding window, Bucket4j и отдельный rate-limit service не нужны.

## 12. Security audit

Использовать существующие `SecurityAuditService` и `security_audit_events`.

Новые actions:

- `PLATFORM_LOGIN`, `PLATFORM_ADMIN_CREATED`, `PLATFORM_ADMIN_STATUS_CHANGED`;
- `PLATFORM_ADMIN_PASSWORD_CHANGED`, `PLATFORM_ADMIN_ROLE_REMOVED`;
- `TENANT_ROLE_CREATED`, `TENANT_ROLE_UPDATED`, `TENANT_ROLE_DELETED`;
- `MEMBERSHIP_ROLES_ASSIGNED`, `MEMBERSHIP_SESSION_REVOKED`, `MEMBERSHIP_SESSIONS_REVOKED`;
- `MEMBERSHIP_BLOCKED`, `MEMBERSHIP_UNBLOCKED`;
- `SERVICE_CLIENT_SCOPES_UPDATED`, `SERVICE_TOKEN_ISSUED`.

Успешный audit сохраняется в той же транзакции, что и изменение: при rollback event тоже откатывается.
Отдельный `afterCommit` механизм для MVP не нужен.

Denied login/token events записываются методом с `REQUIRES_NEW`, поскольку основная операция завершается
исключением. Audit содержит actor/target IDs, action, result, reason, trace/correlation IDs, IP и
безопасную metadata. Password, token, secret и hashes запрещены.

## 13. Service-client scopes

Добавить:

`PUT /api/v1/integration/service-clients/{id}/scopes`

Доступ: `ROLE_HUMAN + SERVICE_CLIENT_UPDATE`.

Операция проверяет scopes по `service_scopes`, заменяет набор, увеличивает
`service_clients.authorization_version` и пишет before/after audit. Старые service JWT получают `401`.

Не создавать временные capability/test endpoints в production. Scope contract закрепить на первом
реальном endpoint модуля `importing` или `communication`:

```java
@PreAuthorize("hasAuthority('ROLE_SERVICE')")
class IntegrationImportController {

    @PreAuthorize("hasAuthority('SCOPE_integration:imports:write')")
    // real business operation
}
```

До появления реального API обязательны unit-тест converter и smoke-тест запрета service JWT на
human/platform API. Неизвестный или не назначенный scope возвращает `400 INVALID_SCOPE`.

## 14. Error contract

| Code | HTTP |
|---|---:|
| `INVALID_CREDENTIALS` | 401 |
| `TOKEN_INVALIDATED` | 401 |
| `ACCESS_DENIED` | 403 |
| `RESOURCE_NOT_FOUND` | 404 |
| `LAST_ADMIN_PROTECTED` | 409 |
| `ROLE_IN_USE` | 409 |
| `DUPLICATE_EMAIL` | 409 |
| `DUPLICATE_ROLE_CODE` | 409 |
| `INVALID_PERMISSION` | 400 |
| `INVALID_SCOPE` | 400 |
| `RATE_LIMIT_EXCEEDED` | 429 |

`ApiExceptionHandler` не возвращает stack trace, secrets или сведения о чужом tenant.

## 15. Тесты

### Unit tests

- `JwtServiceUnitTest`: platform JWT без tenant claims;
- `AuthorizationVersionFilterUnitTest`: tenant/platform/service, stale version, malformed claims;
- `PlatformAdminBootstrapUnitTest`: disabled, create, repeat run;
- `PlatformAdministratorServiceUnitTest`: last admin и session revoke;
- `RbacServiceUnitTest`: update/delete/system role/role in use/cross-tenant;
- `RedisRateLimiterUnitTest`: allow, deny, TTL, Redis error;
- `SecurityAuditServiceUnitTest`: отсутствие secrets и rollback success event.

### Integration/smoke tests

- bootstrap создаёт одного platform administrator;
- platform login, refresh rotation/reuse, logout;
- platform JWT доступен только к `/platform/**`;
- tenant/service JWT запрещены на `/platform/**`;
- управление platform administrators и защита последнего;
- update/delete custom role и invalidation старого JWT;
- permission catalog по ролям;
- tenant-isolated session list/revoke;
- block membership отзывает refresh и access;
- общий Redis rate limit для двух application instances;
- audit before/after без secrets;
- после появления integration API: свой scope разрешён, чужой scope и human JWT запрещены.

Тесты используют реально выданные JWT. Искусственные authorities допустимы только в unit-тестах
converter/filter. Общая проверка: `mvn clean verify`.

## 16. Порядок реализации второго этапа

1. PR-A: migration `006`, bootstrap, platform JWT/auth/filter и тесты.
2. PR-B: управление platform administrators.
3. PR-C: custom roles, permission catalog и sessions.
4. PR-D: membership session revoke и audit.
5. PR-E: Redis rate limiter.
6. PR-F: service scope catalog/update; endpoint tests — с первым реальным integration API.

Каждый PR обновляет `rbac-coverage-matrix.md`, `identity-rbac-rest-api.md` и проходит CI.

## 17. Definition of Done

- bootstrap повторяемо создаёт первого platform administrator;
- platform authentication работает без tenant/membership claims;
- нельзя удалить/заблокировать последнего активного platform или tenant administrator;
- custom/system/cross-tenant roles обрабатываются по правилам;
- блокировка membership немедленно прекращает access и refresh;
- rate limit одинаков для всех replicas;
- изменения доступа имеют audit без secrets;
- service JWT содержит только разрешённые scopes;
- первый реальный integration endpoint проверяет `ROLE_SERVICE` и конкретный `SCOPE_*`;
- `mvn clean verify` проходит в CI.

## 18. Зафиксированные решения

1. Platform и tenant accounts пока независимы, даже при одинаковом email.
2. MFA реализуется отдельным следующим security milestone.
3. Platform password задаётся при create/update до появления notification worker.
4. Временные capability endpoints не создаются; scopes подключаются к реальным API.
5. Redis fail-closed применяется к login/token/OTP, но не к регистрации tenant.

