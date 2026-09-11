# Communication / Delivery Core — implementation specification

> **Статус:** detailed design. Перед реализацией обязательно применить binding
> corrections из `docs/roadmap/communication-delivery-core-code-audit.md`, особенно
> для payload/locale rendering, `PROCESSING` recovery, provider idempotency,
> transaction boundaries и tenant-scoped counter updates.

## 0. Назначение документа

Этот документ превращает high-level ТЗ в последовательность конкретных изменений для текущего `collectra-api`.

Цель: реализовать delivery pipeline без лишней инфраструктуры и без изменения основных принципов Campaign Core.

Текущая база проекта уже содержит:

- `campaign` module с `Campaign`, `CampaignRun`, `CampaignRecipient`;
- `CampaignEligibilityService`;
- `template` module;
- `shared.outbox`;
- RabbitMQ publisher;
- application `Clock`;
- пустой `communication` package.

Поэтому новая задача должна ДОБАВЛЯТЬ delivery core, а не создавать параллельные framework-слои.

---

# 1. Итоговый поток

```text
CampaignRun READY
    |
    v
CampaignDeliveryService.start(runId)
    |
    +--> eligibility recheck
    |
    +--> для ELIGIBLE recipient
            create Message(QUEUED)
            + OutboxEvent(MESSAGE_DELIVERY_REQUESTED)
                    |
                    v
               shared.outbox
                    |
                    v
                 RabbitMQ
                    |
                    v
          MessageDeliveryWorker
                    |
                    v
          ChannelProviderRegistry
             |      |      |
           EMAIL   SMS   WHATSAPP ...
                    |
                    v
          ProviderSendResult
                    |
                    v
             Message transition
                    |
                    v
        CampaignDeliveryStatsService
                    |
                    v
              CampaignRun counters
```

Ключевое правило: **один worker для всех каналов**.

---

# 2. Scope v1

## Реализуем сейчас

- Message domain;
- Message repository;
- CampaignRecipient -> Message;
- reuse существующего `shared.outbox`;
- новый `MESSAGE_DELIVERY_REQUESTED` route;
- одна RabbitMQ delivery queue;
- один `MessageDeliveryWorker`;
- `ChannelProviderRegistry`;
- mock providers;
- success / retryable / permanent failure;
- delivery retry/backoff;
- агрегирование результата в `CampaignRun`;
- integration tests.

## Не реализуем сейчас

- отдельные queues/workers по каналам;
- SMTP/SendGrid/Twilio/Infobip;
- webhooks delivery receipts;
- provider failover;
- rate limiter;
- DeliveryAttempt table;
- generic workflow engine;
- generic retry engine;
- distributed lock;
- отдельный communication outbox.

---

# 3. Важное ограничение текущего Campaign Core

Сейчас `CampaignService.create(...)` явно разрешает только `TemplateChannel.EMAIL`.

Поэтому implementation делаем двумя уровнями:

### Phase 1

Полностью рабочий vertical slice через EMAIL:

```text
Campaign -> CampaignRecipient -> Message -> Outbox -> RabbitMQ -> Worker -> MockEmailProvider
```

### Phase 2

После стабилизации pipeline подключаются SMS / WHATSAPP / TELEGRAM / IN_APP как тонкие adapters.

Не нужно одновременно переписывать Campaign selection/contact resolution для всех каналов.

---

# 4. Package structure

Предлагаемая структура соответствует текущему modular style проекта:

```text
io.collectra.api.communication

  api/
    MessageController.java

  application/
    CampaignDeliveryService.java
    MessageService.java
    MessageDeliveryService.java
    MessageRetryService.java
    CampaignDeliveryStatsService.java

  domain/
    Message.java
    MessageStatus.java
    CommunicationChannel.java
    ProviderResultStatus.java
    ProviderSendResult.java

  infrastructure/
    MessageRepository.java
    CommunicationMessagingConfig.java
    MessageDeliveryWorker.java
    MessageRetryScheduler.java

    provider/
      ChannelProvider.java
      ChannelProviderRegistry.java

      mock/
        MockDeliveryBehavior.java
        MockEmailProvider.java
        MockSmsProvider.java
        MockWhatsAppProvider.java
        MockTelegramProvider.java
        MockInAppProvider.java
```

Не обязательно создавать все файлы одним commit. Реализовывать по vertical slices.

---

# 5. Message entity

## 5.1 Таблица

Новая таблица:

```text
messages
```

Поля:

