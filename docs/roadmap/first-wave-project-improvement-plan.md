# Collectra — первая волна доработки проекта

**Статус:** Architecture Baseline v2 / implementation-oriented review  
**Назначение:** рабочее ТЗ первой волны, детализированное до уровня пакетов, сущностей, сервисов, API, миграций и тестов с учетом уже существующего кода Collectra.

> Основной принцип: **не переписывать то, что уже работает; расширять существующие модули минимально необходимыми изменениями.**

---

# 1. Цель первой волны

Довести существующую платформенную основу Collectra до рабочего business flow:

```text
Import / API
    ↓
Customer
    ↓
Invoice / Payment
    ↓
Campaign
    ↓
Recipient snapshot
    ↓
Eligibility recheck
    ↓
Existing TemplateVersion + Builder
    ↓
Logo / Image / QR / placeholders
    ↓
CommunicationMessage snapshot
    ↓
Existing Outbox
    ↓
RabbitMQ
    ↓
Communication Worker
    ↓
EMAIL Provider
    ↓
MessageDelivery
```

Первая волна должна закончиться реальным сценарием:

```text
загрузить/создать Customer + Invoice
→ выбрать получателя
→ сформировать персонализированный HTML
→ добавить logo/image/QR
→ отправить EMAIL
→ увидеть результат доставки
```

---

# 2. Результат ревью текущего кода

## 2.1. Уже существует — не создавать заново

В проекте уже есть готовые части, которые должны быть переиспользованы.

### Template subsystem

Существуют:

```text
io.collectra.api.template.domain.DocumentTemplate
io.collectra.api.template.domain.TemplateVersion
io.collectra.api.template.domain.TemplateAsset
io.collectra.api.template.domain.FieldDefinition
io.collectra.api.template.application.FieldCatalogService
io.collectra.api.template.application.PlaceholderScanner
io.collectra.api.template.application.TemplateBuilderService
io.collectra.api.template.application.TemplateBuilderDocumentCompiler
io.collectra.api.template.application.TemplateAssetService
```

`TemplateVersion` уже поддерживает:

```text
DRAFT
→ VALIDATED
→ PUBLISHED
→ ARCHIVED
```

и уже содержит:

```text
locale
channel
subject
builderJson
contentHtml
stylesheet
```

**Решение:** новую сущность `Template`/`TemplateVersion` не создавать. Расширять существующий template module.

### File subsystem

Существуют:

```text
io.collectra.api.file.application.FileService
FileRetentionPolicy
FileCleanupService
FileMetadata
UploadFileCommand
```

**Решение:** отдельное физическое media storage не создавать. Галерея изображений работает поверх существующего `TemplateAsset + FileService`.

### Import subsystem

Уже существуют:

```text
ImportBatch
MappingProfile
MappingRule
SourceSchema
SourceField
CsvInputParser
ExcelInputParser
JsonInputParser
DocumentInputParser
MappingExecutionService
ImportBatchProcessingService
```

**Решение:** не писать новые CSV/Excel/JSON parsers. Добавить последний шаг persistence в business entities.

### Shared Outbox

Уже существуют:

```text
OutboxEvent
OutboxService
OutboxPublisher
OutboxClaimService
OutboxStateService
OutboxRetryPolicy
OutboxRepository
OutboxEventRouter
```

**Решение:** отдельный outbox для communication не создавать. Использовать существующий `shared.outbox` с новым event type/route.

### Tenant context

Уже существуют:

```text
TenantContext
TenantContextFilter
MissingTenantException
```

**Решение:** новые business services должны получать tenant через существующий механизм, а не через пользовательский request parameter.

### Document/PDF

Уже существуют:

```text
GenerationJob
GenerationJobService
DocumentGenerationWorker
PdfRenderer
GeneratedDocument
GeneratedOutputService
```

**Решение:** PDF generation не проектировать вторым независимым pipeline. Новая коммуникационная функциональность должна передавать подготовленный HTML в существующий document module.

