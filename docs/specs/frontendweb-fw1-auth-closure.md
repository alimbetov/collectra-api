# FW1 Authentication Closure — Implementation Contract

Status: **READY FOR IMPLEMENTATION**

Baseline: `main@cdc31dc10855fb490bd45590ec1340734b4ae72b`

## 1. Цель и границы

Закрыть тестовый и race-condition долг уже реализованного FW1, не меняя backend auth
API и не добавляя функции FW2. Browser session boundary должен быть детерминированным
при login, bootstrap, concurrent `401`, logout и потере сессии.

Backend contract остается неизменным:

| Operation | Endpoint | Request | Success |
|---|---|---|---|
| Login | `POST /api/v1/auth/login/by-slug` | `{tenantSlug,email,password}` | `AuthTokens` |
| Refresh | `POST /api/v1/auth/refresh` | `{refreshToken}` | rotated `AuthTokens` |
| Logout | `POST /api/v1/auth/logout` | `{refreshToken}` | `204` |
| Current user | `GET /api/v1/identity/me` | Bearer access token | `MeDto` |

`AuthTokens` содержит non-empty `accessToken`, `refreshToken`, `tokenType` и numeric
`expiresIn`. Refresh token ротируется. Roles/permissions приходят только из `/me`.

## 2. Current baseline

Уже существуют и не должны реализовываться повторно:

- `shared/auth/token-storage.ts`: access token in-memory, refresh token в
  `sessionStorage`;
- `shared/api/http-client.ts`: Bearer injection, single-flight refresh, one replay;
- `features/auth/api/auth.api.ts`;
- `features/auth/model/auth-context.tsx`;
- `RequireAuth`, `PermissionGuard`, `LoginPage` и protected router;
- MSW, Testing Library, jsdom и Vitest;
- три transport tests в `http-client.test.ts`.

## 3. Обнаруженные gaps

1. Нет tests для login, bootstrap, concurrent refresh, logout, guards и return path.
2. In-flight refresh может завершиться после logout и повторно записать tokens.
3. Safe return path проверяет `/` и `//`, но не backslash/control/normalized variants.
4. StrictMode может повторить bootstrap effect; отсутствие refresh storm не доказано.
5. `frontendweb/README.md` ошибочно говорит, что authentication еще отложен.
6. Session invalidation может эмититься повторно; cleanup обязан быть idempotent.

## 4. Scope

Expected production files:

```text
frontendweb/src/shared/auth/token-storage.ts
frontendweb/src/shared/api/http-client.ts
frontendweb/src/features/auth/model/auth-context.tsx
frontendweb/src/pages/auth/LoginPage.tsx
frontendweb/README.md
```

Allowed helpers:

```text
frontendweb/src/shared/auth/session-generation.ts
frontendweb/src/shared/routing/safe-return-path.ts
```

Required test areas may be split into:

```text
frontendweb/src/shared/api/http-client.auth.test.ts
frontendweb/src/shared/auth/token-storage.test.ts
frontendweb/src/features/auth/model/auth-context.test.tsx
frontendweb/src/features/auth/ui/auth-guards.test.tsx
frontendweb/src/pages/auth/LoginPage.test.tsx
```

Не добавлять Redux, axios, второй HTTP client или backend Java changes.

## 5. Session invariants

### 5.1 Storage

- access token существует только в module memory;
- refresh token существует только в current-tab `sessionStorage`;
- password/token/Authorization не логируются и не включаются в errors;
- `clearTokens()` безопасен при повторном вызове;
- unavailable/malformed browser storage трактуется как отсутствие session.

### 5.2 Session generation guard

Операции, завершающие session (`logout`, session-lost, failed bootstrap), увеличивают
monotonic in-memory generation. Async login/refresh/bootstrap запоминает generation и
может применить response только если generation не изменилась.

Обязательная race-проверка:

```text
request 401 -> refresh started -> logout -> refresh 200
```

После logout response не восстанавливает tokens/user/authenticated state. Generation
не хранится в browser storage и не строится на timestamp.

### 5.3 Refresh coordination

- одновременно выполняется максимум один refresh request;
- concurrent `401` ожидают один Promise;
- после success каждый исходный request replayed не более одного раза;
- replayed `401` завершает session без нового refresh;
- refresh `400/401/403/5xx`, network error, non-JSON и malformed DTO завершают session;
- обычный business `403` и request с `auth:false` refresh не запускают;
- coordinator освобождается после success и failure.

### 5.4 Auth state

| State | Invariant |
|---|---|
| `loading` | bootstrap или explicit login выполняется |
| `authenticated` | valid `/identity/me` загружен и `user != null` |
| `unauthenticated` | persisted session отсутствует или отвергнута |

Clear/logout атомарно для browser state очищает tokens, user и TanStack Query cache.

## 6. Operation contracts

### Bootstrap

1. Без refresh token нет network call, итог `unauthenticated`.
2. С refresh token вызывается `/me`; его `401` использует общий refresh/replay.
3. Success применяется только для актуальной generation и mounted provider.
4. Final failure очищает tokens/cache/user.
5. StrictMode cleanup не применяет stale response и не создает refresh storm.