```text
id UUID PK

tenant_id UUID NOT NULL
campaign_id UUID NOT NULL
campaign_run_id UUID NOT NULL
campaign_recipient_id UUID NOT NULL
customer_id UUID NOT NULL
invoice_id UUID NULL
template_version_id UUID NOT NULL

channel VARCHAR(30) NOT NULL
destination VARCHAR(500) NOT NULL
resolved_locale VARCHAR(35) NOT NULL
subject VARCHAR(500) NULL
body TEXT NOT NULL

status VARCHAR(20) NOT NULL
attempt_count INT NOT NULL DEFAULT 0
next_retry_at TIMESTAMP NULL
processing_started_at TIMESTAMP NULL

provider_message_id VARCHAR(255) NULL
last_error_code VARCHAR(80) NULL
last_error_message VARCHAR(1000) NULL

sent_at TIMESTAMP NULL
created_at TIMESTAMP NOT NULL
updated_at TIMESTAMP NOT NULL
```

Constraint:

```text
UNIQUE (campaign_recipient_id)
```

Indexes:

```text
idx_message_run_status(campaign_run_id, status)
idx_message_retry(status, next_retry_at)
idx_message_tenant_created(tenant_id, created_at)
```

Не добавлять FK везде, если текущий проект избегает жёстких cross-module FK. Следовать существующему style Liquibase.

## 5.2 Entity skeleton

```java
@Entity
@Table(
    name = "messages",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_message_recipient",
        columnNames = {"campaign_recipient_id"}
    )
)
public class Message extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "campaign_run_id", nullable = false)
    private UUID campaignRunId;

    @Column(name = "campaign_recipient_id", nullable = false)
    private UUID campaignRecipientId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "template_version_id", nullable = false)
    private UUID templateVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CommunicationChannel channel;

    @Column(nullable = false, length = 500)
    private String destination;

    @Column(name = "resolved_locale", nullable = false, length = 35)
    private String resolvedLocale;

    @Column(length = 500)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 1000)
    private String lastErrorMessage;

    @Column(name = "sent_at")
    private Instant sentAt;
}
```

---

# 6. Message statuses

```java
public enum MessageStatus {
    QUEUED,
    PROCESSING,
    RETRY_WAIT,
    SENT,
    FAILED
}
```

Transitions:

```text
QUEUED -> PROCESSING
PROCESSING -> SENT
PROCESSING -> RETRY_WAIT
PROCESSING -> FAILED
RETRY_WAIT -> QUEUED
```

Terminal:

```text
SENT
FAILED
```

Не добавлять `CREATED`. Message создаётся сразу как готовая delivery command.

---

# 7. Message domain methods

Business transitions держать в Entity либо в одном application service, но не размазывать по worker/provider.

Предлагаемые методы:

```java
public void markProcessing() {
    requireStatus(MessageStatus.QUEUED);
    status = MessageStatus.PROCESSING;
}

public void markSent(String providerMessageId, Instant now) {
    requireStatus(MessageStatus.PROCESSING);
    status = MessageStatus.SENT;
    this.providerMessageId = providerMessageId;
    this.sentAt = now;
    this.nextRetryAt = null;
    this.lastErrorCode = null;
    this.lastErrorMessage = null;
}

public void scheduleRetry(
        Instant nextRetryAt,
        String errorCode,
        String errorMessage) {
    requireStatus(MessageStatus.PROCESSING);
    status = MessageStatus.RETRY_WAIT;
    this.nextRetryAt = nextRetryAt;
    this.lastErrorCode = errorCode;
    this.lastErrorMessage = truncate(errorMessage);
}

public void markFailed(String code, String message) {
    requireStatus(MessageStatus.PROCESSING);
    status = MessageStatus.FAILED;
    nextRetryAt = null;
    lastErrorCode = code;
    lastErrorMessage = truncate(message);
}

public void requeue() {
    requireStatus(MessageStatus.RETRY_WAIT);
    status = MessageStatus.QUEUED;
    nextRetryAt = null;
}
```

## attemptCount semantics

`attemptCount` увеличивается перед фактическим вызовом provider.

```java
public void beginAttempt() {
    requireStatus(MessageStatus.QUEUED);
    status = MessageStatus.PROCESSING;
    attemptCount++;
}
```

Так `attemptCount=1` означает, что первая реальная provider попытка началась.

---

# 8. MessageRepository

Минимальный repository:

```java
public interface MessageRepository extends JpaRepository<Message, UUID> {

    Optional<Message> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Message> findByCampaignRecipientId(UUID campaignRecipientId);

    List<Message> findAllByTenantIdAndCampaignRunIdOrderByCreatedAtAsc(
        UUID tenantId,
        UUID campaignRunId
    );

    long countByCampaignRunIdAndStatusIn(
        UUID campaignRunId,
        Collection<MessageStatus> statuses
    );

    // Retry/recovery batch queries use native FOR UPDATE SKIP LOCKED;
    // exact SQL is defined in the binding code audit.
}
```