---

# 3. Что реально требуется добавить

Новые business packages:

```text
io.collectra.api.customer
io.collectra.api.receivable
io.collectra.api.campaign
io.collectra.api.communication
```

`customer` и `communication` уже имеют package placeholders — использовать их.

Стандартная структура модулей должна соответствовать существующему проекту:

```text
<module>/
├── api
├── application
├── domain
└── infrastructure
```

Не вводить отдельные hexagonal ports/adapters слои поверх уже принятой структуры.

---

# 4. Customer

## 4.1. Сущности

Пакет:

```text
io.collectra.api.customer.domain
```

Добавить:

```text
Customer
CustomerEmail
CustomerPhone
CustomerSegment
CustomerSegmentMember
CustomerStatus
CustomerType
ContactStatus
```

### Customer

Минимальная модель:

```java
@Entity
@Table(
    name = "customers",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_customer_tenant_external",
        columnNames = {"tenant_id", "external_id"}
    )
)
public class Customer extends AuditableEntity {
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "external_id", nullable = false, length = 120)
    private String externalId;

    @Enumerated(EnumType.STRING)
    private CustomerType customerType;

    private String displayName;
    private String firstName;
    private String lastName;
    private String middleName;
    private String companyName;

    @Enumerated(EnumType.STRING)
    private CustomerStatus status;

    private UUID managerUserId;
    private String preferredLocale;
    private String timezone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", columnDefinition = "jsonb")
    private JsonNode customFields;
}
```

Не добавлять `segmentId` прямо в Customer. Один Customer может иметь несколько сегментов.

### CustomerEmail

```text
id
tenantId
customerId
email
type
primary
verified
status
```

### CustomerPhone

```text
id
tenantId
customerId
phone
normalizedPhone
type
primary
verified
status
```

На первой волне **не добавлять отдельную сложную consent subsystem**. Для SMS/WhatsApp правила согласий будут отдельным ТЗ при подключении этих каналов.

### CustomerSegment

```text
CustomerSegment
id, tenantId, code, name, description, active

CustomerSegmentMember
id, tenantId, customerId, segmentId
```

Уникальности:

```text
(tenant_id, code)
(customer_id, segment_id)
```

## 4.2. Application layer

Добавить:

```text
CustomerService
CustomerQueryService
CustomerContactService
CustomerSegmentService
```

Не создавать отдельный service на каждую CRUD-операцию.

Репозитории:

```text
CustomerRepository
CustomerEmailRepository
CustomerPhoneRepository
CustomerSegmentRepository
CustomerSegmentMemberRepository
```

Все repository methods для tenant-owned данных должны включать `tenantId`.

Пример:

```java
Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);
Optional<Customer> findByTenantIdAndExternalId(UUID tenantId, String externalId);
```

## 4.3. API

```http
POST   /api/v1/customers
GET    /api/v1/customers/{id}
GET    /api/v1/customers
PUT    /api/v1/customers/{id}
PATCH  /api/v1/customers/{id}/status

POST   /api/v1/customers/{id}/emails
DELETE /api/v1/customers/{id}/emails/{emailId}
POST   /api/v1/customers/{id}/phones
DELETE /api/v1/customers/{id}/phones/{phoneId}

POST   /api/v1/customer-segments
GET    /api/v1/customer-segments
POST   /api/v1/customers/{id}/segments/{segmentId}
DELETE /api/v1/customers/{id}/segments/{segmentId}
```

Поиск первой версии:

```http
GET /api/v1/customers?externalId=C001
GET /api/v1/customers?segmentId=<uuid>
GET /api/v1/customers?managerUserId=<uuid>
```

Не делать universal dynamic filtering engine.

---

# 5. Receivable

Пакет:

```text
io.collectra.api.receivable
```

Добавить:

