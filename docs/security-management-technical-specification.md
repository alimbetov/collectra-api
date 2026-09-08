# Collectra Security Management — техническое задание

Статус: `DRAFT FOR REVIEW`  
Этап: 1 — проектирование, без изменения runtime-кода  
База проектирования: `main` после PR #6  
Целевой стек: Java 17, Spring Boot 3.5, Spring Security, PostgreSQL, Liquibase, Redis

## 1. Цель и границы

Цель доработки — завершить platform authentication и административное управление Identity/RBAC,
устранить локальные механизмы, которые нельзя безопасно масштабировать, и создать проверяемый
контракт service-to-service авторизации.

В реализацию входят:

1. bootstrap первого `PLATFORM_SUPER_ADMIN`;
2. platform login/access/refresh/logout flow без tenant membership;
3. управление platform administrators;
4. update/delete custom tenant roles;
5. каталог permissions;
6. административный просмотр и отзыв пользовательских сессий;
7. немедленный отзыв refresh-сессий при блокировке membership;
8. Redis rate limiting;
9. полный security audit изменений ролей, permissions и service-client scopes;
10. scope-защищённые integration endpoints для проверки service-client модели.

Не входят: SSO/OIDC, Keycloak, MFA platform administrator, временный support-доступ к tenant,
IP allowlist, бизнес-реализация импорта и уведомлений, UI и API Gateway/WAF policies.

## 2. Обязательные архитектурные инварианты

- `UserAccount` остаётся единственным типом human identity.
- Tenant-доступ существует только через активный `TenantMembership`.
- Platform-доступ существует только через `platform_user_roles`; tenant membership для него не нужен.
- В этой итерации `UserAccount` имеет ровно один identity context: tenant account содержит
  `tenant_id`, platform-only account имеет `tenant_id=null`. Один email может существовать в обоих
  контекстах как две независимые учётные записи.
- Platform JWT не содержит `tenant_id` и `membership_id`.
- Tenant JWT не получает platform authorities даже при наличии platform role у того же пользователя.
- Service client не является `UserAccount`, не имеет password/refresh session и получает только scopes.
- Контроллеры проверяют atomic permissions/scopes. Имена tenant-ролей не используются в бизнес-методах.
- `ROLE_PLATFORM_SUPER_ADMIN`, `ROLE_HUMAN` и `ROLE_SERVICE` задают actor boundary, но не заменяют
  atomic permissions для tenant/service операций.
- Tenant identifier берётся только из проверенного JWT и `TenantContext`; request body/query/path не
  может переопределить tenant.
- Изменение доступа увеличивает `authorization_version`, поэтому старый access token становится
  недействительным немедленно.
- Все секреты и refresh tokens хранятся только в виде hash.
- Security audit не должен фиксировать `SUCCEEDED`, если бизнес-транзакция откатилась.

## 3. Целевая структура пакетов

```text
io.collectra.api
├── identity
│   ├── api
│   │   ├── PlatformAuthController
│   │   ├── PlatformAdministratorController
│   │   ├── TenantPermissionController
│   │   ├── TenantRoleController
│   │   └── TenantMembershipController
│   ├── application
│   │   ├── PlatformAuthService
│   │   ├── PlatformAdministratorService
│   │   ├── PermissionCatalogService
│   │   ├── RbacService
│   │   └── SessionAdministrationService
│   └── infrastructure
│       ├── PlatformUserRoleRepository
│       └── RedisRateLimitRepository
├── integration
│   ├── api
│   │   ├── ServiceClientController
│   │   └── IntegrationAccessController
│   └── application
│       └── ServiceClientService
└── shared/security
    ├── AuthorizationVersionFilter
    ├── RedisRateLimiter
    ├── SecurityConfig
    └── SecurityActor
```

Новые классы создаются только когда у них есть отдельная ответственность. DTO допускается оставлять
внутренними records контроллера, пока они не переиспользуются.

## 4. Модель данных и Liquibase

Изменения оформить последовательными changesets, без редактирования `001`–`005`:

- `006-platform-identity.sql`;
- `007-role-and-session-administration.sql`;
- `008-security-audit.sql`;
- `009-service-scope-catalog.sql`.