## Atomic claim

Для защиты от двух consumers предпочтительно сделать conditional update:

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("""
    update Message m
       set m.status = :processing,
           m.attemptCount = m.attemptCount + 1,
           m.processingStartedAt = :now,
           m.updatedAt = :now,
           m.version = m.version + 1
     where m.id = :messageId
       and m.status = :queued
""")
int claim(
    @Param("messageId") UUID messageId,
    @Param("queued") MessageStatus queued,
    @Param("processing") MessageStatus processing,
    @Param("now") Instant now
);
```

Если `claim(...) == 0`, worker не вызывает provider.

Это проще distributed lock и достаточно для v1.

---

# 9. CampaignRun aggregate counters

Текущий `CampaignRun` не хранит delivery statistics.

Добавить минимально устойчивые counters:

```text
recipient_count INT NOT NULL DEFAULT 0
sent_count INT NOT NULL DEFAULT 0
failed_count INT NOT NULL DEFAULT 0
skipped_count INT NOT NULL DEFAULT 0
retry_count INT NOT NULL DEFAULT 0
```

НЕ хранить mutable counters:

```text
queuedCount
processingCount
retryWaitCount
```

Они легко рассинхронизируются. При необходимости их можно получить запросом по `messages.status`.

## Почему эти пять counters

- `recipientCount` — сколько recipient snapshots было создано prepare;
- `sentCount` — сколько доставок завершено успехом;
- `failedCount` — terminal delivery failures;
- `skippedCount` — recipients, остановленные eligibility;
- `retryCount` — сколько дополнительных provider attempts было запланировано/произошло.

## CampaignRun methods

Использовать `Clock`, а не `Instant.now()` напрямую. Текущий `CampaignRun` сейчас использует `Instant.now()`, поэтому при реализации Communication Core стоит заодно перевести lifecycle methods на передаваемый `Instant now`.

Пример:

```java
public void prepared(int recipientCount, Instant now) {
    require(CampaignRunStatus.PREPARING);
    this.recipientCount = recipientCount;
    this.status = CampaignRunStatus.READY;
    this.preparedAt = now;
}
```

Не обязательно хранить `queued`/`processing` counters.

---

# 10. CampaignDeliveryStatsService

Provider не должен обновлять CampaignRun.

Worker вызывает отдельный application service после успешного status transition.

```java
@Service
public class CampaignDeliveryStatsService {

    private final CampaignRunRepository runs;
    private final MessageRepository messages;
    private final Clock clock;

    @Transactional
    public void recordSent(UUID tenantId, UUID runId) {
        requireOne(runs.incrementSent(tenantId, runId, Instant.now(clock)));
        completeIfDone(tenantId, runId);
    }

    @Transactional
    public void recordFailed(UUID tenantId, UUID runId) {
        requireOne(runs.incrementFailed(tenantId, runId, Instant.now(clock)));
        completeIfDone(tenantId, runId);
    }

    @Transactional
    public void recordSkipped(UUID tenantId, UUID runId, int delta) {
        requireOne(runs.incrementSkipped(tenantId, runId, delta, Instant.now(clock)));
        completeIfDone(tenantId, runId);
    }

    @Transactional
    public void recordRetry(UUID tenantId, UUID runId) {
        requireOne(runs.incrementRetry(tenantId, runId, Instant.now(clock)));
    }
}
```

Counters в repository лучше обновлять atomically:

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("""
    update CampaignRun r
       set r.sentCount = r.sentCount + 1,
           r.updatedAt = :now,
           r.version = r.version + 1
     where r.id = :runId
       and r.tenantId = :tenantId
       and r.status = io.collectra.api.campaign.domain.CampaignRunStatus.RUNNING
""")
int incrementSent(UUID tenantId, UUID runId, Instant now);
```

Аналогично failed/skipped/retry.

Это безопаснее read-modify-write при нескольких workers.

---

# 11. Completion rule

Run считается завершённым, когда все prepared recipients получили terminal outcome.

Простая формула:

```text
sentCount + failedCount + skippedCount == recipientCount
```

и status == RUNNING.

Тогда:

```text
RUNNING -> COMPLETED
```

`retryCount` в формулу не входит.

Важно: recipient без Message из-за `PAID`, `NO_CONTACT`, `CUSTOMER_INACTIVE` учитывается как `skippedCount`.

---

