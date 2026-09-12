# Slice 6 — Campaign delivery counters and durable completion

Status: READY FOR IMPLEMENTATION  
Depends on: Slice 2, Slice 5  
Specification branch: `spec/slice-06-campaign-delivery-counters`  
Suggested implementation branch: `feat/campaign-delivery-counters`

## 1. Цель

Сделать `CampaignRun` authoritative durable aggregate для delivery progress и завершения campaign run.

Slice 6 должен гарантировать:

```text
Message state transition
+
CampaignRun counter mutation
+
CampaignRun completion decision
```

в одной database transaction для каждого outcome transition.

Основные свойства:

- `sentCount`, `failedCount`, `retryCount`, `skippedCount` не теряются при concurrency;
- один persisted transition учитывается ровно один раз;
- fixed lock order для delivery outcome path: `Message -> CampaignRun`;
- `CampaignRun` завершается только по durable PostgreSQL state;
- duplicate/redelivery/recovery не double-count;
- RabbitMQ, worker lifecycle и scheduler state не являются source of truth.

---

## 2. Текущий baseline после Slice 5

Уже существует `CampaignRun`:

```text
recipientCount
sentCount
failedCount
skippedCount
retryCount
preparedAt
startedAt
completedAt
status
```

и domain lifecycle:

```text
PREPARING -> READY -> RUNNING -> COMPLETED
                         \-> FAILED
                         \-> CANCELLED
```

`CampaignRun.complete(...)` уже проверяет:

```text
sentCount + failedCount + skippedCount == recipientCount
```

Slice 5 создаёт:

```text
CampaignRecipient
Message(status=QUEUED)
MESSAGE_DELIVERY_REQUESTED outbox event
```

и final eligibility может перевести `CampaignRecipient` в `SKIPPED`.

Текущий delivery lifecycle Message:

```text
QUEUED
  -> PROCESSING
       -> SENT
       -> FAILED
       -> RETRY_WAIT
            -> QUEUED
```

Критический текущий gap:

- `MessageStateService.markSent()` меняет только Message;
- `MessageStateService.markFailed()` меняет только Message;
- `MessageStateService.scheduleRetry()` меняет только Message;
- `MessageRecoveryService` напрямую делает `PROCESSING -> RETRY_WAIT/FAILED`, минуя `MessageStateService`;
- `CampaignRun` counters с этими переходами атомарно не связаны;
- Slice 5 skip path пока не обновляет `skippedCount`;
- durable completion пока отсутствует.

---

## 3. Scope

Обязательные изменения:

```text
campaign.domain.CampaignRun
campaign.infrastructure.CampaignRunRepository
campaign.application.CampaignMessageMaterializer
communication.application.MessageStateService
communication.application.MessageRecoveryService
```

При необходимости добавить узкий application service, например:

```text
campaign.application.CampaignRunDeliveryService
```

или оставить orchestration внутри `MessageStateService`, если dependency direction остаётся чистым.

Не добавлять отдельную counter/projection table для MVP.

`MessageRetryDispatcher` не должен увеличивать `retryCount`: retry считается при transition `PROCESSING -> RETRY_WAIT`, а не при последующем `RETRY_WAIT -> QUEUED`.

---

## 4. Authoritative semantics counters

### 4.1 recipientCount

`recipientCount` — количество `CampaignRecipient`, созданных для конкретного run после prepare.

Для текущего EMAIL-only MVP:

```text
1 CampaignRecipient = 1 expected terminal outcome
```

После `CampaignRun.ready(...)` значение immutable.

### 4.2 sentCount

Увеличивается ровно один раз на реальный persisted transition:

```text
PROCESSING -> SENT
```

### 4.3 failedCount

Увеличивается ровно один раз на реальный persisted transition:

```text
PROCESSING -> FAILED
```

Причина failure не важна:

- permanent provider rejection;
- exhausted retry policy;
- stale PROCESSING timeout после исчерпания retries.

### 4.4 retryCount

Cumulative metric количества реально назначенных retry transitions:

```text
PROCESSING -> RETRY_WAIT
```

Retry не terminal.

Пример:

```text
attempt1 -> RETRY_WAIT
attempt2 -> RETRY_WAIT
attempt3 -> SENT

retryCount  = 2
sentCount   = 1
failedCount = 0
```

`RETRY_WAIT -> QUEUED` не меняет counters.

### 4.5 skippedCount

Увеличивается ровно один раз, когда existing `CampaignRecipient`, уже входящий в `recipientCount`, становится `SKIPPED` во время final eligibility/materialization.