```text
domain/Invoice.java
domain/Payment.java
domain/PaymentAllocation.java
domain/InvoicePaymentStatus.java
application/InvoiceService.java
application/PaymentService.java
infrastructure/InvoiceRepository.java
infrastructure/PaymentRepository.java
infrastructure/PaymentAllocationRepository.java
api/InvoiceController.java
api/PaymentController.java
```

## 5.1. Invoice

```text
id
tenantId
customerId
externalId
invoiceNumber
invoiceDate
dueDate
originalAmount
paidAmount
outstandingAmount
currency
paymentStatus
documentFileId nullable
customFields jsonb
```

Уникальность:

```text
(tenant_id, external_id)
```

`OVERDUE` не хранить как payment status.

Определение просрочки:

```text
dueDate < today && outstandingAmount > 0
```

## 5.2. Payment и PaymentAllocation

Не привязывать Payment напрямую к одному Invoice.

```text
Payment
id
tenantId
customerId
externalId
paymentDate
amount
currency
paymentReference
source
customFields jsonb
```

```text
PaymentAllocation
id
tenantId
paymentId
invoiceId
amount
```

Это позволяет без дальнейшей переделки поддержать:

```text
1 payment → N invoices
N payments → 1 invoice
```

`Invoice.paidAmount` и `outstandingAmount` обновляются внутри `PaymentService` после изменения allocations.

На первой волне не создавать отдельный reconciliation engine.

## 5.3. API

```http
POST /api/v1/invoices
GET  /api/v1/invoices/{id}
GET  /api/v1/invoices

POST /api/v1/payments
GET  /api/v1/payments/{id}
POST /api/v1/payments/{paymentId}/allocations
```

Для удобства допускается shortcut:

```http
POST /api/v1/invoices/{invoiceId}/payments
```

Он внутри создает `Payment + PaymentAllocation`.

---

# 6. Import persistence

Существующий pipeline parser/mapping сохраняется.

Необходимо добавить компонент:

```text
io.collectra.api.importing.application.BusinessRecordPersistenceService
```

Его ответственность:

```text
Mapped record
→ определить target object
→ validation
→ Customer/Invoice/Payment service
→ CREATED / UPDATED / ERROR
```

Не обращаться к repositories business modules напрямую из parser classes.

Целевые поля mapping первой версии:

```text
customer.externalId
customer.displayName
customer.firstName
customer.lastName
customer.companyName
customer.email
customer.phone
customer.preferredLocale

invoice.externalId
invoice.invoiceNumber
invoice.invoiceDate
invoice.dueDate
invoice.originalAmount
invoice.currency

payment.externalId
payment.paymentDate
payment.amount
payment.currency
payment.invoiceExternalId
```

Все прочие разрешенные входные поля:

```text
custom.customer.*
custom.invoice.*
custom.payment.*
```

Upsert выполняется существующим ImportBatch flow по tenant-scoped business key.

Ошибка одной строки не должна откатывать успешные строки batch.

Не добавлять новый MappingProfile/MappingRule механизм — он уже существует.

---

# 7. Template / Media / QR — только расширение существующего модуля

## 7.1. TemplateVersion

Ничего нового вместо `TemplateVersion` не создавать.

Текущая модель уже подходит:

```text
channel
locale
subject
builderJson
contentHtml
stylesheet
status
```

PUBLISHED/ARCHIVED версии не изменять. Изменения делаются новой draft version через существующий template service.

## 7.2. Placeholder catalog

Новый `PlaceholderRegistry` не создавать.

Расширить существующие:

```text
FieldDefinition
FieldCatalogService
PlaceholderScanner
```

Добавить canonical fields:

```text
customer.externalId
customer.displayName
customer.preferredLocale
invoice.externalId
invoice.invoiceNumber
invoice.dueDate
invoice.originalAmount
invoice.outstandingAmount
invoice.currency
invoice.documentUrl
payment.externalId
manager.displayName
```

и разрешить существующий custom namespace.

Один механизм placeholder resolution должен использоваться для:

```text
subject
HTML
button URL
image dynamic URL
QR value
PDF
```

## 7.3. Media Gallery

