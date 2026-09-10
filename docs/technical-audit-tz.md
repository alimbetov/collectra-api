# Collectra API — техническое задание по результатам повторного аудита

## 1. Цель и границы

Цель этапа — сделать техническую основу Collectra API предсказуемой и надежной перед дальнейшим развитием business-функционала.

Принцип этапа: **не усложнять архитектуру, а укрепить уже существующие механизмы**.

В рамках этого ТЗ:

- не выделяем новые микросервисы;
- не меняем базовую архитектуру Outbox;
- не вводим Kafka, Redis, distributed lock или отдельный scheduler service;
- не проектируем ручную admin-панель для recovery;
- не переписываем FileService целиком;
- закрепляем изменения unit- и integration-тестами.

Приоритет реализации: `P0 → P1 → P2`.

---

# 2. P0 — воспроизводимый build и test isolation

## 2.1. Зафиксировать commit, который реально тестируется

### Изменяемые файлы

```text
.github/workflows/ci.yml
```

### Требование

Перед Maven build CI должен выводить текущий Git SHA.

Добавить step:

```yaml
- name: Build metadata
  run: |
    echo "branch=${GITHUB_REF_NAME}"
    echo "sha=$(git rev-parse HEAD)"
    git log -1 --oneline
```

Не требуется отдельная build-система или shell script.

### Локальная проверка

```bash
git status
git branch --show-current
git rev-parse HEAD
git log -1 --oneline
mvn clean verify
```

### Acceptance criteria

```text
Failures: 0
Errors: 0
BUILD SUCCESS
```

SHA в CI должен соответствовать commit, для которого выполняется workflow.

---

## 2.2. Изоляция integration tests

Текущий `AbstractIntegrationTest` использует один PostgreSQL Testcontainer для Spring context. Это допустимо и не требуется менять.

Проблему изоляции решаем на уровне тестовых данных.

### Правила

Каждый integration test обязан:

1. создавать собственные UUID/tenant/project identifiers;
2. проверять только созданные им сущности;
3. не считать таблицу пустой без явной подготовки;
4. не использовать `findAll()` для assertions, если таблица разделяется с другими тестами;
5. не зависеть от порядка выполнения классов;
6. очищать данные в `@BeforeEach`/`@AfterEach` только там, где тест проверяет конкурентную работу общей таблицы.

### OutboxClaimIntegrationTest

Сохранить текущий подход:

```java
List<UUID> claimedIds = Stream.concat(a.stream(), b.stream()).toList();

assertThat(events.findAllById(claimedIds))
        .hasSize(4)
        .allMatch(event -> event.getStatus() == OutboxEventStatus.PROCESSING);
```

Не возвращать assertion через:

```java
events.findAll()
```

### Обязательный regression test

`OutboxClaimIntegrationTest.skipLockedGivesConcurrentTransactionsDisjointClaims`

Проверяет:

- worker A claim-ит 2 события;
- worker B claim-ит 2 события;
- пересечение UUID пустое;
- все четыре созданных события находятся в `PROCESSING`;
- test не зависит от других строк `outbox_events`.

### Acceptance criteria

Тест должен стабильно проходить при повторных запусках:

```bash
for i in {1..20}; do
  mvn -Dtest=OutboxClaimIntegrationTest test || exit 1
done
```

---

# 3. P0 — безопасная конфигурация

## 3.1. Разделить shared/local/test/prod configuration

Текущие файлы `application-local.yml`, `application-test.yml`, `application-prod.yml` уже существуют. Не создавать дополнительную систему конфигурации.

### Изменяемые файлы

```text
src/main/resources/application.yml
src/main/resources/application-local.yml
src/main/resources/application-test.yml
src/main/resources/application-prod.yml
```

### application.yml

Оставить только общие настройки:

```text
spring.application
JPA общие настройки
Liquibase
Rabbit publisher confirms
multipart limits
Actuator common exposure
общие retry/batch/retention defaults
```