### 4.1 Platform identity

Текущая `user_accounts.tenant_id NOT NULL` не позволяет platform-only account. В `006`:

```sql
ALTER TABLE user_accounts ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE user_accounts ALTER COLUMN role DROP NOT NULL;
ALTER TABLE refresh_sessions ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE refresh_sessions ALTER COLUMN membership_id DROP NOT NULL;

ALTER TABLE refresh_sessions
    ADD COLUMN actor_context VARCHAR(20) NOT NULL DEFAULT 'TENANT';

ALTER TABLE refresh_sessions
    ADD CONSTRAINT ck_refresh_actor_context CHECK (
        (actor_context = 'TENANT' AND tenant_id IS NOT NULL AND membership_id IS NOT NULL)
        OR
        (actor_context = 'PLATFORM' AND tenant_id IS NULL AND membership_id IS NULL)
    );

CREATE UNIQUE INDEX uk_platform_user_email
    ON user_accounts (lower(email)) WHERE tenant_id IS NULL;
```

Legacy `user_accounts.role` не использовать для новой авторизации. Его удаление выполняется отдельной
expand/contract миграцией после подтверждения отсутствия чтений.

Для platform role используется существующая таблица `platform_user_roles`. Нельзя назначать туда
роль с `scope_type != 'PLATFORM'`.

### 4.2 Role deletion and session lookup

В `007` добавить индексы:

```sql
CREATE INDEX idx_refresh_membership_active
    ON refresh_sessions (membership_id, created_at DESC)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_platform_user_role_user
    ON platform_user_roles (user_id);
```

Удаление custom role опирается на существующий FK `membership_roles.role_id`. API заранее проверяет
назначения и возвращает `409`; каскадное снятие роли при delete запрещено.

### 4.3 Audit metadata

В `008` существующая таблица `security_audit_events` сохраняется. В `metadata` записываются только
идентификаторы, codes и before/after-наборы permissions/scopes. Password, token, secret и их hashes
в audit запрещены.

При необходимости добавить индексы:

```sql
CREATE INDEX idx_security_audit_actor_created
    ON security_audit_events (actor_id, created_at DESC);
CREATE INDEX idx_security_audit_action_created
    ON security_audit_events (action, created_at DESC);
```

### 4.4 Service scope catalog

В `009` создать whitelist:

```sql
CREATE TABLE service_scopes (
    code VARCHAR(120) PRIMARY KEY,
    module VARCHAR(60) NOT NULL,
    description VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
```

Начальный каталог:

- `integration:imports:read`;
- `integration:imports:write`;
- `integration:notifications:write`;
- `integration:notifications:status:read`.

Создание клиента, обновление scopes и выдача service token валидируют scopes по каталогу.

## 5. Bootstrap первого PLATFORM_SUPER_ADMIN

### 5.1 Конфигурация

```yaml
collectra:
  security:
    platform-bootstrap:
      enabled: ${COLLECTRA_PLATFORM_BOOTSTRAP_ENABLED:false}
      email: ${COLLECTRA_PLATFORM_BOOTSTRAP_EMAIL:}
      password: ${COLLECTRA_PLATFORM_BOOTSTRAP_PASSWORD:}
```

Production defaults всегда `enabled=false`. Значения передаются через Kubernetes Secret/secret
manager, не сохраняются в Git, image layer, application logs или audit metadata.

### 5.2 Реализация

`PlatformAdminBootstrap` реализует `ApplicationRunner` и транзакционно вызывает
`PlatformAdministratorService.bootstrap(email, rawPassword)`.

Алгоритм:

1. Если bootstrap выключен — ничего не делать.
2. Если email/password отсутствуют или password не проходит общую password policy — остановить startup.
3. Заблокировать bootstrap через PostgreSQL advisory transaction lock, чтобы несколько replicas не
   создали разных первых администраторов.
4. Если в `platform_user_roles` уже есть активный `PLATFORM_SUPER_ADMIN` — ничего не создавать и
   вывести безопасный INFO без email/password.