Новую таблицу `media_assets` **не создавать**.

Использовать существующий:

```text
TemplateAsset
├── tenantId
├── assetKey
├── fileId
├── altText
└── status
```

Галерея — это API/UI представление списка `TemplateAsset`.

Добавить при необходимости только поля, реально требуемые UI:

```text
displayName nullable
assetType nullable (LOGO / IMAGE / BANNER / ICON)
```

`mimeType`, `size`, storage key брать из `FileService/FileMetadata`, не дублировать в `TemplateAsset`.

Для загруженных картинок:

```text
UI
→ FileService upload
→ TemplateAsset(fileId)
```

Для внешнего URL отдельная постоянная запись в gallery не обязательна. URL хранится в builder block.

## 7.4. Builder blocks

В существующий `builderJson` добавить/поддержать блоки:

```text
IMAGE
QR_CODE
BUTTON
```

`LOGO` можно считать `IMAGE` с семантическим `assetType=LOGO`; отдельная Java hierarchy не нужна.

Пример IMAGE из gallery:

```json
{
  "type": "IMAGE",
  "sourceType": "ASSET",
  "assetKey": "company-logo",
  "width": 180,
  "alt": "Company logo"
}
```

Пример external image:

```json
{
  "type": "IMAGE",
  "sourceType": "URL",
  "value": "https://client.kz/logo.png"
}
```

Пример dynamic URL:

```json
{
  "type": "IMAGE",
  "sourceType": "DYNAMIC_URL",
  "value": "https://cdn.client.kz/customer/{{customer.externalId}}/logo.png"
}
```

## 7.5. QR

QR хранится как block config, а не как FileEntity.

```json
{
  "type": "QR_CODE",
  "value": "https://pay.client.kz/invoice/{{invoice.externalId}}",
  "size": 200
}
```

Pipeline:

```text
value
→ existing placeholder resolver
→ resolved String
→ QrCodeGenerator
→ data URI / temporary render resource
→ compiled HTML
```

Добавить один простой application component:

```java
public interface QrCodeGenerator {
    String toDataUri(String value, int size);
}
```

Первая реализация может использовать ZXing.

Не создавать QR repository/table/cache на первом этапе.

Если required placeholder не разрешился — template preview/render завершается validation error.

## 7.6. External resources

Добавить только если builder разрешает URL/DYNAMIC_URL:

```text
ExternalImageResolver
```

Обязан:

```text
HTTP(S) only
connection/read timeout
max size
image MIME validation
SSRF protection
```

На первом этапе достаточно in-memory/cache abstraction существующего Spring cache при необходимости; отдельный distributed cache не вводить.

---

# 8. Campaign

Пакет:

```text
io.collectra.api.campaign
```

Для первой версии не вводить полноценный recurring campaign scheduler.

Добавить:

```text
Campaign
CampaignRecipient
CampaignStatus
CampaignRecipientStatus
CampaignSelection
CampaignService
CampaignRecipientService
CampaignRepository
CampaignRecipientRepository
CampaignController
```

## 8.1. Campaign

```text
id
tenantId
name
status
templateVersionId
channel
scheduledAt nullable
selectionCriteria jsonb
createdBy
createdAt
preparedAt
launchedAt
completedAt
```

На первой волне одна Campaign использует один `channel + templateVersionId`.

Мультиканальная Campaign — later. Это сознательное упрощение.

Статусы:

```text
DRAFT
PREPARING
READY
RUNNING
COMPLETED
CANCELLED
FAILED
```

Переходы:

```text
DRAFT → PREPARING → READY → RUNNING → COMPLETED
DRAFT/READY → CANCELLED
RUNNING → COMPLETED/FAILED
```

## 8.2. Selection DTO

Не создавать query DSL.

```java
public record CampaignSelection(
    Set<UUID> customerIds,
    Set<UUID> segmentIds,
    Integer daysOverdueFrom,
    Integer daysOverdueTo,
    BigDecimal amountFrom,
    BigDecimal amountTo
) {}
```

