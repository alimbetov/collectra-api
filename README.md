# collectra-api

Модульный монолит Collectra на Java 17 и Spring Boot.

## Локальный запуск

```bash
docker compose up -d
export COLLECTRA_JWT_SECRET='replace-with-at-least-32-characters-secret'
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Локальные сервисы:

- PostgreSQL: `localhost:5432`, база `app_db`, пользователь `devuser`;
- RabbitMQ: `localhost:5672`, Management UI — `http://localhost:15672`;
- RustFS S3 API: `http://localhost:9000`, Console — `http://localhost:9001`.

Учётные данные в `compose.yaml` предназначены только для локальной разработки.

## Локальный вход

Tenant-пользователи входят через frontend `/login` в режиме `Tenant`: нужен slug рабочего
пространства, email и пароль. Этот режим вызывает `POST /api/v1/auth/login/by-slug`.

Платформенный супер-админ входит через тот же `/login`, но в режиме `Платформа`: slug tenant не
используется, поле логина принимает `super-admin`, пароль по умолчанию `Alimbetov_Ruslan`.
Frontend вызывает `POST /api/v1/platform/auth/login` и открывает `/platform`. Это отдельная
tenantless-сессия с ролью `PLATFORM_SUPER_ADMIN`; она не предназначена для перехода в обычные
tenant-разделы.

## Форматирование кода

Проект содержит общий IntelliJ IDEA code style и `.editorconfig`. Для форматирования всего Java-кода:

```bash
mvn spotless:apply
```

Для проверки форматирования без изменения файлов:

```bash
mvn spotless:check
```

Swagger UI: `http://localhost:8080/swagger-ui.html`
Health: `http://localhost:8080/actuator/health`

Матрица REST-доступа: [`docs/identity-rbac-rest-api.md`](docs/identity-rbac-rest-api.md).

## Первый сценарий

1. `POST /api/v1/auth/tenants/register` создаёт tenant и первого администратора.
2. `POST /api/v1/auth/login` выдаёт access JWT и rotating refresh token.
3. `POST /api/v1/auth/refresh` заменяет использованный refresh token новым.
4. `POST /api/v1/auth/logout` отзывает refresh token.

Каждый tenant-зависимый запрос обязан получать `tenant_id` из JWT. Заголовок `X-Tenant-Id` не считается источником доверия.