В текущем Slice 5 candidate query уже исключает `SKIPPED` и recipient с существующим Message, поэтому повторный materialization batch не должен повторно учитывать skip.

---

## 5. CampaignRun domain API

Добавить explicit methods без public setters:

```java
public void messageSent() {
    requireMutableRunning();
    ensureTerminalCapacity(1);
    sentCount++;
}

public void messageFailed() {
    requireMutableRunning();
    ensureTerminalCapacity(1);
    failedCount++;
}

public void messageRetryScheduled() {
    requireMutableRunning();
    retryCount++;
}

public void recipientSkipped() {
    requireMutableRunning();
    ensureTerminalCapacity(1);
    skippedCount++;
}
```

Допускается package-private/internal batch variant для materializer, например:

```java
void recipientsSkipped(int count)
```

но только с теми же invariants и без generic string-based counter API.

### 5.1 Mandatory invariants

Перед mutation:

- run должен быть `RUNNING`;
- completed/failed/cancelled run immutable для delivery counters;
- counters не могут стать отрицательными;
- `sentCount + failedCount + skippedCount <= recipientCount`;
- `retryCount >= 0`;
- terminal increment, который сделал бы terminal count `> recipientCount`, должен бросить exception и rollback transaction.

Добавить helper:

```java
public int terminalCount() {
    return sentCount + failedCount + skippedCount;
}
```

или private equivalent.

---

## 6. CampaignRun locking