## 8.3. Recipient snapshot

```text
CampaignRecipient
id
tenantId
campaignId
customerId
invoiceId nullable
destination
locale
status
skipReason nullable
createdAt
```

`prepare()` фиксирует получателей.

Перед фактическим созданием Message выполнить легкий `EligibilityService`:

```text
customer active?
contact exists?
invoice still outstanding?
```

Если условие уже не выполняется:

```text
status = SKIPPED
skipReason = PAID | NO_CONTACT | CUSTOMER_INACTIVE
```

Не пересобирать snapshot целиком.

---

# 9. Communication

Пакет уже существует как placeholder:

```text
io.collectra.api.communication
```

Добавить:

```text
domain/CommunicationMessage.java
domain/MessageDelivery.java
domain/MessageStatus.java
domain/DeliveryStatus.java
application/MessagePreparationService.java
application/MessageDeliveryService.java
application/ChannelSender.java
application/EmailChannelSender.java
infrastructure/CommunicationMessageRepository.java
infrastructure/MessageDeliveryRepository.java
infrastructure/CommunicationListener.java
api/DeliveryCallbackController.java  // только если provider поддерживает callback
```

## 9.1. CommunicationMessage

```text
id
tenantId
campaignId
recipientId
customerId
invoiceId nullable
channel
destination
templateVersionId
renderedSubject
renderedBody
renderContextSnapshot jsonb
status
renderedAt
createdAt
```

Ключевое правило:

> Worker отправляет **уже подготовленный immutable message** и не выполняет повторный business rendering.

Это гарантирует, что retry отправит тот же content.

## 9.2. MessagePreparationService

```text
CampaignRecipient
→ eligibility recheck
→ load TemplateVersion(PUBLISHED)
→ build RenderContext
→ resolve placeholders
→ resolve TemplateAsset
→ generate QR
→ compile final HTML
→ save CommunicationMessage
→ save MessageDelivery(PENDING)
→ OutboxService.add(...)
```

Создание Message + Delivery + OutboxEvent выполняется в одной `@Transactional` операции.

## 9.3. Delivery

```text
MessageDelivery
id
tenantId
messageId
attempt
provider
providerMessageId nullable
status
requestedAt
sentAt
deliveredAt
failedAt
errorCode
errorMessage
```

Статусы:

```text
PENDING
PROCESSING
SENT
DELIVERED
FAILED
BOUNCED
REJECTED
```

`providerResponse` целиком не хранить без необходимости; для диагностики достаточно code/message/providerMessageId. Большие ответы provider не нужны.

---

# 10. Outbox + RabbitMQ

Использовать существующий `io.collectra.api.shared.outbox`.

Добавить route/event type:

```text
COMMUNICATION_SEND_REQUESTED
```

Payload минимальный:

```json
{
  "messageId": "uuid",
  "tenantId": "uuid"
}
```

Не передавать в RabbitMQ:

```text
HTML
PDF
images
attachments bytes
```

Communication listener:

```text
messageId
→ load immutable CommunicationMessage
→ claim/update Delivery
→ EmailChannelSender
→ provider
→ update Delivery
```

Outbox retry остается существующим.

Delivery retry реализовать отдельно только для retryable ошибок:

```text
timeout
connection failure
provider 5xx
```

Не retry:

```text
invalid destination
render error
missing recipient
provider 4xx validation
```

Конкретные интервалы вынести в configuration; не зашивать в domain.

---

# 11. EMAIL channel

Первый и единственный канал первой реализации.

Контракт:

```java
public interface ChannelSender {
    TemplateChannel channel();
    SendResult send(CommunicationMessage message);
}
```

```java
@Component
public class EmailChannelSender implements ChannelSender {
    private final EmailProvider emailProvider;
}
```

Provider abstraction:

```java
public interface EmailProvider {
    EmailSendResult send(EmailRequest request);
}
```