5. Создать `UserAccount(tenantId=null)` с BCrypt hash и `ACTIVE`.
6. Назначить существующую системную platform role.
7. Записать `PLATFORM_ADMIN_BOOTSTRAPPED/SUCCEEDED` после успешного commit.

После первого успешного запуска оператор обязан удалить bootstrap password и установить
`enabled=false`. Повторный запуск идемпотентен и не меняет пароль существующего администратора.

## 6. Platform authentication

### 6.1 REST contract

| Method | Endpoint | Access | Result |
|---|---|---|---|
| POST | `/api/v1/platform/auth/login` | Public + rate limit | access + rotating refresh token |
| POST | `/api/v1/platform/auth/refresh` | Platform refresh token | rotated token pair |
| POST | `/api/v1/platform/auth/logout` | Platform refresh token | `204` |
| POST | `/api/v1/platform/auth/logout-all` | `ROLE_PLATFORM_SUPER_ADMIN` | `204` |
| GET | `/api/v1/platform/me` | `ROLE_PLATFORM_SUPER_ADMIN` | platform profile |

Login request: `{ "email": "...", "password": "..." }`. Tenant ID не принимается.

### 6.2 Platform JWT

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

Claims `tenant_id`, `membership_id`, `permissions` и `scope` отсутствуют.

`JwtService` получает отдельный метод `issuePlatform(...)`. Общий private encoder переиспользуется.
`SecurityConfig.extractAuthorities` добавляет `ROLE_HUMAN` и platform roles для
`token_type=platform_user`. Platform token не получает tenant permissions.

`AuthorizationVersionFilter` разделяет проверки:

- `tenant_user` — active user + active membership + совпавшая версия;
- `platform_user` — active user + запись в `platform_user_roles` + совпавшая версия;
- `service` — active client + tenant + совпавшая версия;
- любой другой `token_type` — `401`.

Refresh session с `actor_context=PLATFORM` нельзя использовать в tenant refresh endpoint и наоборот.
Reuse detection и family revocation работают одинаково для обоих human contexts.

## 7. Управление platform administrators

### 7.1 REST contract

| Method | Endpoint | Result |
|---|---|---|
| GET | `/api/v1/platform/administrators` | список platform admins |
| POST | `/api/v1/platform/administrators` | создать admin, `201` |
| PATCH | `/api/v1/platform/administrators/{id}/status` | block/unblock, `204` |
| POST | `/api/v1/platform/administrators/{id}/reset-password` | установить временный password, `204` |
| DELETE | `/api/v1/platform/administrators/{id}/role` | снять platform role, `204` |

Все операции требуют `ROLE_PLATFORM_SUPER_ADMIN`. Response никогда не содержит password hash,
refresh token или authorization internals.

Правила:

- email platform account уникален без учёта регистра;
- нельзя заблокировать или лишить роли последнего активного platform administrator;
- self-block и self-role-removal возвращают `409`;
- block/reset-password/role-removal увеличивают `authorization_version` и отзывают все platform
  refresh sessions пользователя;
- создание задаёт временный пароль; при первом login пользователь обязан сменить его. Для этого в
  `user_accounts` добавить `password_change_required BOOLEAN NOT NULL DEFAULT FALSE`;
- password reset endpoint доступен только до появления email delivery adapter. В response password
  не возвращается: его передаёт оператор в request body через защищённый канал.

## 8. Update/delete custom tenant roles

### 8.1 REST contract

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

Правила `RbacService.updateRole/deleteRole`:

- поиск только `findByIdAndTenantId(id, TenantContext.requireTenantId())`;
- system role (`system_role=true` или `tenant_id IS NULL`) менять/удалять нельзя;
- code нормализуется в uppercase и проверяется на уникальность внутри tenant;
- список permissions непустой и целиком существует в каталоге;
- обновление permissions выполняется одной транзакцией;
- после update увеличить `authorization_version` всех пользователей, которым назначена роль;
- delete назначенной роли возвращает `409 ROLE_IN_USE` и число назначений без user identifiers;
- cross-tenant ID внешне выглядит как `404`;
- optimistic-lock conflict возвращает `409`.

