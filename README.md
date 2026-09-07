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

Swagger UI: `http://localhost:8080/swagger-ui.html`
Health: `http://localhost:8080/actuator/health`

## Первый сценарий

1. `POST /api/v1/auth/tenants/register` создаёт tenant и первого администратора.
2. `POST /api/v1/auth/login` выдаёт access JWT и rotating refresh token.
3. `POST /api/v1/auth/refresh` заменяет использованный refresh token новым.
4. `POST /api/v1/auth/logout` отзывает refresh token.

Каждый tenant-зависимый запрос обязан получать `tenant_id` из JWT. Заголовок `X-Tenant-Id` не считается источником доверия.
