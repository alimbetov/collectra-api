# Test Infrastructure Cleanup

Status: READY FOR IMPLEMENTATION

## 1. Goal

Сделать integration test runtime детерминированным и тихим по умолчанию перед финальным закрытием Slice 10A.

Главный принцип: generic Spring/Testcontainers tests не должны случайно запускать background schedulers, consumers или provider simulations, которые не относятся к проверяемому сценарию.

## 2. Mandatory changes

### 2.1 RabbitMQ scheduler default OFF in tests

В общем test profile:

```yaml
collectra:
  messaging:
    scheduler-enabled: false
```

или эквивалентный существующей конфигурации property.

Scheduler включается явно только в тестах, которые проверяют:

- retry dispatch;
- recovery dispatch;
- outbox publication;
- broker scheduling semantics.

Запрещено включать scheduler через широкий `@SpringBootTest` default.

### 2.2 Background workers

Каждый background component должен иметь явный enable/disable property или test-specific bean boundary.

Generic integration context MUST NOT:

- consume RabbitMQ queues;
- mutate Message state asynchronously;
- publish retries;
- recover stale messages;
- invoke external provider clients.

### 2.3 Deterministic time

Все time-sensitive tests используют injected/fixed `Clock`.

Запрещено строить assertions на wall clock sleep там, где можно управлять временем напрямую.

### 2.4 Deterministic provider mocks

Mock/fake providers должны поддерживать explicit scripted outcomes:

```text
ACCEPTED
RETRYABLE
RATE_LIMITED
PERMANENT_FAILURE
AUTH_FAILURE
TIMEOUT_BEFORE_ACCEPT
ACCEPT_THEN_TIMEOUT
MALFORMED_RESPONSE
SLOW_SUCCESS
```

Random 80/20 режим разрешён только как exploratory smoke mode и не используется как acceptance gate.

### 2.5 Testcontainers ownership

- PostgreSQL/RabbitMQ lifecycle централизован;
- tests не создают неконтролируемое число containers/contexts;
- connection budget документирован;
- broker/database state очищается между сценариями;
- parallel execution допускается только там, где доказана isolation.

### 2.6 Repository cleanup

Удалить accidental root artifact:

```text
fatal: Invalid revision range b0264e079f01b4b29d4ecae2b8c8fb4efc8adc2b..HEAD
```

Проверить `.gitignore` на временные shell/build/test artifacts.

## 3. Required tests

1. generic application integration test стартует без RabbitMQ scheduler activity;
2. scheduler-specific test включает scheduler explicitly;
3. fixed Clock полностью управляет retry/recovery timestamps;
4. test context shutdown не оставляет worker threads;
5. provider mock сценарии reproducible across repeated runs;
6. full `mvn verify` два последовательных запуска дают одинаковый результат.

## 4. Definition of Done

- generic tests не зависят от timing races background scheduler;
- no accidental broker consumption in unrelated tests;
- no random acceptance assertions;
- full suite green минимум два последовательных запуска;
- repository root очищен от accidental artifacts.