# 12. CampaignDeliveryService — orchestration start

Новый application service запускает delivery для подготовленного run.

```java
@Service
public class CampaignDeliveryService {

    private final CampaignRepository campaigns;
    private final CampaignRunRepository runs;
    private final CampaignRecipientRepository recipients;
    private final CampaignEligibilityService eligibility;
    private final MessageService messages;
    private final Clock clock;

    @Transactional
    public DeliveryStartResult start(UUID tenantId, UUID runId) {
        CampaignRun run = runs.findForStart(tenantId, runId)
            .orElseThrow(() -> new NoSuchElementException("Campaign run not found"));

        Campaign campaign = campaigns.findByIdAndTenantId(run.getCampaignId(), tenantId)
            .orElseThrow(() -> new NoSuchElementException("Campaign not found"));

        if (run.getStatus() != CampaignRunStatus.READY) {
            throw new IllegalStateException("Campaign run must be READY");
        }

        run.start(Instant.now(clock));

        CampaignEligibilityService.EligibilityResult result =
            eligibility.recheck(tenantId, runId);

        // Iterate stable Slice<CampaignRecipient> pages of 500 and batch-load
        // Customer/Email/Invoice data for every page.
        List<CampaignRecipient> values = nextRecipientPage(tenantId, runId);

        int created = 0;
        for (CampaignRecipient recipient : values) {
            if (recipient.getStatus() == CampaignRecipientStatus.SKIPPED) {
                // skip counter должен быть записан ровно один раз
                continue;
            }
            if (recipient.getStatus() == CampaignRecipientStatus.ELIGIBLE) {
                if (messages.createFromRecipient(tenantId, campaign, recipient).created()) {
                    created++;
                }
            }
        }

        return new DeliveryStartResult(runId, result.total(), created, result.skipped());
    }
}
```

## Важная поправка по skipped counters

Не инкрементировать `skippedCount` простым проходом каждый раз, иначе повторный start/recheck может удвоить counters.

Нужен один из двух простых вариантов:

### Preferred v1

`CampaignEligibilityService.recheck(...)` возвращает количество **newly skipped transitions**, а не просто текущее skipped количество.

То есть внутри цикла:

```java
if (recipient.getStatus() != SKIPPED) {
    recipient.skip("PAID");
    newlySkipped++;
}
```

После recheck:

```java
stats.addSkipped(runId, newlySkipped);
```

### Альтернатива

Не хранить skipped counter инкрементально, а пересчитывать один раз после eligibility. Но preferred вариант проще для дальнейшего lifecycle.

---

# 13. Message creation transaction

`Message` и OutboxEvent обязаны сохраняться в одной транзакции.

```java
@Service
public class MessageService {

    private final MessageRepository messages;
    private final OutboxService outbox;
    private final ObjectMapper json;
    private final MessageContentFactory contentFactory;

    @Transactional
    public CreateMessageResult createFromRecipient(
            UUID tenantId, Campaign campaign, CampaignRecipient recipient) {

        CommunicationChannel channel =
            CommunicationChannel.valueOf(recipient.getChannel());

        Optional<Message> existing =
            messages.findByCampaignRecipientId(recipient.getId());

        if (existing.isPresent()) {
            return new CreateMessageResult(existing.get().getId(), false);
        }

        RenderedMessage rendered = contentFactory.render(recipient);

        Message message = messages.save(
            Message.queued(...)
        );

        String payload = json.writeValueAsString(
            Map.of("messageId", message.getId())
        );

        outbox.append(
            message.getTenantId(),
            "MESSAGE",
            message.getId(),
            "MESSAGE_DELIVERY_REQUESTED",
            payload
        );

        return new CreateMessageResult(message.getId(), true);
    }
}
```

DB unique constraint остаётся финальной защитой от race condition.

---

# 14. Content creation

Worker НЕ должен рендерить template.

Message должен содержать final snapshot:

```text
subject
body
destination
channel
locale-derived rendered content
```

Причина: если template или customer data изменятся между queue и send, отправка должна использовать именно тот content, который был сформирован для данного CampaignRun.

Для первого slice можно сделать `MessageContentFactory` только для EMAIL и переиспользовать существующую template/rendering инфраструктуру.

Не строить generic rendering engine заново.

---

# 15. Outbox integration

Использовать существующий:

```text
io.collectra.api.shared.outbox.OutboxService
io.collectra.api.shared.outbox.OutboxPublisher
io.collectra.api.shared.outbox.OutboxEventRouter
```

Новый event:

```text
MESSAGE_DELIVERY_REQUESTED
```

Payload:

```json
{
  "messageId": "UUID"
}
```

Не помещать в event body/subject/customer data.

Worker всегда читает текущий `Message` из PostgreSQL.

---

# 16. OutboxEventRouter change

Сейчас router знает только `DOCUMENT_GENERATION_REQUESTED`.

Добавить вторую ветку:

```java
if ("MESSAGE_DELIVERY_REQUESTED".equals(eventType)) {
    return new OutboxRoute(
        CommunicationMessagingConfig.EXCHANGE,
        CommunicationMessagingConfig.ROUTING_KEY
    );
}
```

На v1 обычный `if` здесь допустим. Не нужно преждевременно делать Map registry/router framework для двух типов событий.

---

# 17. RabbitMQ topology

Одна queue для всех communication channels.

```java
@Configuration
public class CommunicationMessagingConfig {

    public static final String EXCHANGE = "collectra.communication";
    public static final String QUEUE = "collectra.communication.delivery";
    public static final String ROUTING_KEY = "communication.delivery.requested";

    @Bean
    DirectExchange communicationExchange() { ... }

    @Bean
    Queue communicationDeliveryQueue() { ... }

    @Bean
    Binding communicationDeliveryBinding(...) { ... }
}
```

Не создавать:

```text
email.queue
sms.queue
whatsapp.queue
...
```

Пока нет объективной причины разносить topology.

---

# 18. Rabbit event DTO

```java
public record MessageDeliveryRequested(UUID messageId) {}
```

Worker listener получает этот DTO либо `JsonNode` и извлекает UUID.

Предпочтительнее typed DTO.

---

# 19. Один MessageDeliveryWorker

```java
@Component
public class MessageDeliveryWorker {

    private final MessageDeliveryService delivery;

    @RabbitListener(queues = CommunicationMessagingConfig.QUEUE)
    public void handle(MessageDeliveryRequested event) {
        delivery.deliver(event.messageId());
    }
}
```

Worker максимально тонкий.

Он НЕ должен:

- знать retry delays;
- обновлять CampaignRun напрямую;
- содержать template rendering;
- содержать channel-specific `if`;
- работать с Customer/Invoice напрямую.

---

# 20. MessageDeliveryService

```java
@Service
public class MessageDeliveryService {

    private final MessageRepository messages;
    private final ChannelProviderRegistry providers;
    private final CommunicationRetryPolicy retryPolicy;
    private final CampaignDeliveryStatsService stats;
    private final Clock clock;

    public void deliver(UUID messageId) {

        if (!claim(messageId)) {
            return;
        }

        Message message = load(messageId);
        ChannelProvider provider = providers.get(message.getChannel());

        ProviderSendResult result;
        try {
            result = provider.send(message);
        } catch (Exception ex) {
            result = ProviderSendResult.retryable(
                "PROVIDER_EXCEPTION",
                rootMessage(ex)
            );
        }

        applyResult(messageId, result);
    }
}
```

Provider runtime exception трактуется как `RETRYABLE_ERROR`, если нет доказательства permanent failure.

---

# 21. Transaction boundaries worker

Не держать DB transaction открытой во время сетевого provider call.

Рекомендуемый порядок:

```text
TX1:
  atomic QUEUED -> PROCESSING
  attemptCount++
COMMIT

provider.send(...)

TX2:
  PROCESSING -> SENT / RETRY_WAIT / FAILED
  update stats
COMMIT
```

Это важно, когда mock заменится реальным provider.

Не делать:

```java
@Transactional
public void deliver(...) {
    // DB read
    // remote HTTP call 5 sec
    // DB update
}
```

---

# 22. Provider contract

```java
public interface ChannelProvider {
    CommunicationChannel channel();
    ProviderSendResult send(ProviderSendCommand command);
}
```

`ProviderSendCommand` является immutable DTO и несёт
`idempotencyKey=messageId.toString()`. Provider не получает JPA entity.

```java
public enum ProviderResultStatus {
    SUCCESS,
    RETRYABLE_ERROR,
    PERMANENT_ERROR
}
```

```java
public record ProviderSendResult(
    ProviderResultStatus status,
    String providerMessageId,
    String errorCode,
    String errorMessage
) {
    public static ProviderSendResult success(String providerMessageId) { ... }
    public static ProviderSendResult retryable(String code, String message) { ... }
    public static ProviderSendResult permanent(String code, String message) { ... }
}
```

---

# 23. ChannelProviderRegistry

