# Slice 6 — Campaign delivery counters and completion

Status: PLANNED  
Depends on: Slice 2, Slice 5  
Suggested branch: `feat/campaign-delivery-counters`

## 1. Цель

Сделать `CampaignRun` authoritative aggregate для delivery progress: корректно и атомарно учитывать `sentCount`, `failedCount`, `retryCount`, `skippedCount` и завершать run только по durable database state.

## 2. Текущий baseline

Уже есть:

```text
CampaignRun.recipientCount
CampaignRun.sentCount
CampaignRun.failedCount
CampaignRun.skippedCount
CampaignRun.retryCount
CampaignRun.complete(Instant)
```

Текущий invariant:

```text
sentCount + failedCount + skippedCount == recipientCount
```

Сейчас отсутствует полноценная atomic integration между Message terminal transition и CampaignRun counters.

## 3. Scope

Изменить:

```text
campaign.domain.CampaignRun
campaign.infrastructure.CampaignRunRepository
communication.application.MessageStateService
```

При необходимости добавить:

```text
campaign.application.CampaignRunDeliveryService
```

Но не создавать отдельный projection/counter table для MVP.

## 4. Counter semantics

### recipientCount

Количество recipient outcomes, подготовленных для run.

Не меняется во время delivery.

### sentCount

Увеличивается **ровно один раз**, когда Message впервые переходит:

```text
PROCESSING -> SENT
```

### failedCount

Увеличивается **ровно один раз**, когда Message впервые переходит:

```text
PROCESSING -> FAILED
```

### skippedCount

Относится к recipient, который terminally не должен получить Message по campaign eligibility/business reason.

Увеличивается на campaign/materialization path, не delivery worker-ом.

### retryCount

Количество фактически назначенных retry transitions:

```text
PROCESSING -> RETRY_WAIT
```

Это cumulative metric counter, а не текущее количество messages в `RETRY_WAIT`.

## 5. Domain API

Добавить domain methods без setters:

```java
public void messageSent() {
    sentCount++;
}

public void messageFailed() {
    failedCount++;
}

public void messageRetryScheduled() {
    retryCount++;
}

public void recipientSkipped() {
    skippedCount++;
}
```

Перед increment methods добавить defensive invariants, где это разумно:

- counters не могут стать отрицательными;
- terminal outcome counters не должны превысить `recipientCount`;
- completed run нельзя мутировать.

Не добавлять generic `incrementCounter(String)`.

## 6. CampaignRun repository lock