Из `application.yml` убрать development fallback credentials.

Не должно оставаться:

```yaml
password: ${DB_PASSWORD:collectra}
jwt-secret: ${COLLECTRA_JWT_SECRET:local-development-...}
otp-pepper: ${COLLECTRA_OTP_PEPPER:local-development-...}
```

### application-local.yml

Local credentials допустимы только здесь:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/app_db
    username: devuser
    password: devpass
```

То же относится к RabbitMQ и локальному RustFS.

### application-test.yml

Тестовые secrets оставить изолированными в test profile.

Test profile должен продолжать отключать:

```text
Rabbit listener auto-startup
scheduler
File cleanup scheduler
Outbox scheduler
```

если конкретный integration test явно их не включает.

### application-prod.yml

Production profile обязан требовать реальные environment variables:

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

collectra:
  security:
    jwt-secret: ${COLLECTRA_JWT_SECRET}
    otp-pepper: ${COLLECTRA_OTP_PEPPER}
  file:
    storage:
      endpoint: ${RUSTFS_ENDPOINT}
      access-key: ${RUSTFS_ACCESS_KEY}
      secret-key: ${RUSTFS_SECRET_KEY}
```

Production startup без этих параметров должен завершаться ошибкой Spring placeholder resolution/config binding.

### Важное ограничение

Не добавлять собственный `EnvironmentValidator`, если fail-fast уже обеспечивается обязательными `${ENV_NAME}` placeholders.

### Tests

Добавить небольшой configuration test, например:

```text
src/test/java/io/collectra/api/config/ProductionConfigurationTest.java
```

Проверить минимум:

- prod profile без `COLLECTRA_JWT_SECRET` не поднимается;
- prod profile без DB credentials не поднимается;
- test profile продолжает подниматься;
- local profile properties остаются доступными локально.

Если полноценный Spring context test для отсутствующих ENV получается хрупким, допускается проверка через `ApplicationContextRunner`. Не поднимать отдельный Testcontainer для каждого negative-case.

---

# 4. P1 — FileService: терминальное состояние удаления

## 4.1. Проблема

Сейчас cleanup выбирает только:

```sql
status in ('READY', 'DELETE_PENDING')
and delete_attempts < :maxAttempts
```

После `maxDeleteAttempts` файл остается в `DELETE_PENDING`, но больше не claim-ится. С технической точки зрения запись зависает в промежуточном состоянии.

## 4.2. Добавить FileStatus.DELETE_FAILED

### Изменяемые файлы

```text
src/main/java/io/collectra/api/file/domain/FileStatus.java
src/main/java/io/collectra/api/file/domain/StoredFile.java
src/main/java/io/collectra/api/file/application/FileCleanupService.java
src/main/java/io/collectra/api/file/application/FileMetadata.java
```

`FileStatus`:

```java
public enum FileStatus {
    UPLOADING,
    READY,
    DELETE_PENDING,
    DELETE_FAILED,
    DELETED,
    FAILED,
    QUARANTINED
}
```

Отдельная колонка БД не нужна: `stored_file.status` уже `varchar(40)`.

Отдельная миграция нужна только в том случае, если будет добавлен DB CHECK constraint по status. В текущей схеме такого ограничения нет, поэтому не создавать пустую миграцию исключительно ради enum.

---

## 4.3. Доменная логика StoredFile

Добавить явный доменный метод:

```java
public void registerDeleteFailure(
        Instant attemptedAt,
        String error,
        int maxAttempts) {

    requireStatus(FileStatus.DELETE_PENDING, "register delete failure");

    if (maxAttempts < 1) {
        throw new IllegalArgumentException("maxAttempts must be positive");
    }

    this.deleteAttempts++;
    this.lastDeleteAttemptAt = require(attemptedAt, "attemptedAt");
    this.lastError = sanitizeError(error);

    if (this.deleteAttempts >= maxAttempts) {
        this.status = FileStatus.DELETE_FAILED;
    }
}
```