```java
@Component
public class ChannelProviderRegistry {

    private final Map<CommunicationChannel, ChannelProvider> providers;

    public ChannelProviderRegistry(List<ChannelProvider> values) {
        this.providers = values.stream()
            .collect(Collectors.toUnmodifiableMap(
                ChannelProvider::channel,
                Function.identity()
            ));
    }

    public ChannelProvider get(CommunicationChannel channel) {
        ChannelProvider provider = providers.get(channel);
        if (provider == null) {
            throw new IllegalStateException("No provider for channel " + channel);
        }
        return provider;
    }
}
```

No switch/if in worker.

---

# 24. Mock provider behavior

Один общий component отвечает за failure injection.

```java
@Component
public class MockDeliveryBehavior {

    private final AtomicLong calls = new AtomicLong();
    private final int failEvery;

    public ProviderSendResult next(CommunicationChannel channel) {
        long call = calls.incrementAndGet();

        if (failEvery > 0 && call % failEvery == 0) {
            return ProviderSendResult.retryable(
                "MOCK_TEMPORARY_ERROR",
                "Synthetic retryable error"
            );
        }

        return ProviderSendResult.success(
            "mock-" + channel.name().toLowerCase() + "-" + UUID.randomUUID()
        );
    }
}
```

Config:

```yaml
collectra:
  communication:
    mock:
      fail-every: 10
```

Production profile для mock delivery должен либо быть disabled, либо иметь `fail-every: 0`.

---

# 25. MockEmailProvider

```java
@Component
@ConditionalOnProperty(
    name = "collectra.communication.provider.email",
    havingValue = "mock",
    matchIfMissing = true
)
public class MockEmailProvider implements ChannelProvider {

    private final MockDeliveryBehavior behavior;

    @Override
    public CommunicationChannel channel() {
        return CommunicationChannel.EMAIL;
    }

    @Override
    public ProviderSendResult send(ProviderSendCommand command) {
        return behavior.next(channel());
    }
}
```

Остальные mock adapters после EMAIL могут быть такими же тонкими.

---

# 26. Retry policy

Config:

```yaml
collectra:
  communication:
    retry:
      max-attempts: 5
      delays:
        - PT1M
        - PT5M
        - PT15M
        - PT30M
```

```java
@Component
@ConfigurationProperties("collectra.communication.retry")
public class CommunicationRetryProperties {
    private int maxAttempts = 5;
    private List<Duration> delays = ...;
}
```

Policy:

```java
public Duration delayForAttempt(int attemptCount) {
    int index = Math.max(0, attemptCount - 1);
    return delays.get(Math.min(index, delays.size() - 1));
}
```

---

# 27. Result processing

## SUCCESS

```text
PROCESSING -> SENT
sentCount++
```

## PERMANENT_ERROR

```text
PROCESSING -> FAILED
failedCount++
```

## RETRYABLE_ERROR and attempts remain

```text
PROCESSING -> RETRY_WAIT
retryCount++
nextRetryAt = now + backoff
```

## RETRYABLE_ERROR and maxAttempts reached

```text
PROCESSING -> FAILED
failedCount++
```

Не увеличивать `failedCount` при промежуточном retryable error.

---

# 28. MessageRetryScheduler

Scheduler выбирает due `RETRY_WAIT` messages небольшими batches.

```java
@Component
public class MessageRetryScheduler {

    private final MessageRetryService retries;

    @Scheduled(fixedDelayString = "${collectra.communication.retry-scan-ms:5000}")
    public void requeueDue() {
        retries.requeueDue();
    }
}
```

Application service выбирает due rows с `FOR UPDATE SKIP LOCKED`:

```java
@Transactional
public int requeueDue() {
    Instant now = Instant.now(clock);

    List<Message> due = messages.findRetryDueForUpdate(now, 100);

    int count = 0;
    for (Message message : due) {
        message.requeue();
        appendDeliveryEvent(message);
        count++;
    }
    return count;
}
```

Для каждого successful `RETRY_WAIT -> QUEUED` создать новый OutboxEvent `MESSAGE_DELIVERY_REQUESTED` в той же transaction.

Не делать отдельную Rabbit retry topology в v1.

---

# 29. Atomic retry claim and processing recovery

Чтобы два scheduler instance не переочередили один message дважды, `requeueOne` должен использовать conditional update либо pessimistic lock.

Preferred:

```sql
update messages
set status = 'QUEUED',
    next_retry_at = null
where id = :id
  and status = 'RETRY_WAIT'
  and next_retry_at <= :now
```

Если affected rows = 1 -> append Outbox event.

Binding вариант после code audit — выбирать batch через `FOR UPDATE SKIP LOCKED` и
в той же transaction делать entity transition + Outbox append. Это сохраняет
auditing/version и не даёт двум scheduler instances обработать одну строку.