Первая реализация может использовать SMTP/Spring Mail согласно выбранной конфигурации проекта.

Не проектировать SES/SendGrid/Mailgun implementations заранее.

---

# 12. PDF и attachments

Не создавать новый PDF worker.

Переиспользовать существующие:

```text
GenerationJobService
DocumentGenerationWorker
PdfRenderer
GeneratedDocument
```

Связь:

```text
resolved HTML
→ existing GenerationJob
→ PDF
→ existing FileService/DocumentStorage
→ generated file id
→ CommunicationMessage attachment reference
```

Если EMAIL без PDF работает — PDF не должен блокировать первый end-to-end slice.

---

# 13. Миграции БД

Проект использует существующий Liquibase changelog в:

```text
src/main/resources/db/changelog
```

Добавлять migrations в принятом проектом формате.

Новые таблицы первой волны:

```text
customers
customer_emails
customer_phones
customer_segments
customer_segment_members
invoices
payments
payment_allocations
campaigns
campaign_recipients
communication_messages
message_deliveries
```

`template_assets` и `template_versions` повторно не создавать.

Основные индексы:

```text
customers(tenant_id, external_id) UNIQUE
customers(tenant_id, status)
customer_emails(tenant_id, customer_id)
customer_phones(tenant_id, customer_id)
invoices(tenant_id, external_id) UNIQUE
invoices(tenant_id, customer_id)
invoices(tenant_id, due_date)
payments(tenant_id, external_id) UNIQUE
payment_allocations(payment_id)
payment_allocations(invoice_id)
campaign_recipients(tenant_id, campaign_id, status)
communication_messages(tenant_id, campaign_id)
message_deliveries(message_id, attempt)
message_deliveries(provider_message_id)
```

Не добавлять индексы на каждый столбец.

---

# 14. Транзакционные границы

Использовать короткие application transactions.

Примеры:

```text
CustomerService.create/update      → одна transaction
PaymentService.allocate            → payment allocation + recalc invoice
CampaignService.prepare            → campaign + recipient snapshot
MessagePreparationService.prepare  → message + delivery + outbox
```

HTTP provider call **не выполнять внутри транзакции подготовки Message**.

Worker:

```text
transaction 1: claim Delivery
HTTP send outside DB transaction
transaction 2: save provider result
```

---

# 15. API errors

Использовать существующий shared error handling проекта.

Не вводить новый error envelope.

Новые error codes по необходимости:

```text
CUSTOMER_NOT_FOUND
CUSTOMER_EXTERNAL_ID_EXISTS
INVOICE_NOT_FOUND
PAYMENT_NOT_FOUND
PAYMENT_ALLOCATION_EXCEEDS_AMOUNT
CAMPAIGN_NOT_FOUND
CAMPAIGN_INVALID_STATE
CAMPAIGN_RECIPIENT_NOT_ELIGIBLE
TEMPLATE_REQUIRED_VALUE_MISSING
TEMPLATE_ASSET_NOT_FOUND
COMMUNICATION_MESSAGE_NOT_FOUND
DELIVERY_NOT_RETRYABLE
```

---

# 16. Тестирование

Для каждого PR обязательны не только unit tests, но и несколько integration tests на реальные границы.

Минимальный набор:

```text
Customer
- tenant A не видит customer tenant B
- duplicate externalId внутри tenant запрещен
- один externalId в разных tenant разрешен

Receivable
- partial payment уменьшает outstanding
- несколько payments на invoice
- один payment распределяется на несколько invoices

Import
- valid row creates/updates business entity
- invalid row не откатывает соседние successful rows

Template
- existing placeholder engine resolves customer/invoice fields
- IMAGE asset from TemplateAsset works
- QR resolves URL + {{placeholder}}
- unresolved required QR placeholder returns validation error

Campaign
- prepare creates snapshot
- fully paid invoice before send → recipient SKIPPED

Communication
- prepare saves Message + Delivery + Outbox atomically
- retry не перерендеривает body
- duplicate Rabbit delivery не создает второй Message
```

