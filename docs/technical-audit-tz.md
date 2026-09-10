# Collectra API — техническое задание по результатам повторного аудита

## 1. Цель

Стабилизировать техническую основу Collectra API перед дальнейшим развитием business-функционала.

На этом этапе не требуется усложнять архитектуру или вводить новые сервисы. Основная задача — закрыть обнаруженные риски в сборке, конфигурации, FileService и эксплуатационной наблюдаемости.

---

## 2. Приоритет P0 — стабильная сборка и конфигурация

### 2.1. Зафиксировать воспроизводимый build

Необходимо гарантировать, что локальная сборка и CI проверяют один и тот же код.

Перед `mvn clean verify` должна быть возможность определить текущий commit:

```bash
git status
git branch --show-current
git rev-parse HEAD
git log -1 --oneline
```

В GitHub Actions добавить вывод SHA текущего commit.

### Критерий приемки

```bash
mvn clean verify
```

стабильно завершается:

```text
Failures: 0
Errors: 0
BUILD SUCCESS
```

`OutboxClaimIntegrationTest` должен проходить независимо от порядка исполнения остальных тестов.

---

### 2.2. Изоляция integration tests

Integration tests не должны зависеть от данных, созданных другими тестами.

Требования:

- использовать уникальные UUID для тестовых сущностей;
- очищать собственные данные перед/после теста, где это необходимо;
- в assertions проверять только созданные текущим тестом записи;
- не использовать `findAll()` как источник истины, если таблица используется другими integration tests;
- не полагаться на порядок запуска тестовых классов.

Текущую архитектуру Outbox с `FOR UPDATE SKIP LOCKED` менять не требуется.

---

### 2.3. Разделить local и production configuration

Использовать минимум:

```text
application.yml
application-local.yml
application-test.yml
application-prod.yml
```

Local credentials и local endpoints перенести в `application-local.yml`.

В production обязательные параметры не должны иметь development fallback-значений:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
COLLECTRA_JWT_SECRET
COLLECTRA_OTP_PEPPER
RUSTFS_ENDPOINT
RUSTFS_ACCESS_KEY
RUSTFS_SECRET_KEY
```

Production должен завершать startup ошибкой при отсутствии обязательной конфигурации.

### Критерий приемки

Запуск с `SPRING_PROFILES_ACTIVE=prod` без обязательных secrets завершается ошибкой конфигурации и не использует local defaults.

---

## 3. Приоритет P1 — FileService reliability

### 3.1. Добавить терминальное состояние неудачного удаления

Сейчас после достижения `max-delete-attempts` файл перестает выбираться cleanup-процессом, но остается в `DELETE_PENDING`.

Добавить состояние:

```text
DELETE_FAILED
```

Целевой lifecycle:

```text
READY
  ↓ expiration
DELETE_PENDING
  ├─ success → DELETED
  ├─ temporary error → DELETE_PENDING + retry
  └─ max attempts reached → DELETE_FAILED
```

После последней неудачной попытки необходимо сохранить:

```text
status = DELETE_FAILED
delete_attempts = maxDeleteAttempts
last_error
last_delete_attempt_at
```

### Критерий приемки

Файл после исчерпания попыток больше не остается в неопределенном `DELETE_PENDING`.

---

### 3.2. Улучшить cleanup logging

Лог cleanup должен показывать реальный результат попытки:

```text
retryable
exhausted
```

Минимальные поля:

```text
fileId
tenantId
category
deleteAttempt
maxDeleteAttempts
attemptResult
```

Secrets, presigned URLs и содержимое файлов в лог не писать.

---

### 3.3. Добавить минимальные FileService metrics

Добавить Micrometer metrics:

```text
collectra_file_cleanup_processed_total
collectra_file_cleanup_deleted_total
collectra_file_cleanup_failed_total
collectra_file_cleanup_exhausted_total
```

Также желательно иметь gauge количества файлов в:

```text
DELETE_PENDING
DELETE_FAILED
```

Не требуется строить отдельную monitoring-систему в рамках этой задачи.

---

### 3.4. Убрать дублирование object storage configuration

Сейчас существуют две конфигурационные модели:

```text
collectra.storage
collectra.file.storage
```

Необходимо постепенно перевести генерацию документов на единый FileService/ObjectStorage abstraction.

Целевой поток:

```text
DocumentGeneration
    ↓