### Login

1. `tenantSlug`: trim/lowercase; `email`: trim; password не преобразуется.
2. Pending submit disabled; duplicate submit не создает второй request.
3. Tokens сохраняются до `/me`, чтобы новый Bearer был доступен.
4. `/me` failure очищает local session и возвращает error UI.
5. Previous user/query cache не переживает failed login.
6. Success открывает только validated internal return path.

### Logout

1. Local generation инвалидируется до ожидания backend response.
2. При refresh token backend logout вызывается один раз.
3. `204`, `4xx/5xx` и network error завершаются local cleanup.
4. Server revocation failure не создает unhandled rejection.
5. In-flight refresh/bootstrap/login не восстанавливает session.

## 7. Safe return path

Разрешен только application-local absolute path:

- начинается ровно с одного `/`;
- не содержит scheme/host;
- не начинается с `//`, `/\\` или browser-normalized equivalent;
- не содержит control characters;
- сохраняет valid path/query/hash.

Invalid/missing input возвращает `/`. Helper pure и unit-tested. Return path из query
parameter в будущем обязан использовать тот же helper.

## 8. Permission behavior

- exact permission code берется только из `MeDto.permissions`;
- `PermissionGuard` показывает children только при permission;
- fallback optional, default `null`;
- guard не делает network call и не заменяет backend authorization;
- route-level RBAC/403 page относятся к FW2.

## 9. Required test matrix

### Storage

1. access token отсутствует в `localStorage`/`sessionStorage`;
2. refresh token только в `sessionStorage`;
3. clear и unavailable storage безопасны;
4. tests сбрасывают module memory и storage.

### HTTP/refresh

1. Bearer использует current access token;
2. три concurrent `401` создают один refresh;
3. success ротирует tokens и replay каждого request ровно один раз;
4. replayed `401` инвалидирует session без loop;
5. refresh `401/403/500`, network, invalid JSON/DTO инвалидируют session;
6. ordinary `403` и `auth:false` не вызывают refresh;
7. logout во время refresh запрещает stale token write.

### AuthProvider

1. bootstrap без token -> unauthenticated без network;
2. bootstrap с token -> `/me` -> authenticated;
3. failed bootstrap очищает tokens/query cache;
4. login success сохраняет tokens и loads `/me`;
5. invalid login и `/me` failure оставляют unauthenticated;
6. backend logout failure все равно очищает local state/cache;
7. session-lost idempotently очищает state;
8. stale result после unmount/logout игнорируется.

### Routing/UI

1. loading protected route имеет accessible `role=status`;
2. unauthenticated redirect сохраняет path/query/hash;
3. authenticated route renders children;
4. permission allowed/denied/fallback;
5. success login возвращает на safe internal path;
6. reject `https://`, `//host`, `/\\host`, control/encoded unsafe variants;
7. login error имеет safe `role=alert`, button выходит из pending;
8. double submit создает один login request.

MSW handlers проверяют method/path/body/Authorization, не только возвращают fixtures.
Real network запрещен.

## 10. Test infrastructure rules

- новый QueryClient для каждого test, retry выключен;
- handlers/storage очищаются after each;
- module reset применяется только для проверки in-memory token;
- fake timers не маскируют races;
- assertions не зависят от incidental React render count;
- tests устойчивы к StrictMode.

## 11. Error and security semantics

- final authenticated `401` означает session loss;
- `403` означает insufficient permission и не завершает session;
- ProblemDetail отображается через safe detail/title fallback;
- raw response, credentials и tokens не логируются;
- backend refresh rotation authoritative;
- HttpOnly cookie/BFF и multi-tab global logout требуют отдельного security ТЗ;
  `sessionStorage` не обещает cross-tab persistence.

## 12. Out of scope

- backend API/Java changes;
- registration/invitation/password reset/OTP/logout-all UI;
- HttpOnly cookie/BFF session;
- full RBAC navigation, 403 page и i18n (FW2);
- business screens и Playwright;
- provider/channel behavior.

## 13. Implementation order

1. Подготовить deterministic render/MSW helpers.
2. Закрыть storage tests.
3. Ввести session generation guard без изменения public auth API.
4. Закрыть HTTP single-flight/replay/failure matrix.
5. Закрыть AuthProvider bootstrap/login/logout races.
6. Extract/harden safe return helper.
7. Закрыть guards и LoginPage tests.
8. Обновить `frontendweb/README.md` по фактическому FW1.
9. Проверить отсутствие backend diff и выполнить frontend verification.

## 14. Verification

```bash
cd frontendweb
npm ci
npm run typecheck
npm run test:ci
npm run build
```

Backend `mvn verify` остается repository gate, но FW1 PR не меняет backend code/tests.

## 15. Definition of Done

- вся test matrix green;
- single-flight и exactly-one-replay доказаны;
- stale async result не восстанавливает завершенную session;
- safe redirect покрыт attack-like inputs;
- token placement/cleanup и query cache cleanup доказаны;
- production code не зависит от test-only hooks;
- README соответствует FW1;
- frontend checks и repository CI green;
- PR не содержит FW2, business UI или backend changes.