Старый overload без `maxAttempts` удалить либо оставить package-private только если он реально нужен. Желательно иметь одну семантику, чтобы лимит не вычислялся отдельно в service и domain.

### Инварианты

- `READY → DELETE_PENDING` разрешен;
- `DELETE_PENDING → DELETED` разрешен;
- `DELETE_PENDING → DELETE_PENDING` после временной ошибки разрешен;
- `DELETE_PENDING → DELETE_FAILED` после последней попытки разрешен;
- `DELETE_FAILED → DELETED` автоматически запрещен;
- `DELETE_FAILED → DELETE_PENDING` в этом ТЗ не реализуем;
- `DELETED` остается idempotent terminal state.

---

## 4.4. Unit tests StoredFile

Расширить:

```text
src/test/java/io/collectra/api/file/domain/StoredFileUnitTest.java
```

Обязательные unit tests:

```java
@Test
void deleteFailureBelowLimitKeepsDeletePending()
```

Проверить:

```text
deleteAttempts = 1
status = DELETE_PENDING
lastError заполнен
lastDeleteAttemptAt заполнен
```

```java
@Test
void deleteFailureAtLimitMovesToDeleteFailed()
```

Проверить:

```text
deleteAttempts = maxAttempts
status = DELETE_FAILED
```

```java
@Test
void cannotMarkDeleteFailedFileAsDeleted()
```

Ожидать `IllegalFileStateException`.

```java
@Test
void registerDeleteFailureRejectsInvalidMaxAttempts()
```

Ожидать `IllegalArgumentException` для `0` и отрицательных значений.

Существующие тесты lifecycle сохранить.

---

# 5. P1 — FileCleanupService reliability

## 5.1. Передавать maxAttempts в domain

В `FileCleanupService.registerFailure(...)` использовать:

```java
current.registerDeleteFailure(
        attemptedAt,
        error,
        properties.getCleanup().getMaxDeleteAttempts());
```

Не вычислять новый счетчик попыток в service вручную.

## 5.2. Возвращать результат failure transition

Чтобы корректно логировать `retryable/exhausted`, `registerFailure(...)` должен возвращать итоговый status либо небольшой enum результата.

Предпочтительный простой вариант:

```java
private FileStatus registerFailure(...)
```

После транзакции:

```java
FileStatus result = registerFailure(...);
boolean exhausted = result == FileStatus.DELETE_FAILED;
```

Не создавать отдельный event bus или exception hierarchy.

## 5.3. CleanupResult

Расширить record:

```java
public record CleanupResult(
        int processed,
        int deleted,
        int failed,
        int exhausted,
        int batches) {}
```

Семантика:

- `failed` — количество storage delete failures в текущем запуске;
- `exhausted` — subset `failed`, который достиг лимита и стал `DELETE_FAILED`.

## 5.4. Logging

Для временной ошибки:

```text
attemptResult=retryable
```

Для последней попытки:

```text
attemptResult=exhausted
```

Минимальный набор structured fields:

```text
fileId
tenantId
category
deleteAttempt
maxDeleteAttempts
attemptResult
```

Ошибка storage передается как exception в logger, но secrets/URL/content явно не логируются.

## 5.5. Scheduler summary

`FileCleanupScheduler` должен логировать:

```text
processed
deleted
failed
exhausted
batches
```

Пример:

```java
log.info(
    "File cleanup completed processed={} deleted={} failed={} exhausted={} batches={}",
    result.processed(),
    result.deleted(),
    result.failed(),
    result.exhausted(),
    result.batches());
```

---

# 6. P1 — FileCleanup integration tests

Расширить существующий:

```text
FileCleanupServiceIntegrationTest
```

Не создавать второй почти идентичный integration test class.

## Обязательные сценарии

### 6.1. Concurrent claim

Сохранить текущий тест:

```java
concurrentRunsDoNotDeleteSameClaimedFileTwice()
```

Assertions:

```text
storage.delete -> exactly once
second cleanup processed = 0
final status = DELETED
```