GeneratedOutputService
    ↓
FileService
    ↓
ObjectStorage
    ↓
RustFS
```

После миграции удалить legacy `collectra.storage.*`.

На этом этапе не требуется выделять FileService в отдельный микросервис.

---

## 4. Приоритет P1 — Outbox reliability

Текущую модель Outbox сохранить:

```text
PENDING
PROCESSING
RETRY_WAIT
PUBLISHED
DEAD
```

Сохранить также:

```text
FOR UPDATE SKIP LOCKED
attempt_count
locked_at
locked_by
retry
stale processing recovery
```

Архитектурную переработку Outbox в рамках этой задачи не выполнять.

Дополнительно добавить минимальные метрики:

```text
collectra_outbox_pending
collectra_outbox_retry_wait
collectra_outbox_dead
```

Критично иметь возможность обнаружить `DEAD > 0`.

---

## 5. Приоритет P2 — инженерное качество

### 5.1. Добавить Spotless в CI gate

В CI выполнять:

```bash
mvn spotless:check
mvn clean verify
```

или привязать `spotless:check` к Maven lifecycle.

---

### 5.2. Устранить текущие build warnings

Отдельно проверить:

- MapStruct compiler options;
- deprecated `@MockBean`;
- конфликт `commons-logging` / `spring-jcl`;
- deprecated API в `TemplateAssetService`.

Не требуется включать глобальный `-Werror`.

Цель — не накапливать новые предупреждения и постепенно убрать текущие.

---

### 5.3. Добавить build metadata

Через `/actuator/info` или аналогичный internal endpoint вывести:

```text
application version
git commit
build timestamp
Java version
```

Это позволит быстро определить, какой commit фактически развернут.

---

## 6. Обязательные regression tests

Минимальный набор:

### Outbox

- два worker не claim-ят одну запись;
- retry выполняется после delay;
- превышение числа попыток переводит event в `DEAD`;
- stale `PROCESSING` восстанавливается.

### FileService

- два cleanup worker не удаляют один файл дважды;
- временная ошибка storage приводит к retry;
- retry до истечения delay невозможен;
- успешный retry переводит файл в `DELETED`;
- достижение max attempts переводит файл в `DELETE_FAILED`;
- `DELETE_FAILED` автоматически повторно не выбирается.

### Configuration

- prod без JWT secret не стартует;
- prod без DB credentials не стартует;
- prod без storage credentials не стартует;
- local profile продолжает запускаться с локальной инфраструктурой.

---

## 7. Definition of Done

Работа считается завершенной, когда:

```text
mvn clean verify -> BUILD SUCCESS
spotless:check -> SUCCESS
Failures -> 0
Errors -> 0
```

Также выполнено:

- production не использует development secrets;
- FileService имеет `DELETE_FAILED`;
- legacy storage configuration больше не используется после миграции;
- Outbox и File Cleanup имеют минимальные эксплуатационные метрики;
- текущий git commit доступен через build metadata.

---

## 8. Порядок реализации

Рекомендуемый порядок без параллельного усложнения системы:

```text
1. Build + test isolation
2. Production configuration
3. FileService DELETE_FAILED
4. FileService metrics/logging
5. Legacy storage migration
6. Outbox metrics
7. Build warnings + build metadata
```

После этого можно продолжать развитие основного pipeline Collectra:

```text
Campaign
   ↓
Recipients
   ↓
Template rendering
   ↓
Document generation
   ↓
Outbox
   ↓
RabbitMQ
   ↓
Workers
   ↓
Email / SMS / WhatsApp / Telegram / In-App
```