## 9. Каталог permissions

`GET /api/v1/identity/permissions` требует `ROLE_HUMAN + ROLE_READ` и возвращает только активный
системный каталог: `code`, `module`, `description`. Pagination не требуется до 500 записей; порядок
детерминированный: `module`, затем `code`.

Tenant не может создавать или изменять permissions. Управление каталогом через REST в этом этапе не
реализуется: изменения поставляются Liquibase migration и проходят code review.

## 10. Административное управление сессиями

### 10.1 REST contract

| Method | Endpoint | Authority |
|---|---|---|
| GET | `/api/v1/identity/memberships/{id}/sessions` | `USER_READ` |
| DELETE | `/api/v1/identity/memberships/{id}/sessions/{sessionId}` | `USER_UPDATE` |
| DELETE | `/api/v1/identity/memberships/{id}/sessions` | `USER_UPDATE` |

Сервис всегда сначала загружает membership по `(id, tenantId)`, затем session по
`(sessionId, userId, membershipId)`. Cross-tenant доступ возвращает `404`.

Session response: `id`, `createdAt`, `expiresAt`, `lastUsedAt`, `revokedAt`, masked `sourceIp`,
normalized `userAgent`. Refresh token/hash не возвращается.

Повторный revoke идемпотентен. Отзыв одной/всех sessions записывается в audit. Текущий access token
не отзывается при ручном revoke одной refresh session; для немедленного прекращения всего доступа
администратор блокирует membership.

## 11. Блокировка membership

`RbacService.changeMembershipStatus(..., active=false)` в одной транзакции:

1. проверяет tenant ownership и last-active-tenant-admin invariant;
2. меняет membership status на `BLOCKED`;
3. отзывает все активные `refresh_sessions` этого membership;
4. увеличивает `UserAccount.authorization_version`;
5. добавляет audit event `MEMBERSHIP_BLOCKED`;
6. после commit старый access token получает `401` в `AuthorizationVersionFilter`.

Unblock не восстанавливает sessions. Пользователь должен выполнить новый login. Если у пользователя
позже появятся memberships разных tenants, блокировка одного membership не должна отзывать sessions
остальных memberships.

## 12. Redis rate limiting

### 12.1 Инфраструктура

Добавить `spring-boot-starter-data-redis`, Redis в `compose.yaml`, health indicator и параметры:

```yaml
spring.data.redis:
  host: ${REDIS_HOST:localhost}
  port: ${REDIS_PORT:6379}
  password: ${REDIS_PASSWORD:}
collectra.security.rate-limit:
  key-prefix: collectra:security:rate-limit
  fail-mode: closed
```

`RedisRateLimiter` реализует существующий application-facing контракт `RateLimiter`, чтобы сервисы
не зависели от Redis API. `InMemoryRateLimiter` остаётся только для unit/local profile при явной
конфигурации; production использует Redis.

### 12.2 Алгоритм

Fixed window выполнить атомарным Lua script: `INCR`, первый `EXPIRE`, возврат remaining/reset time.
Key не содержит raw email, clientId или IP: чувствительная часть предварительно HMAC-SHA256 с
отдельным `rate-limit-pepper`.

Минимальные policies:

| Operation | Key | Limit |
|---|---|---:|
| tenant login | tenant + email + IP | 5 / 15 min |
| platform login | email + IP | 5 / 15 min |
| password forgot | tenant + email | 3 / 30 min |
| OTP verify | challenge + IP | 5 / 10 min |
| service token | clientId + IP | 10 / min |

Превышение возвращает `429`, `Retry-After` и стабильный error code `RATE_LIMIT_EXCEEDED`.
Production fail mode — closed для login/token/OTP; readiness становится unhealthy при недоступном
Redis. Метрики: allowed/denied/error по policy, без identity labels.

## 13. Security audit

Audit events для новых/изменённых операций:

| Action | Actor | Result |
|---|---|---|
| `PLATFORM_LOGIN` | USER | SUCCEEDED/DENIED |
| `PLATFORM_ADMIN_CREATED` | USER | SUCCEEDED/DENIED |
| `PLATFORM_ADMIN_STATUS_CHANGED` | USER | SUCCEEDED/DENIED |
| `PLATFORM_ADMIN_ROLE_REMOVED` | USER | SUCCEEDED/DENIED |
| `TENANT_ROLE_CREATED` | USER | SUCCEEDED/DENIED |
| `TENANT_ROLE_UPDATED` | USER | SUCCEEDED/DENIED |
| `TENANT_ROLE_DELETED` | USER | SUCCEEDED/DENIED |
| `MEMBERSHIP_ROLES_ASSIGNED` | USER | SUCCEEDED/DENIED |
| `MEMBERSHIP_SESSIONS_REVOKED` | USER | SUCCEEDED/DENIED |
| `SERVICE_CLIENT_SCOPES_UPDATED` | USER | SUCCEEDED/DENIED |
| `SERVICE_TOKEN_ISSUED` | SERVICE | SUCCEEDED/DENIED |

`SecurityAuditService` принимает structured metadata, сериализуемую `ObjectMapper`. Успешное событие
публикуется через transaction synchronization `afterCommit` либо отдельный transactional event
listener. Denied login/token события пишутся в отдельной `REQUIRES_NEW` транзакции, потому что
основная операция завершается исключением.

Каждое событие содержит: tenantId при tenant context, actor type/id, target type/id, action, result,
reason code, traceId, correlationId, source IP и безопасную metadata.

## 14. Service-client scopes и integration endpoints

### 14.1 Управление scopes

Добавить:

`PUT /api/v1/integration/service-clients/{id}/scopes`

Authority human actor: `SERVICE_CLIENT_UPDATE`. В `009` добавить permission и выдать
`TENANT_ADMIN`. Request: `{ "scopes": ["integration:imports:read"] }`.

Операция валидирует whitelist, заменяет scopes транзакционно, увеличивает client
`authorization_version`, пишет before/after audit. Все ранее выпущенные service JWT немедленно
становятся недействительными.

### 14.2 Scope-protected endpoints

До реализации бизнес-модулей создать только безопасные capability endpoints, не фиктивный CRUD:

| Method | Endpoint | Required authority |
|---|---|---|
| GET | `/api/v1/integration/access/imports/read` | `SCOPE_integration:imports:read` |
| POST | `/api/v1/integration/access/imports/write` | `SCOPE_integration:imports:write` |
| POST | `/api/v1/integration/access/notifications/write` | `SCOPE_integration:notifications:write` |
| GET | `/api/v1/integration/access/notifications/status` | `SCOPE_integration:notifications:status:read` |

Response для capability endpoint: `204 No Content`. Контроллер имеет class boundary
`@PreAuthorize("hasAuthority('ROLE_SERVICE')")`; каждый метод — отдельный scope. После появления
реальных importing/communication endpoints capability controller удаляется, а те же contract tests
переносятся на реальные API.

Human JWT всегда получает `403` независимо от совпадения строк permissions. Service token endpoint
выдаёт только запрошенное пересечение `requestedScopes ∩ clientScopes`; запрос неизвестного или не
назначенного scope возвращает `400 INVALID_SCOPE` и audit DENIED.

## 15. Error contract

Использовать единый `ApiExceptionHandler` и стабильные codes:

- `INVALID_CREDENTIALS` — `401`;
- `TOKEN_INVALIDATED` — `401`;
- `ACCESS_DENIED` — `403`;
- `RESOURCE_NOT_FOUND` — `404`;
- `LAST_ADMIN_PROTECTED` — `409`;
- `ROLE_IN_USE` — `409`;
- `DUPLICATE_EMAIL` / `DUPLICATE_ROLE_CODE` — `409`;
- `INVALID_PERMISSION` / `INVALID_SCOPE` — `400`;
- `RATE_LIMIT_EXCEEDED` — `429`.

Ответ не раскрывает наличие email, cross-tenant resource, password/hash или внутренний stack trace.

## 16. Обязательная тестовая стратегия

### 16.1 Unit tests