Использовать tenant-scoped pessimistic lock:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        select r
        from CampaignRun r
        where r.id = :runId
          and r.tenantId = :tenantId
        """)
Optional<CampaignRun> findLockedByIdAndTenantId(
        UUID runId,
        UUID tenantId);
```

Если метод уже существует после Slice 5 — переиспользовать, не создавать дубликат.

---

## 7. Fixed lock order

### 7.1 Delivery outcome paths

Для любого transition, который одновременно меняет Message и CampaignRun:

```text
1. lock Message PESSIMISTIC_WRITE
2. validate current Message status
3. obtain campaignRunId from locked Message
4. lock CampaignRun PESSIMISTIC_WRITE
5. mutate Message
6. mutate CampaignRun counter
7. maybeComplete(run)
8. commit
```

Единый order:

```text
Message -> CampaignRun
```

Запрещён production path:

```text
CampaignRun -> Message
```

для delivery transition.

### 7.2 Materialization/skip path

`CampaignMessageMaterializer` уже locks `CampaignRun` до обработки batch и до создания Message.

Это допустимо, потому что skip transition не имеет Message и delivery event не может обработать новый Message до commit/outbox publication.

Не нужно искусственно создавать Message lock для skipped recipient.

---

## 8. Atomic transition contract

Для `SENT` transaction должна выглядеть концептуально так:

```text
TX
  lock Message
  if status != PROCESSING -> idempotent no-op / transition rejected according contract
  lock CampaignRun
  Message.markSent(...)
  CampaignRun.messageSent()
  maybeComplete(CampaignRun)
COMMIT
```

Для `FAILED`:

```text
TX
  lock Message
  validate PROCESSING
  lock CampaignRun
  Message.markFailed(...)
  CampaignRun.messageFailed()
  maybeComplete(CampaignRun)
COMMIT
```

Для retry:

```text
TX
  lock Message
  validate PROCESSING
  lock CampaignRun
  Message.scheduleRetry(...)
  CampaignRun.messageRetryScheduled()
COMMIT
```

Если любая часть transaction rollback — не должно остаться частичного состояния.

Например rollback после `Message.markSent()` обязан оставить:

```text
Message.status = PROCESSING
CampaignRun.sentCount unchanged
```

---

## 9. Idempotency contract

Counters считаются по **persisted transition**, не по delivery result object и не по broker event.

### 9.1 Duplicate terminal callback/event

Если повторно вызван `markSent` для Message, уже находящегося в `SENT`:

```text
Message unchanged
sentCount unchanged
completion unchanged
```

То же для already `FAILED`.

### 9.2 Wrong/stale transition

Если stale worker пытается завершить Message, который уже находится в другом state, service не должен increment counter.

Preferred contract для asynchronous redelivery:

```text
expected transition absent -> no-op result
```

а не exception, который провоцирует бесконечную broker redelivery.

Допускается internal result enum:

```java
TransitionResult.APPLIED
TransitionResult.NO_OP
```

но это не обязательный API shape.

### 9.3 Retry idempotency

Повторная попытка `scheduleRetry` после уже committed `RETRY_WAIT`:

```text
retryCount NOT incremented
```

---

## 10. Durable completion rule

После каждого terminal outcome:

- `PROCESSING -> SENT`;
- `PROCESSING -> FAILED`;
- recipient -> `SKIPPED`;

под locked `CampaignRun` выполнить:

```text
terminalCount = sentCount + failedCount + skippedCount
```

Правила:

```text
terminalCount < recipientCount
    -> RUNNING

terminalCount == recipientCount
    -> if RUNNING: complete(clock.instant())

terminalCount > recipientCount
    -> invariant violation -> rollback
```

Completion и последний counter increment должны быть в одной transaction.

Следовательно crash после commit не может оставить ситуацию:

```text
all recipients terminal
but run permanently RUNNING
```

для перехода, который уже был успешно зафиксирован Slice 6 path.

---

## 11. Completion source of truth

CampaignRun completion запрещено определять по:

- RabbitMQ queue emptiness;
- Rabbit ACK/NACK count;
- отсутствию текущих worker threads;
- завершению scheduler iteration;
- отсутствию `PROCESSING` rows;
- отсутствию due retries;
- transient in-memory metrics.

Source of truth:

```text
CampaignRun.recipientCount
CampaignRun.sentCount
CampaignRun.failedCount
CampaignRun.skippedCount
CampaignRun.status
```

в PostgreSQL под pessimistic lock.

---

## 12. Critical recovery-path redesign

### 12.1 Почему текущий код нельзя оставить

Сейчас `MessageRecoveryService.recoverStale()`:

```text
findStaleProcessingForUpdate(batch)
-> держит Message row locks на весь batch
-> напрямую message.scheduleRetry()/markFailed()
```

После Slice 6 такой код недопустим по двум причинам:

1. он обходит counter/completion transaction boundary;
2. если внутри одного batch начать lock несколько `CampaignRun`, два concurrent recovery batch могут захватить разные run locks в разном порядке и создать deadlock cycle.

### 12.2 Required redesign

Recovery должен использовать тот же single-message outcome transaction service, что и обычный delivery worker.

Рекомендуемый shape:

```text
scheduler scans candidate message IDs
for each messageId:
    transactional recoverOne(messageId)
        lock Message
        re-check PROCESSING + stale condition
        lock CampaignRun
        if retry exhausted:
            Message -> FAILED
            failedCount++
            maybeComplete
        else:
            Message -> RETRY_WAIT
            retryCount++
```

Ключевое правило:

```text
one Message -> one CampaignRun lock pair per outcome transaction
```

Не держать locks на десятки Message из разных campaign runs одновременно во время counter mutation.

Если для candidate scan используется `SKIP LOCKED`, lock должен быть краткоживущим и не превращаться в batch transaction, которая затем locks multiple CampaignRuns.

Допускается отдельный `REQUIRES_NEW` per-message processor, но self-invocation через тот же Spring bean использовать нельзя; transactional boundary должен реально проходить через proxy/отдельный bean.

---

## 13. MessageRetryDispatcher semantics

Current dispatcher:

```text
RETRY_WAIT -> QUEUED
+ MESSAGE_DELIVERY_REQUESTED outbox
```

не меняет CampaignRun counters.

Он должен сохранить atomicity:

```text
Message.requeue()
+
outbox requestDelivery
```

в одной transaction, как сейчас.

`retryCount` уже был увеличен при `PROCESSING -> RETRY_WAIT`.

Не считать повторную публикацию delivery request как новый retry.

---

## 14. Slice 5 skipped integration

`CampaignMessageMaterializer` уже держит `CampaignRun` pessimistic lock и вызывает page-scoped eligibility.

После `EligibilityBatch` Slice 6 должен учитывать количество реально изменённых recipients в `SKIPPED`.

Preferred integration:

```text
CampaignRun locked
eligibility.evaluateBatch(...)
actualSkipped = evaluated.skipped()
CampaignRun.recipientSkipped() x actualSkipped
maybeComplete(run)
```

или безопасный batch method.

Важно:

- count должен соответствовать recipient rows, которые именно в этой transaction стали `SKIPPED`;
- повторный batch/restart не должен повторно увеличить `skippedCount`;
- candidate query уже исключает `SKIPPED`, это является частью idempotency design;
- `skippedCount` относится только к recipients, уже вошедшим в `recipientCount`.

---

## 15. Zero-recipient run

Current Slice 5 behavior требует явной корректировки.

Для:

```text
recipientCount == 0
```

run не должен зависнуть `READY` или `RUNNING` навсегда.

Required behavior:

```text
first materialization/orchestration entry
READY -> RUNNING -> COMPLETED
completedAt = clock.instant()
```

в одной transaction под locked `CampaignRun`.

Не создавать fake `CampaignRecipient`, fake Message или fake counter.

После завершения повторный materialization call должен быть deterministic/idempotent: либо вернуть empty result без mutation, либо orchestration layer должен не вызывать materializer для completed run. Выбранный contract закрепить тестом.

---

## 16. Message FAILED != CampaignRun FAILED

`Message FAILED` является terminal recipient outcome.

CampaignRun может иметь:

```text
status = COMPLETED
failedCount > 0
```

если все expected recipient outcomes terminal.

`CampaignRun.FAILED` резервируется для system-level failure run lifecycle, а не provider rejection конкретного email.

---

## 17. Concurrency model

### 17.1 Different Messages, same CampaignRun

Два workers могут одновременно получить:

```text
M1 -> SENT
M2 -> FAILED
```

Оба сначала locks свои Message rows, затем сериализуются на одном CampaignRun lock.

Expected result:

```text
sentCount +1
failedCount +1
no lost update
```

### 17.2 Same Message duplicate workers

Оба конкурируют за один Message lock.

Первый применяет transition и counter.
Второй после получения lock видит terminal/non-expected state и делает no-op без counter increment.

### 17.3 Completion race

Если два последних Messages одного run завершаются одновременно:

- CampaignRun lock сериализует counter increments;
- только transaction, после которой terminalCount становится равен recipientCount, вызывает `complete(...)`;
- второй не может выполнить duplicate completion.

---

## 18. Database requirements

Ожидаемо новая migration не нужна.

Перед реализацией подтвердить существующие constraints/defaults:

```text
campaign_runs.recipient_count NOT NULL DEFAULT 0
campaign_runs.sent_count      NOT NULL DEFAULT 0
campaign_runs.failed_count    NOT NULL DEFAULT 0
campaign_runs.skipped_count   NOT NULL DEFAULT 0
campaign_runs.retry_count     NOT NULL DEFAULT 0
```

Не добавлять отдельную counter table.

Не добавлять Redis counters.

Не добавлять optimistic `@Version` только ради Slice 6: pessimistic row lock является primary concurrency mechanism.

---

## 19. Application-service responsibilities

### MessageStateService

После Slice 6 должен быть authoritative owner обычных delivery outcome transitions:

```text
begin              QUEUED -> PROCESSING
markSent           PROCESSING -> SENT + sentCount + maybeComplete
scheduleRetry      PROCESSING -> RETRY_WAIT + retryCount
markFailed         PROCESSING -> FAILED + failedCount + maybeComplete
```

`begin()` не меняет CampaignRun counters и не обязан lock CampaignRun.

### MessageRecoveryService

Не должен напрямую вызывать domain outcome methods без CampaignRun integration.

Он отвечает за:

```text
find stale candidates
invoke single-message recovery transaction
```

### CampaignMessageMaterializer

Отвечает за:

```text
SKIPPED recipient + skippedCount + maybeComplete
```

в уже существующей locked-run transaction.

---

## 20. Tests

### 20.1 CampaignRun domain unit tests

Добавить/расширить `CampaignRunTest`:

- `messageSent` increments once;
- `messageFailed` increments once;
- `messageRetryScheduled` increments retry only;
- `recipientSkipped` increments skipped;
- terminal counters cannot exceed `recipientCount`;
- completed run counters immutable;
- failed/cancelled run counters immutable;
- retry count does not affect terminalCount;
- complete requires exact terminalCount.

### 20.2 MessageStateService PostgreSQL integration

`MessageDeliveryCounterIntegrationTest`:

- `PROCESSING -> SENT` updates Message + `sentCount` atomically;
- `PROCESSING -> FAILED` updates Message + `failedCount` atomically;
- `PROCESSING -> RETRY_WAIT` updates Message + `retryCount` atomically;
- retry does not change terminal counters;
- tenant isolation;
- missing/foreign run cannot leave Message partially mutated;
- forced rollback preserves old Message state and old CampaignRun counters.

### 20.3 Duplicate/idempotency tests

- duplicate SENT callback/event -> `sentCount == 1`;
- duplicate FAILED callback/event -> `failedCount == 1`;
- duplicate retry scheduling -> `retryCount == 1` for one applied transition;
- stale worker after another worker committed -> no counter mutation.

### 20.4 Recovery integration tests

Mandatory because recovery currently bypasses `MessageStateService`:

- stale PROCESSING + retries available -> `RETRY_WAIT`, `retryCount +1`;
- stale PROCESSING + exhausted -> `FAILED`, `failedCount +1`;
- terminal recovery can complete final CampaignRun;
- duplicate recovery scans do not double-count;
- recovery processes messages from multiple runs without lost counters/deadlock in representative concurrent test.

### 20.5 Skip/materialization integration tests

Extend Slice 5 integration coverage:

- final eligibility skip increments `skippedCount`;
- skipped recipient creates no Message;
- rerunning materialization does not increment skipped twice;
- last skipped recipient completes run;
- mixed `SENT + FAILED + SKIPPED` eventually completes exactly once.

### 20.6 Completion tests

- partial outcomes -> RUNNING;
- last SENT -> COMPLETED;
- last FAILED -> COMPLETED;
- last SKIPPED -> COMPLETED;
- retry does not complete;
- `completedAt` comes from injected fixed `Clock`;
- zero-recipient run completes durably;
- `Message FAILED` does not set CampaignRun status FAILED.

### 20.7 Concurrency tests

На PostgreSQL/Testcontainers:

- two different Messages same run complete concurrently -> no lost increments;
- duplicate workers same Message -> one applied transition;
- two final Messages same run -> exactly one durable completion;
- representative delivery + recovery concurrency completes without deadlock;
- test should use bounded timeout so deadlock/regression fails deterministically.

---

## 21. Recommended implementation order

1. Add `CampaignRun` counter domain API and invariants.
2. Reuse/verify `CampaignRunRepository.findLockedByIdAndTenantId`.
3. Introduce one helper for `maybeComplete(run, clock.instant())`.
4. Integrate `markSent` into transaction `Message -> CampaignRun`.
5. Integrate `markFailed`.
6. Integrate `scheduleRetry`.
7. Make these transitions idempotent for duplicate asynchronous calls.
8. Refactor `MessageRecoveryService` to single-message outcome transactions through the same counter path.
9. Integrate Slice 5 `SKIPPED -> skippedCount + maybeComplete`.
10. Implement explicit zero-recipient completion behavior.
11. Add rollback/idempotency/concurrency tests.
12. Run Spotless and full `mvn --batch-mode --no-transfer-progress clean verify`.

---

## 22. Out of scope

- delivery/open/click analytics dashboard;
- provider delivery receipts after initial accepted/send result;
- bounce counters;
- separate statistics microservice;
- Kafka aggregation;
- Redis counters;
- multi-channel recipient fan-out;
- replacing RabbitMQ;
- campaign progress websocket/SSE API;
- historical counter rebuild job.

---

## 23. Multi-channel constraint

Current DB invariant:

```text
UNIQUE(messages.campaign_recipient_id)
```

и Slice 5 EMAIL-only model означают:

```text
one CampaignRecipient -> max one Message -> max one terminal delivery outcome
```

Slice 6 строится на этом invariant.

При будущем multi-channel fan-out completion model должен быть пересмотрен отдельно; нельзя просто убрать unique constraint и оставить текущую формулу counters.

---

## 24. Definition of Done

Slice 6 считается завершённым только если одновременно выполнено всё:

- `SENT`, `FAILED`, `RETRY_WAIT` counters mutate в одной transaction с соответствующим Message transition;
- delivery path везде соблюдает lock order `Message -> CampaignRun`;
- `MessageRecoveryService` больше не обходит counter/completion path;
- recovery не держит multi-run batch locks во время counter mutation;
- final eligibility `SKIPPED` атомарно увеличивает `skippedCount`;
- duplicate/redelivery/stale worker не double-count;
- concurrent workers не теряют increments;
- last terminal outcome атомарно переводит run в `COMPLETED`;
- zero-recipient run не остаётся зависшим;
- completion не зависит от RabbitMQ/scheduler/in-memory state;
- `Message FAILED` не переводит весь run в `FAILED`;
- `completedAt` берётся из injected `Clock`;
- representative concurrency tests green;
- `mvn --batch-mode --no-transfer-progress clean verify` green.

---

## 25. Architectural invariant summary

После Slice 6 основной invariant проекта должен быть формулируем так:

```text
For every CampaignRecipient in a RUNNING CampaignRun,
exactly one terminal outcome is eventually represented by
SENT, FAILED or SKIPPED.

CampaignRun terminal counters are mutated only in the same
PostgreSQL transaction that durably creates that outcome.

The last terminal outcome durably completes CampaignRun.
```

И lock invariant:

```text
Delivery outcome transaction:
Message PESSIMISTIC_WRITE
    -> CampaignRun PESSIMISTIC_WRITE
        -> state + counter + completion
            -> COMMIT
```