### 6.2. Retry delay

Сохранить:

```java
failedDeleteIsRetriedOnlyAfterRetryDelay()
```

Assertions:

```text
1-я попытка -> DELETE_PENDING, attempts=1
early retry -> processed=0
after delay -> DELETED
storage.delete -> exactly twice
```

### 6.3. Exhaustion

Изменить текущий тест:

```java
maxDeleteAttemptsMovesPoisonFileToDeleteFailed()
```

После десяти попыток:

```java
assertThat(exhausted.getDeleteAttempts()).isEqualTo(10);
assertThat(exhausted.getStatus()).isEqualTo(FileStatus.DELETE_FAILED);
```

Следующий cleanup:

```text
processed = 0
storage.delete count = 10
```

Дополнительно:

```text
CleanupResult.exhausted() = 1
```

только на последнем запуске.

---

# 7. P1 — FileService.delete consistency

`FileService.delete(...)` выполняет ручное delete и использует ту же `StoredFile.registerDeleteFailure`.

Это значит, что после изменения domain method ручное удаление тоже обязано соблюдать `maxDeleteAttempts`.

### Изменяемый код

В catch-блоке:

```java
current.registerDeleteFailure(
        Instant.now(),
        ex.getMessage(),
        properties.getCleanup().getMaxDeleteAttempts());
```

### Поведение

- первая временная ошибка → `DELETE_PENDING`;
- последняя разрешенная ошибка → `DELETE_FAILED`;
- повторный вызов `delete()` для `DELETE_FAILED` должен завершаться `FileNotReadyException`;
- автоматического reset attempts не делать.

### Unit/integration coverage

Добавить тест сервиса на:

```text
manual delete failure increments attempts
manual delete at max attempts becomes DELETE_FAILED
manual delete of DELETE_FAILED does not call ObjectStorage.delete again
```

Если `FileService` уже покрывается API integration test, использовать существующий класс, а не создавать дублирующий Spring context.

---

# 8. P1 — FileService metrics

Micrometer уже подключен. Новую monitoring библиотеку не добавлять.

## Реализация

Добавить небольшой компонент:

```text
io.collectra.api.file.infrastructure.metrics.FileMetrics
```

или метрики непосредственно в `FileCleanupService`, если это позволяет сохранить код проще.

Рекомендуемые counters:

```text
collectra.file.cleanup.processed
collectra.file.cleanup.deleted
collectra.file.cleanup.failed
collectra.file.cleanup.exhausted
```

Не использовать tenantId/fileId как metric tags — это создаст high cardinality.

Допустимые low-cardinality tags:

```text
category
result
```

### Test

Использовать `SimpleMeterRegistry` в unit test.

Проверить:

```text
successful deletion increments processed + deleted
retryable error increments processed + failed
exhausted error increments processed + failed + exhausted
```

Не тестировать Prometheus HTTP exposition в отдельном integration test.

---

# 9. P1 — Outbox: не переделывать, только закрепить reliability

Текущая модель остается:

```text
PENDING
PROCESSING
RETRY_WAIT
PUBLISHED
DEAD
```

Текущие компоненты сохраняются:

```text
OutboxEvent
OutboxClaimService
OutboxStateService
OutboxPublisher
OutboxRetryPolicy
OutboxRepository
```

`FOR UPDATE SKIP LOCKED` сохраняется.

## 9.1. Unit tests OutboxEvent

Расширить `OutboxEventUnitTest` следующими сценариями:

```java
markDeadClearsOwnership()
```

Проверить:

```text
status=DEAD
lockedAt=null
lockedBy=null
lastErrorCode заполнен
```

```java
publishedEventCannotBeRetried()
```

Ожидать `IllegalStateException`.

```java
retryWaitCannotBeClaimedBeforeNextAttemptAt()
```

Этот сценарий уже фактически есть — сохранить.

```java
recoverKeepsAttemptCount()
```

Текущий тест сохранить.