Нужен tenant-scoped pessimistic lock:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        select r
        from CampaignRun r
        where r.id = :runId
          and r.tenantId = :tenantId
        """)
Optional<CampaignRun> findLockedByIdAndTenantId(
        UUID tenantId,
        UUID runId);
```

## 7. Atomic Message transition + counter

Критическое правило:

```text
Message status transition
+
CampaignRun counter increment
```

должны находиться в одной transaction.

Пример SENT:

```text
TX
 lock Message
 validate PROCESSING
 lock CampaignRun
 Message.markSent(...)
 CampaignRun.messageSent()
 maybe complete
 commit
```

Если transaction rollback — не должно остаться ни нового Message status, ни increment counter.

То же правило для FAILED и RETRY_WAIT.

## 8. Lock order

Во всех delivery paths использовать один порядок:

```text
1. Message
2. CampaignRun
```

Не использовать где-то обратный порядок `CampaignRun -> Message`, иначе при concurrent workers увеличивается риск deadlock.

Materialization/skipped path, где Message отсутствует, может lock CampaignRun напрямую, но delivery transition path должен соблюдать fixed order.

## 9. Idempotency / double count prevention

Counter увеличивается только если реальный domain transition сейчас произошёл.

Пример duplicate broker event после SENT:

```text
Message already SENT
-> no transition
-> sentCount NOT incremented
```

Не делать:

```text
if worker result == success -> sentCount++
```

без проверки persisted Message state.

Правильное место increment — transaction service, который владеет locked Message transition.

## 10. Completion rule

После terminal transition или skip проверить:

```text
terminalCount = sentCount + failedCount + skippedCount
```

Если:

```text
terminalCount == recipientCount
AND CampaignRun.status == RUNNING
```

вызвать:

```java
campaignRun.complete(clock.instant());
```

Если `< recipientCount` — run остаётся RUNNING.

Если `> recipientCount` — это invariant violation и transaction должна fail.

## 11. Completion must not depend on RabbitMQ

Запрещено считать CampaignRun completed по:

- empty queue;
- Rabbit ACK count;
- no current PROCESSING rows;
- scheduler iteration finished;
- worker thread count.

Source of truth — durable counters/state в PostgreSQL.

## 12. Zero-recipient run

Явно определить behavior.

Для `recipientCount == 0` после prepare:

- run может быть завершён сразу согласно existing campaign orchestration;
- не ждать Message delivery;
- не создавать fake Message.

Добавить test.

## 13. Retry semantics

Retry не является terminal outcome:

```text
retryCount++
terminal count unchanged
CampaignRun remains RUNNING
```

После последующей success/failure увеличивается соответствующий terminal counter один раз.

Пример:

```text
attempt1 retry
attempt2 retry
attempt3 sent

retryCount = 2
sentCount  = 1
failedCount = 0
```

## 14. Skipped semantics

`skippedCount` должен увеличиваться только для recipient, который является частью `recipientCount`.

Если current prepare logic считает recipientCount только уже eligible recipients, перед реализацией сверить semantics и не double-count skip, который произошёл до фиксации `recipientCount`.

Правило проекта должно быть одно:

```text
recipientCount = number of recipient outcomes expected for this run
```

и invariant должен подтверждаться integration tests.

## 15. Multi-channel constraint

Current invariant предполагает one terminal delivery outcome per CampaignRecipient.

Пока unique constraint:

```text
messages.campaign_recipient_id
```

это соответствует EMAIL-only MVP.

До включения parallel multi-channel fan-out completion semantics необходимо пересмотреть отдельно. Slice 6 не пытается заранее решать multi-channel aggregation.

## 16. Failure of CampaignRun itself

`Message FAILED` не означает `CampaignRun FAILED`.

CampaignRun может завершиться `COMPLETED` с `failedCount > 0`, если все recipient outcomes terminal.

`CampaignRun.FAILED` резервируется для system-level failure существующего campaign lifecycle, а не для одного недоставленного email.

## 17. Database changes

Ожидаемо migration не нужна.

Проверить, что counters `NOT NULL DEFAULT 0` на DB level согласно текущей migration.

Не добавлять optimistic version только ради этого slice, если pessimistic lock достаточен.

## 18. Tests

### Domain unit

`CampaignRunTest`

- sent increment;
- failed increment;
- retry increment;
- skipped increment;
- completed run cannot mutate;
- terminal count cannot exceed recipientCount.

### PostgreSQL integration

`MessageDeliveryCounterIntegrationTest`

- PROCESSING -> SENT increments sent once;
- PROCESSING -> FAILED increments failed once;
- PROCESSING -> RETRY_WAIT increments retry only;
- transaction rollback preserves both old status and old counters;
- tenant isolation.

### Duplicate/redelivery

- same successful event twice -> `sentCount == 1`;
- same terminal failure twice -> `failedCount == 1`;
- retry event redelivery does not duplicate retry transition.

### Concurrent terminal transitions

Для разных Messages одного CampaignRun:

- concurrent SENT/FAILED increments not lost;
- final counters equal actual terminal messages;
- no deadlock under fixed lock order in representative test.

### Completion

- partial outcomes -> RUNNING;
- final terminal outcome -> COMPLETED;
- `completedAt` from fixed `Clock`;
- retry does not complete;
- zero-recipient case handled explicitly.

## 19. Порядок реализации

1. domain counter methods/invariants;
2. CampaignRun locked repository lookup;
3. integrate counters into MessageStateService terminal transactions;
4. retry counter integration;
5. skipped integration in campaign path;
6. completion check;
7. concurrency/idempotency tests;
8. `mvn verify`.

## 20. Out of scope

- analytics dashboards;
- multi-channel aggregate redesign;
- bounce/open/click counters;
- provider delivery receipts;
- separate statistics service;
- Redis counters.

## 21. Definition of Done

Slice 6 готов, если:

- SENT/FAILED/RETRY counters mutate atomically with Message state;
- skipped semantics согласованы с recipientCount;
- duplicate events не double-count;
- concurrent workers не теряют increments;
- fixed lock order используется в delivery path;
- CampaignRun completes only when all recipient outcomes terminal;
- Rabbit state не участвует в completion decision;
- Message FAILED не переводит весь CampaignRun в FAILED;
- `mvn verify` green.