Также обязателен recovery зависших `PROCESSING`: выбирать rows, у которых
`processing_started_at < now - processingTimeout`, через `FOR UPDATE SKIP LOCKED`.
Если attempts остались — переводить в `RETRY_WAIT` с `nextRetryAt=now`; иначе в
`FAILED`. Полный SQL и counter rules находятся в code audit.

---

# 30. Eligibility integration

Сейчас `CampaignEligibilityService` проверяет:

- active customer;
- active email matching snapshot destination;
- unpaid invoice.

Для EMAIL phase этот код подходит.

При добавлении других каналов eligibility/contact resolution надо расширять отдельно.

Не делать сейчас generic `ContactStrategy` abstraction только ради будущих каналов.

Когда реально добавится SMS, тогда выделить:

```text
CustomerContactResolver
```

если станет видно реальное дублирование.

---

# 31. CampaignRecipient status

Current model:

```text
SNAPSHOT
ELIGIBLE
SKIPPED
```

Для delivery core не нужно добавлять `SENT/FAILED` в CampaignRecipient.

Результат доставки живёт в `Message`.

CampaignRecipient отвечает только за snapshot + eligibility.

Это предотвращает дублирование статусов между двумя aggregates.

---

# 32. CampaignRun lifecycle

Предлагаемый flow:

```text
PREPARING
   -> READY
   -> RUNNING
   -> COMPLETED
```

Failure/cancel остаются существующими.

`RUNNING` устанавливается при `CampaignDeliveryService.start()`.

`COMPLETED` — когда:

```text
sentCount + failedCount + skippedCount == recipientCount
```

Для run с `recipientCount == 0` допустимо завершить его сразу после start.

---

# 33. API changes

## Start delivery

```http
POST /api/v1/campaigns/runs/{runId}/deliver
```

Response:

```json
{
  "runId": "...",
  "recipients": 100,
  "eligible": 92,
  "skipped": 8,
  "messagesCreated": 92
}
```

Endpoint должен быть idempotent enough: повторный вызов не создаёт duplicate Message благодаря unique constraint.

Но повторный `start` для уже RUNNING/COMPLETED run должен возвращать conflict/business error, а не запускать orchestration заново.

---

# 34. Message API

Минимально:

```http
GET /api/v1/messages/{id}
GET /api/v1/messages?campaignRunId=...
```

Дополнительные filters только реально нужные:

```text
status
channel
customerId
campaignId
```

Не вводить Specification DSL.

---

# 35. Observability/logging

Worker log минимум:

```text
messageId
campaignRunId
channel
attemptCount
result
errorCode
```

Не логировать full body или чувствительные destination данные без необходимости.

Пример:

```java
log.info(
    "Message delivery finished. messageId={}, runId={}, channel={}, attempt={}, result={}",
    message.getId(),
    message.getCampaignRunId(),
    message.getChannel(),
    message.getAttemptCount(),
    result.status()
);
```

---

# 36. Integration tests — обязательный набор

## Test 1 — success

```text
prepare campaign
start delivery
Message QUEUED
Outbox event exists
worker handles event
MockEmailProvider SUCCESS
Message SENT
CampaignRun.sentCount = 1
CampaignRun COMPLETED
```

## Test 2 — paid before send

```text
prepare
allocate payment
start delivery / eligibility recheck
recipient SKIPPED/PAID
Message absent
CampaignRun.skippedCount = 1
CampaignRun COMPLETED
```

## Test 3 — retryable then success

Deterministic provider:

```text
attempt1 RETRYABLE_ERROR
attempt2 SUCCESS
```

Assert:

```text
Message RETRY_WAIT after attempt1
attemptCount = 1
nextRetryAt != null
CampaignRun.failedCount = 0
CampaignRun.retryCount = 1

retry scheduler -> QUEUED + outbox
worker -> SENT
attemptCount = 2
sentCount = 1
failedCount = 0
```

## Test 4 — retry exhausted

```text
all attempts RETRYABLE_ERROR
```

Assert final:

```text
Message FAILED
attemptCount = maxAttempts
failedCount = 1
```

## Test 5 — permanent failure

```text
PERMANENT_ERROR
```

Assert:

```text
FAILED immediately
no nextRetryAt
retryCount unchanged
failedCount = 1
```

## Test 6 — duplicate Rabbit event

Call worker twice with same `messageId`.

Expected:

```text
provider called once after first successful SENT
second claim returns 0
sentCount remains 1
```

## Test 7 — concurrent claim

Two threads try claim same QUEUED message.

Expected:

```text
one updated row total
one provider send
```

## Test 8 — retry scheduler duplicate protection

Two scheduler invocations see same retry due candidate.

Expected:

```text
only one RETRY_WAIT -> QUEUED transition
only one new outbox event
```

---

# 37. Unit tests

Отдельно покрыть:

```text
Message state transitions
CommunicationRetryPolicy
ChannelProviderRegistry missing provider
MockDeliveryBehavior fail-every
CampaignRun completion formula
```

---

# 38. Deterministic test provider

Не завязывать integration tests на `every 10th`.

Для tests сделать stub/scripted behavior:

```java
@TestConfiguration
class CommunicationTestConfig {

    @Bean
    @Primary
    ChannelProvider testEmailProvider() {
        return new ScriptedEmailProvider(
            RETRYABLE_ERROR,
            SUCCESS
        );
    }
}
```

Либо programmable bean с queue результатов.

`fail-every=10` нужен для dev/demo, а не для deterministic tests.

---

# 39. Liquibase work

Создать changeSet для:

1. `messages` table;
2. indexes/unique constraint;
3. new counters in `campaign_runs`.

Использовать существующий каталог:

```text
src/main/resources/db/changelog/changes/
```

и добавить include в:

```text
src/main/resources/db/changelog/db.changelog-master.yaml
```

Не переходить на Flyway.

---

# 40. Recommended implementation sequence

## Task A — Message domain + DB

- Liquibase messages;
- Message entity;
- enums;
- repository;
- state transition unit tests.

## Task B — CampaignRun counters

- migration columns;
- fields/getters;
- atomic increment queries;
- completion rule.

## Task C — Campaign -> Message orchestration

- CampaignDeliveryService;
- eligibility integration;
- MessageService;
- template render snapshot;
- Message + Outbox transactional creation.

## Task D — Rabbit routing + worker

- CommunicationMessagingConfig;
- OutboxEventRouter branch;
- event DTO;
- MessageDeliveryWorker;
- claim logic.

## Task E — Provider abstraction + MockEmail

- ChannelProvider;
- registry;
- result model;
- mock behavior;
- MockEmailProvider.

## Task F — Retry

- retry properties/policy;
- RETRY_WAIT handling;
- scheduler;
- conditional requeue;
- outbox re-publication.

## Task G — Statistics / completion

- sent/failed/skipped/retry counters;
- atomic updates;
- CampaignRun completion.

## Task H — Tests

- PostgreSQL/Testcontainers integration tests;
- duplicate worker event;
- retry scheduler idempotency;
- deterministic provider behavior.

## Task I — remaining mock channels

Only after EMAIL path is stable:

```text
MockSmsProvider
MockWhatsAppProvider
MockTelegramProvider
MockInAppProvider
```

No extra workers/queues.

---

# 41. Definition of Done

Communication / Delivery Core v1 считается готовым, когда:

1. `Message` persisted model существует.
2. Message создаётся только для ELIGIBLE CampaignRecipient.
3. PAID/NO_CONTACT/etc recipient не создаёт Message.
4. Message + Outbox сохраняются в одной transaction.
5. `MESSAGE_DELIVERY_REQUESTED` публикуется существующим shared Outbox.
6. Одна Rabbit queue принимает все communication messages.
7. Один `MessageDeliveryWorker` обслуживает все channels.
8. Worker atomically claims QUEUED Message.
9. Duplicate Rabbit event не приводит к duplicate provider send.
10. Provider выбирается через registry.
11. MockEmailProvider работает.
12. SUCCESS -> SENT.
13. RETRYABLE_ERROR -> RETRY_WAIT.
14. retry scheduler возвращает due message в QUEUED и создаёт Outbox event.
15. max attempts -> FAILED.
16. PERMANENT_ERROR -> FAILED без retry.
17. CampaignRun sent/failed/skipped/retry counters обновляются atomically.
18. Retryable intermediate failure НЕ увеличивает failedCount.
19. CampaignRun становится COMPLETED при terminal outcome всех recipients.
20. integration tests покрывают success, paid skip, retry success, exhausted, permanent, duplicate delivery.
21. Нет отдельного worker/queue/outbox/framework на каждый канал.

---

# 42. Основной архитектурный принцип

Communication Core не должен превращаться в отдельную платформу внутри Collectra.

Для v1 достаточно:

```text
CampaignRecipient
  -> Message
  -> shared Outbox
  -> RabbitMQ
  -> one Worker
  -> ChannelProvider
  -> Message status
  -> CampaignRun aggregate result
```

Все следующие усложнения должны появляться только после реальной provider/business необходимости.