## 9.2. Unit tests OutboxRetryPolicy

Расширить существующий тест:

```text
constructor rejects maxAttempts < 1
delayForAttempt rejects attempt < 1
attempt >= 5 returns 1 hour
```

Не усложнять retry schedule configurable DSL.

## 9.3. OutboxPublisher behavior

Закрепить unit-тестами минимум:

```text
unknown event type -> DEAD
invalid JSON -> DEAD
Rabbit ACK -> PUBLISHED
Rabbit NACK before max attempts -> RETRY_WAIT
Rabbit failure at max attempts -> DEAD
InterruptedException restores interrupted flag
```

Для этих тестов мокировать `RabbitTemplate`, `OutboxStateService`, router и retry policy. RabbitMQ container здесь не нужен.

## 9.4. Idempotency contract

Событие уже публикует:

```text
messageId = event.id
x-event-id = event.id
```

Это считать системным контрактом.

Новые consumers должны использовать `eventId` для идемпотентной обработки, но реализация общей таблицы processed_events в рамках этого ТЗ **не требуется**, пока нет concrete consumer, где это необходимо.

---

# 10. P1 — Outbox metrics

Добавить минимальные counters:

```text
collectra.outbox.published
collectra.outbox.retry
collectra.outbox.dead
collectra.outbox.recovered
```

Gauge по всей таблице в каждом publish loop не выполнять.

Если нужен статус количества `DEAD`, реализовать repository count:

```java
long countByStatus(OutboxEventStatus status);
```

и Micrometer gauge с low-frequency polling/actuator access.

Не добавлять отдельный monitoring scheduler только ради gauge.

Unit tests counters — через `SimpleMeterRegistry`.

---

# 11. P1 — единый ObjectStorage

Сейчас существуют:

```text
collectra.storage
collectra.file.storage
```

и `application-test.yml`/`application-local.yml` содержат обе конфигурации.

Это технический долг, но миграция должна быть линейной, без нового abstraction layer поверх уже существующего `ObjectStorage`.

## Целевой поток

```text
GeneratedOutputService
    ↓
FileService
    ↓
ObjectStorage
    ↓
RustFS/S3-compatible storage
```

## Требования

1. Найти прямое использование legacy `collectra.storage` в document generation.
2. Перевести generated PDF/HTML output на FileService с категорией `REPORT` либо `EXPORT` по назначению.
3. В БД хранить `stored_file.id` как reference на сгенерированный объект, если модель generation job уже поддерживает такую связь.
4. После отсутствия usages удалить legacy properties/configuration.
5. Удалить legacy значения из local/test config.

## Ограничение

Не выделять FileService в отдельный сервис и не создавать второй storage gateway.

## Tests

Добавить/обновить unit test GeneratedOutputService:

```text
calls FileService once
uses expected FileCategory
passes tenant/project ownership
propagates returned fileId
```

Сохранить один integration happy-path генерации документа.

---

# 12. P2 — Maven/CI hygiene

## 12.1. Spotless

В CI добавить отдельный step перед build:

```yaml
- name: Check formatting
  run: mvn --batch-mode --no-transfer-progress spotless:check
```

Затем:

```yaml
- name: Verify with PostgreSQL Testcontainers
  run: mvn --batch-mode --no-transfer-progress clean verify
```

Не привязывать auto-format (`spotless:apply`) к build.

## 12.2. MapStruct warning

Проверить, почему javac выводит:

```text
options were not recognized by any processor
```

Не убирать compiler args только ради скрытия warning.

Проверить фактическое участие `mapstruct-processor` в annotationProcessorPaths.

После исправления:

```text
-Amapstruct.defaultComponentModel=spring
-Amapstruct.unmappedTargetPolicy=ERROR
```

должны применяться без warning.

## 12.3. Deprecated MockBean

Новые тесты писать через актуальный Spring Test механизм (`@MockitoBean`), если версия Spring Boot 3.5.16 его поддерживает в используемом test stack.