- `JwtServiceUnitTest`: platform claims не содержат tenant/membership; service/human claims разделены.
- `AuthorizationVersionFilterUnitTest`: все три token types, malformed claims, stale version, blocked actor.
- `PlatformAdminBootstrapUnitTest`: disabled, partial config, first create, idempotency, concurrent lock.
- `PlatformAdministratorServiceUnitTest`: last admin, self-block, session revoke, version increment.
- `RbacServiceUnitTest`: custom update/delete, system-role protection, role-in-use, cross-tenant.
- `RedisRateLimiterUnitTest`: key hashing, Lua result mapping, retry-after, Redis failure policy.
- `SecurityAuditServiceUnitTest`: secret redaction и after-commit behavior.

### 16.2 Integration/smoke tests с PostgreSQL + Redis Testcontainers

- bootstrap создаёт ровно одного platform admin при двух параллельных application contexts;
- platform login/refresh rotation/reuse/logout;
- platform JWT вызывает `/platform/**`, но получает `403` на tenant/service API;
- tenant/service JWT получают `403` на `/platform/**`;
- platform admin create/block/unblock/reset/remove-role + last-admin protection;
- tenant admin update/delete custom role + stale-token invalidation;
- permission catalog доступен `TENANT_ADMIN`/`TENANT_USER`, запрещён service actor;
- admin session list/revoke tenant-isolated;
- block membership отзывает refresh tokens и немедленно инвалидирует access token;
- rate limit общий для двух application instances;
- audit содержит actor/target/before/after и не содержит secrets;
- service JWT допускается ровно к endpoint своего scope;
- изменение scopes инвалидирует старый service JWT.

`RoleAccessSmokeIntegrationTest` расширить строками `PLATFORM_SUPER_ADMIN` и каждым service scope.
`RbacControllerContractTest` обязан проверять все mapping annotations, включая `DELETE`, и наличие
effective `@PreAuthorize` на class или method уровне.

## 17. Порядок реализации второго этапа

Рекомендуемый порядок, чтобы каждый PR был небольшим и завершённым:

1. PR-A: migrations `006`, platform bootstrap, platform JWT/filter, platform auth tests.
2. PR-B: platform administrator management и last-admin invariant.
3. PR-C: role update/delete, permission catalog, administrative session management.
4. PR-D: membership block session revocation и полный transactional audit.
5. PR-E: Redis rate limiter + compose/config/Testcontainers.
6. PR-F: service scope catalog/update/capability endpoints и финальная access matrix.

Каждый PR должен проходить `mvn clean verify`, иметь Liquibase rollback/restart проверку и обновлять
`docs/rbac-coverage-matrix.md` + `docs/identity-rbac-rest-api.md`.

## 18. Definition of Done

Доработка считается завершённой, если:

- первый platform admin создаётся безопасно и идемпотентно;
- platform authentication полностью работает без tenant/membership claims;
- невозможно удалить/заблокировать последнего активного platform или tenant administrator;
- custom/system/cross-tenant роли обрабатываются согласно инвариантам;
- блокировка membership немедленно прекращает access и refresh;
- production rate limiting не зависит от памяти отдельной replica;
- каждое изменение доступа имеет корректный committed audit event;
- service endpoint требует одновременно service actor boundary и конкретный scope;
- ни один тест не использует отключённые filters или искусственно внедрённые authorities вместо
  реально выданного JWT, кроме изолированных unit tests converter/filter;
- полный `mvn clean verify` проходит в CI.

## 19. Вопросы для review перед реализацией

На втором этапе необходимо явно утвердить:

1. Допускается ли одному email одновременно быть platform account и tenant account. Текущее ТЗ
   допускает две независимые учётные записи с разными identity contexts; объединение identity в этой
   итерации запрещено.
2. Нужен ли обязательный MFA для platform login до production launch. Рекомендация: да, отдельным
   следующим security milestone, не смешивать с текущей RBAC доработкой.
3. Оставляем ли временный password reset через platform API до появления notification worker.
4. Устраивает ли capability-controller как временная проверка scopes или первый endpoint следует
   сразу реализовать в модуле importing.
5. Требуется ли fail-closed Redis policy для tenant registration, кроме login/token/OTP.