---

# 17. Порядок реализации без лишнего усложнения

## PR 1 — Customer + Receivable Core

```text
feature/customer-receivable-core
```

Добавить:

```text
Customer / contacts / segments
Invoice / Payment / PaymentAllocation
repositories
services
REST API
Liquibase
integration tests
```

Не трогать Campaign/Email.

## PR 2 — Import Business Persistence

```text
feature/import-business-persistence
```

Только соединить существующие:

```text
ImportBatch + MappingExecutionService
```

с:

```text
CustomerService / InvoiceService / PaymentService
```

Не переписывать parsers и mapping subsystem.

## PR 3 — Template Media + QR

```text
feature/template-media-assets
```

Расширить существующий template module:

```text
TemplateAsset gallery API
IMAGE/LOGO configuration
DYNAMIC_URL
QR_CODE
canonical customer/invoice fields in FieldCatalogService
preview tests
```

Не создавать новый template engine и новую media storage subsystem.

## PR 4 — Campaign Core

```text
feature/campaign-core
```

```text
Campaign
CampaignRecipient
selection DTO
snapshot
eligibility recheck
```

## PR 5 — Communication Email Vertical Slice

```text
feature/communication-email
```

```text
CommunicationMessage
MessageDelivery
MessagePreparationService
existing Outbox integration
Rabbit listener
ChannelSender
EmailProvider
EmailChannelSender
retry/idempotency
```

Результат PR 5 — реальная end-to-end EMAIL отправка.

## PR 6 — PDF Attachments

```text
feature/communication-pdf-attachments
```

Подключить существующий document generation pipeline к CommunicationMessage.

---

# 18. Что сознательно НЕ делать в первой волне

Чтобы не усложнять проект, сейчас не вводить:

```text
microservices split
Kafka
новый Outbox
новый FileService
новый Template engine
новый Import parser subsystem
universal query DSL
workflow/BPM engine
recurring CampaignRun model
multi-channel campaign orchestration
separate consent service
distributed render cache
QR persistence
payment reconciliation engine
complex rule engine
```

Эти элементы добавляются только после появления подтвержденного business requirement.

---

# 19. Definition of Done первой волны

Первая волна считается завершенной, когда тестовый tenant может выполнить:

```text
1. Создать или импортировать Customer.
2. Создать/import Invoice.
3. Провести partial/full Payment.
4. Создать EMAIL TemplateVersion существующим template module.
5. Добавить изображение из TemplateAsset gallery.
6. Добавить QR:
   https://pay.client.kz/{{invoice.externalId}}
7. Создать Campaign и подготовить recipient snapshot.
8. Перед отправкой повторно проверить актуальность задолженности.
9. Сформировать immutable CommunicationMessage.
10. В той же DB transaction создать Delivery + Outbox event.
11. Существующий Outbox публикует messageId в RabbitMQ.
12. Communication Worker отправляет EMAIL.
13. MessageDelivery хранит результат.
14. Retry отправляет тот же rendered content, а не пересобирает его.
```

После отправки система должна позволять определить:

```text
кому отправлено
по какому invoice
какой TemplateVersion использован
какой subject/body фактически отправлен
какие assets использованы
какое значение было закодировано в QR
когда произошла отправка
каков статус delivery
какова последняя ошибка, если отправка неуспешна
```

---

# 20. Итоговое архитектурное решение

Целевая первая волна остается **модульным монолитом** внутри существующего `collectra-api`:

```text
customer ───────┐
receivable ─────┼──→ campaign ─→ communication ─→ EMAIL
importing ──────┘          │             │
                           │             └→ existing shared.outbox
existing template ─────────┤
existing file ─────────────┤
existing document ─────────┘
```

Главное правило реализации:

> **Новые business modules добавляются поверх уже готовой платформенной инфраструктуры Collectra. Дублирование Template, File, Import, Outbox и PDF подсистем не допускается.**