Старые `@MockBean` можно заменить в затрагиваемых классах, но не делать массовый рефакторинг всего test tree в рамках этой задачи.

## 12.4. commons-logging

Через:

```bash
mvn dependency:tree -Dincludes=commons-logging:commons-logging
```

определить транзитивный источник.

Если dependency не требуется напрямую — добавить точечный `<exclusion>` в родительскую зависимость.

Не исключать произвольные logging зависимости без dependency tree evidence.

---

# 13. P2 — build metadata

Добавить build information стандартными средствами Spring Boot/Maven.

Предпочтительно использовать `spring-boot-maven-plugin` build-info вместо собственного REST controller.

Пример:

```xml
<executions>
    <execution>
        <goals>
            <goal>build-info</goal>
        </goals>
    </execution>
</executions>
```

Git SHA можно добавить через существующий Maven git commit id plugin, только если он действительно нужен для `/actuator/info`.

Не создавать отдельную таблицу/endpoint.

Acceptance:

```text
/actuator/info
```

позволяет определить как минимум application version и build time; Git SHA должен быть доступен в CI logs в любом случае.

---

# 14. Матрица обязательных тестов

| Область | Тип | Сценарий |
|---|---|---|
| StoredFile | Unit | READY → DELETE_PENDING → DELETED |
| StoredFile | Unit | delete failure ниже лимита оставляет DELETE_PENDING |
| StoredFile | Unit | delete failure на лимите переводит DELETE_FAILED |
| StoredFile | Unit | DELETE_FAILED нельзя markDeleted |
| FileCleanup | Integration | два cleanup worker не удаляют один объект дважды |
| FileCleanup | Integration | retry раньше delay не выполняется |
| FileCleanup | Integration | retry после delay выполняется |
| FileCleanup | Integration | max attempts → DELETE_FAILED |
| FileService | Unit/Integration | manual delete failure учитывает max attempts |
| OutboxEvent | Unit | claim → publish |
| OutboxEvent | Unit | retry → повторный claim после nextAttemptAt |
| OutboxEvent | Unit | DEAD очищает lock |
| OutboxRetryPolicy | Unit | retry schedule и validation |
| OutboxPublisher | Unit | ACK/NACK/timeout/invalid payload/unknown type |
| OutboxClaim | Integration | concurrent claims disjoint через SKIP LOCKED |
| Config | Context test | prod без secrets fail-fast |
| Metrics | Unit | counters увеличиваются корректно |

---

# 15. Definition of Done

Работа считается завершенной только при одновременном выполнении условий:

```text
mvn spotless:check -> SUCCESS
mvn clean verify   -> BUILD SUCCESS
Failures           -> 0
Errors             -> 0
```

Дополнительно:

- `DELETE_FAILED` реализован как terminal automatic-cleanup state;
- unit tests фиксируют доменные state transitions;
- integration tests фиксируют PostgreSQL `SKIP LOCKED` и retry semantics;
- prod profile не имеет development secret fallback;
- cleanup различает `retryable` и `exhausted`;
- FileService и Outbox имеют минимальные low-cardinality metrics;
- legacy object storage config удаляется после миграции generated output;
- CI выводит точный Git SHA;
- новые изменения не вводят отдельные микросервисы, distributed locks или лишние инфраструктурные зависимости.

---

# 16. Порядок реализации

Выполнять последовательно небольшими изменениями:

```text
1. CI SHA + test isolation
2. prod/local/test configuration cleanup
3. FileStatus.DELETE_FAILED + StoredFile unit tests
4. FileCleanupService + integration tests
5. FileService.delete consistency tests
6. File cleanup metrics/logging
7. Outbox unit-test hardening + metrics
8. legacy storage migration
9. Spotless/warnings/build metadata
10. final mvn spotless:check && mvn clean verify
```

Каждый шаг должен оставлять ветку в состоянии `BUILD SUCCESS`. Не накапливать несколько незавершенных инфраструктурных изменений перед запуском полного test suite.