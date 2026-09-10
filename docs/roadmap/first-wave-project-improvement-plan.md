# Collectra — первая волна обзора плана доработки проекта

**Статус:** Draft / Review  
**Назначение:** системно-аналитическое ТЗ верхнего уровня для первой волны развития бизнес-функциональности Collectra.

## 1. Цель

Сформировать последовательный путь развития проекта от существующей инфраструктуры импорта, файлов, шаблонов и security к полноценному business flow:

```text
Import / API
    ↓
Customer
    ↓
Receivable
    ↓
Campaign
    ↓
Recipient
    ↓
Template
    ↓
Template Rendering
    ↓
HTML / PDF / Message
    ↓
Outbox
    ↓
RabbitMQ
    ↓
Worker
    ↓
Channel Provider
    ↓
Delivery Result
```

Ключевой принцип первой волны: не строить все подсистемы одновременно. Развитие вести вертикальными business slices, каждый из которых доводится до рабочего API, БД, правил, тестов и интеграции с существующей инфраструктурой.

---

## 2. Границы первой волны

Первая волна охватывает следующие функциональные области:

- Customer и контактные данные;
- Customer Segment;
- Receivable / Invoice;
- Payment;
- Import Mapping и persistence в business entities;
- Campaign;
- Campaign Recipient;
- Template и Template Blocks;
- Media Asset Gallery;
- Logo / Image blocks;
- QR Code block;
- Communication Message;
- Message Delivery;
- Outbox;
- RabbitMQ;
- Worker;
- EMAIL как первый delivery channel;
- HTML preview;
- PDF rendering и attachments как следующий шаг внутри первой большой волны.

Не блокируют первую волну:

- SMS;
- WhatsApp;
- Telegram;
- In-App;
- CollectionCase;
- PromiseToPay;
- NextAction;
- Dispute;
- Contracts / Customer Care / VIP сценарии.

---

# 3. Общие требования

Все tenant-owned сущности должны содержать:

```text
id
tenantId
createdAt
updatedAt
```

Tenant isolation обязательна для repository/service/API слоя.

Недопустим сценарий, при котором объект другого tenant можно получить по прямому UUID.

Business keys должны быть tenant-scoped, например:

```text
Customer: tenantId + externalId
Invoice:  tenantId + externalId
Payment:  tenantId + externalId
Segment:  tenantId + code
```

---

# 4. Customer

Customer — центральная сущность получателя коммуникаций.

```text
Customer
├── id
├── tenantId
├── externalId
├── customerType
├── displayName
├── firstName
├── lastName
├── middleName
├── companyName
├── status
├── segmentId
├── managerUserId
├── preferredLocale
├── timezone
├── customFields JSONB
├── createdAt
└── updatedAt
```

Тип:

```text
INDIVIDUAL
COMPANY
```

Статус:

```text
ACTIVE
INACTIVE
BLOCKED
ARCHIVED
```

## 4.1. CustomerEmail

```text
CustomerEmail
├── id
├── tenantId
├── customerId
├── email
├── type
├── primary
├── verified
├── status
├── createdAt
└── updatedAt
```

Email не хранить как `email1 ... email5`. Количество адресов регулируется бизнес-ограничением, а не схемой БД.

## 4.2. CustomerPhone

```text
CustomerPhone
├── id
├── tenantId
├── customerId
├── phone
├── normalizedPhone
├── type
├── primary
├── verified
├── whatsappAllowed
├── telegramAllowed
├── status
├── createdAt
└── updatedAt
```

Телефон должен нормализоваться, например:

```text
+7 (777) 123-45-67 → +77771234567
```

## 4.3. CustomerSegment

```text
CustomerSegment
├── id
├── tenantId
├── code
├── name
├── description
├── active
├── createdAt
└── updatedAt
```

Примеры: `VIP`, `RETAIL`, `SME`, `CORPORATE`, `OVERDUE_HIGH_RISK`.

---

# 5. Receivable / Invoice

Invoice отражает финансовое обязательство клиента.

```text
Invoice
├── id
├── tenantId
├── customerId
├── contractId nullable
├── externalId
├── invoiceNumber
├── invoiceDate
├── dueDate
├── originalAmount
├── paidAmount
├── outstandingAmount
├── currency
├── paymentStatus
├── documentFileId nullable
├── customFields JSONB
├── createdAt
└── updatedAt
```

`OVERDUE` желательно не использовать как единственный permanent payment status. Просрочка может вычисляться из `dueDate` и `outstandingAmount`.

---

# 6. Payment

```text
Payment
├── id
├── tenantId
├── invoiceId
├── externalId
├── paymentDate
├── amount
├── currency
├── paymentReference
├── source
├── customFields JSONB
├── createdAt
└── updatedAt
```

Обязательна поддержка partial payment.

```text
paidAmount = SUM(applied payments)
outstandingAmount = originalAmount - paidAmount
```

---

# 7. Import → Business Objects

Существующий Import subsystem должен стать ingestion layer:

```text
Excel / CSV / JSON / XML / API
        ↓
Parser
        ↓
Raw Record
        ↓
Mapping
        ↓
Canonical Model
        ↓
Validation
        ↓
Business Persistence
```

Первый набор target entities:

```text
Customer
CustomerEmail
CustomerPhone
Invoice
Payment
```

Пример mapping:

```text
client_code → customer.externalId
fio         → customer.displayName
bill_no     → invoice.invoiceNumber
bill_sum    → invoice.originalAmount
deadline    → invoice.dueDate
```

Поля вне canonical model сохранять в `customFields`.

Повторный импорт должен выполнять upsert по business keys.

Ошибка одной строки не должна откатывать весь batch.

Пример результата:

```json
{
  "received": 1000,
  "created": 800,
  "updated": 185,
  "failed": 15
}
```

---

# 8. Campaign

```text
Campaign
├── id
├── tenantId
├── name
├── type
├── status
├── templateId
├── scheduledAt
├── selectionCriteria JSONB
├── createdBy
├── createdAt
├── launchedAt
└── completedAt
```

Статусы:

```text
DRAFT
SCHEDULED
PREPARING
RUNNING
COMPLETED
CANCELLED
FAILED
```

На первом этапе selection criteria реализовать обычным DTO, без универсального query language.

Пример полей:

```text
segmentIds
daysOverdueFrom
daysOverdueTo
amountFrom
amountTo
customerIds
invoiceIds
```

---

# 9. Campaign Recipient

Перед отправкой необходимо фиксировать snapshot получателей.

```text
CampaignRecipient
├── id
├── tenantId
├── campaignId
├── customerId
├── invoiceId nullable
├── channel
├── destination
├── locale
├── status
└── createdAt
```

После формирования snapshot последующие изменения Customer/Invoice не должны молча менять уже подготовленный состав Campaign.

---

# 10. Template и Placeholder Engine

```text
Template
├── id
├── tenantId
├── code
├── name
├── channel
├── locale
├── subjectTemplate
├── bodyTemplate
├── status
├── version
├── createdAt
└── updatedAt
```

Поддерживаемые placeholders:

```text
{{customer.displayName}}
{{customer.externalId}}
{{invoice.invoiceNumber}}
{{invoice.outstandingAmount}}
{{invoice.currency}}
{{invoice.dueDate}}
{{invoice.documentUrl}}
{{manager.displayName}}
{{custom.customer.region}}
{{custom.invoice.project}}
```

Один и тот же placeholder engine должен использоваться для:

- subject;
- HTML;
- PDF;
- QR;
- dynamic image URLs;
- button URLs.

Не создавать отдельные несовместимые механизмы под каждый renderer.

---

# 11. Template Blocks

Для будущего visual template builder предусмотреть логические блоки:

```text
TEXT
HTML
IMAGE
LOGO
QR_CODE
DIVIDER
BUTTON
```

Не требуется сразу строить сложный page builder.

---

# 12. Media Asset Gallery

В системе должна существовать tenant-scoped галерея reusable assets:

- логотипы;
- изображения;
- баннеры;
- иконки;
- фоновые изображения;
- изображения для HTML/PDF.

```text
MediaAsset
├── id
├── tenantId
├── name
├── type
├── sourceType
├── fileId nullable
├── externalUrl nullable
├── mimeType
├── sizeBytes nullable
├── width nullable
├── height nullable
├── status
├── createdBy
├── createdAt
└── updatedAt
```

`type`:

```text
LOGO
IMAGE
ICON
BANNER
BACKGROUND
OTHER
```

`sourceType`:

```text
UPLOAD
EXTERNAL_URL
```

Для UPLOAD:

```text
Collectra Web
→ Media API
→ FileService
→ RustFS
```

Template должен хранить `assetId`, а не физический RustFS path.

Для EXTERNAL_URL допускается, например:

```text
https://client.kz/assets/logo.png
```

---

# 13. Image / Logo blocks

Пример IMAGE:

```json
{
  "type": "IMAGE",
  "assetId": "uuid",
  "alt": "Company logo",
  "width": 180,
  "alignment": "LEFT"
}
```

LOGO рассматривается как специализированный IMAGE block, чтобы UI мог отдельно предлагать действие `Добавить логотип`.

Для media source предусмотреть унифицированную модель:

```text
ASSET
URL
DYNAMIC_URL
```

Примеры:

```json
{
  "sourceType": "ASSET",
  "assetId": "uuid"
}
```

```json
{
  "sourceType": "URL",
  "value": "https://client.kz/logo.png"
}
```

```json
{
  "sourceType": "DYNAMIC_URL",
  "value": "https://cdn.client.kz/customer/{{customer.externalId}}/logo.png"
}
```

---

# 14. QR_CODE block

QR должен генерироваться динамически во время rendering и не обязан храниться как отдельный постоянный файл.

Поддержать три сценария.

## 14.1. Static URL

```json
{
  "type": "QR_CODE",
  "value": "https://client.kz/payment",
  "size": 200
}
```

## 14.2. Placeholder как полное значение

```json
{
  "type": "QR_CODE",
  "value": "{{invoice.documentUrl}}",
  "size": 200
}
```

## 14.3. URL + placeholder

```json
{
  "type": "QR_CODE",
  "value": "https://pay.client.kz/invoice/{{invoice.externalId}}",
  "size": 200
}
```

Допускаются query params:

```text
https://pay.client.kz/pay?invoice={{invoice.externalId}}&customer={{customer.externalId}}&amount={{invoice.outstandingAmount}}
```

QR rendering pipeline:

```text
QR Block
   ↓
valueTemplate
   ↓
Placeholder Resolver
   ↓
Resolved Value
   ↓
Validation
   ↓
QR Generator
   ↓
PNG / SVG / Data URI
   ↓
HTML / PDF
```

Placeholder resolution всегда выполняется до QR generation.

Если после resolution остался unresolved required placeholder, rendering должен завершиться контролируемой ошибкой, например:

```text
TEMPLATE_REQUIRED_VALUE_MISSING
```

с указанием `blockId` и placeholder.

---

# 15. Render Context

Для каждого сообщения формируется единый context.

Пример:

```json
{
  "customer": {
    "externalId": "C001",
    "displayName": "ABC LLP"
  },
  "invoice": {
    "externalId": "INV001",
    "invoiceNumber": "100001",
    "outstandingAmount": 150000,
    "currency": "KZT",
    "documentUrl": "https://client.kz/invoices/INV001"
  },
  "custom": {
    "customer": {
      "region": "Almaty"
    }
  }
}
```

---

# 16. Preview и HTML/PDF rendering

API preview:

```http
POST /api/v1/templates/{templateId}/preview
```

Preview должен возвращать или позволять получить:

- rendered subject;
- rendered HTML;
- resolved media;
- generated QR;
- validation warnings/errors.

HTML и PDF должны строиться из одного resolved render model.

```text
Template
   ↓
Resolve placeholders
   ↓
Resolve assets
   ↓
Resolve external resources
   ↓
Generate QR
   ↓
Resolved HTML
   ├── Email
   └── PDF renderer
```

---

# 17. External Resource Resolver

Для внешних картинок предусмотреть отдельный компонент:

```text
ExternalResourceResolver
```

Ответственность:

```text
URL
→ download
→ MIME validation
→ size validation
→ temporary/cache storage
→ renderable resource
```

Обязательны timeout и SSRF protection.

Запретить/ограничить обращения к:

```text
localhost
127.0.0.1
private/internal networks
cloud metadata endpoints
file://
ftp://
```

Предпочтительная схема — HTTPS.

Для одного и того же logo URL в большой Campaign должен использоваться render cache, чтобы ресурс не скачивался на каждого recipient заново.

---

# 18. Communication Message

```text
CommunicationMessage
├── id
├── tenantId
├── campaignId
├── recipientId
├── customerId
├── channel
├── destination
├── subject
├── body
├── status
├── renderedAt
└── createdAt
```

После rendering сохраняется snapshot фактически сформированного content.

Изменение Template после этого не должно менять уже созданный Message.

Для аудита желательно сохранять:

```text
templateVersion
assetId/version
resolved QR value
resolved dynamic URLs
```

Юридически значимый PDF хранить как generated file через FileService.

---

# 19. Message Delivery

```text
MessageDelivery
├── id
├── tenantId
├── messageId
├── attempt
├── provider
├── providerMessageId
├── status
├── requestedAt
├── sentAt
├── deliveredAt
├── failedAt
├── errorCode
├── errorMessage
└── providerResponse
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
EXPIRED
```

Message один, Delivery attempts может быть несколько.

---

# 20. Outbox + RabbitMQ

В одной DB-транзакции создаются:

```text
CommunicationMessage
MessageDelivery(PENDING)
OutboxEvent
COMMIT
```

Пример события:

```json
{
  "eventId": "...",
  "eventType": "COMMUNICATION_SEND_REQUESTED",
  "tenantId": "...",
  "messageId": "..."
}
```

Не передавать в RabbitMQ большие HTML/PDF/images. Worker получает идентификатор сообщения и дочитывает данные из БД/FileService.

Outbox retry и Delivery retry — разные механизмы.

---

# 21. Worker и EMAIL

Worker:

```text
consume
→ load Message
→ idempotency check
→ load Delivery
→ load attachments/assets if needed
→ ChannelSender
→ provider
→ save providerMessageId
→ update Delivery
```

Общий контракт:

```java
public interface ChannelSender {
    Channel channel();
    SendResult send(OutboundMessage message);
}
```

Первый adapter:

```text
EmailChannelSender
```

Email provider также должен быть абстрагирован отдельным интерфейсом, чтобы бизнес-код не зависел от SMTP/SES/SendGrid/Mailgun и т.п.

---

# 22. API первого этапа

Customer:

```http
POST   /api/v1/customers
GET    /api/v1/customers
GET    /api/v1/customers/{id}
PUT    /api/v1/customers/{id}
PATCH  /api/v1/customers/{id}/status
POST   /api/v1/customers/{id}/emails
DELETE /api/v1/customers/{id}/emails/{emailId}
POST   /api/v1/customers/{id}/phones
DELETE /api/v1/customers/{id}/phones/{phoneId}
```

Invoice/Payment:

```http
POST /api/v1/invoices
GET  /api/v1/invoices
GET  /api/v1/invoices/{id}
POST /api/v1/invoices/{id}/payments
GET  /api/v1/invoices/{id}/payments
```

Media:

```http
POST   /api/v1/media
POST   /api/v1/media/upload
GET    /api/v1/media
GET    /api/v1/media/{id}
PUT    /api/v1/media/{id}
DELETE /api/v1/media/{id}
```

Templates:

```http
POST /api/v1/templates
GET  /api/v1/templates
GET  /api/v1/templates/{id}
PUT  /api/v1/templates/{id}
POST /api/v1/templates/{id}/preview
```

Campaign:

```http
POST /api/v1/campaigns
GET  /api/v1/campaigns
GET  /api/v1/campaigns/{id}
POST /api/v1/campaigns/{id}/prepare
POST /api/v1/campaigns/{id}/launch
POST /api/v1/campaigns/{id}/cancel
```

---

# 23. План PR первой волны

## PR 1 — Customer + Receivable Core

Ветка:

```text
feature/customer-receivable-core
```

Scope:

```text
Customer
CustomerEmail
CustomerPhone
CustomerSegment
Invoice
Payment
```

Обязательно:

```text
Entity
Repository
Service
REST Controller
DTO
Mapper
Validation
Liquibase
Tenant isolation
Unit tests
Integration tests
```

Не включать Campaign, RabbitMQ, Worker, Email, QR и Media Gallery.

## PR 2 — Import Business Persistence

```text
feature/import-business-persistence
```

Цель:

```text
Existing Import
→ Mapping
→ Validation
→ Upsert
→ Customer / Invoice / Payment
```

Поддержать row-level errors и batch statistics.

## PR 3 — Template Media Assets

```text
feature/template-media-assets
```

Scope:

```text
MediaAsset
Media Gallery
FileService integration
IMAGE block
LOGO block
QR_CODE block
DYNAMIC_URL
Placeholder Resolver
Preview
ExternalResourceResolver
Render cache
```

Этот PR формирует единый фундамент для HTML, PDF, QR, Logos, Images, Buttons и будущих attachments.

## PR 4 — Campaign Core

```text
Campaign
CampaignRecipient
selection criteria
recipient snapshot
```

## PR 5 — Communication Core

```text
CommunicationMessage
MessageDelivery
render snapshot
delivery attempts
```

## PR 6 — EMAIL Delivery Pipeline

```text
Outbox
RabbitMQ
Worker
ChannelSender
EmailChannelSender
EmailProvider
Retry
Idempotency
```

## PR 7 — PDF / Attachments

```text
Resolved HTML
→ PDF Renderer
→ FileService
→ Attachment
→ Email
```

---

# 24. Acceptance Criteria для Media / QR

Функциональность считается принятой, если можно:

```text
загрузить logo.png
→ увидеть в Media Gallery
→ выбрать в Template
→ выполнить Preview
→ увидеть логотип
```

```text
создать QR Block
→ value = {{invoice.documentUrl}}
→ выполнить Preview
→ получить персональный QR
```

```text
создать QR Block
→ value = https://pay.kz/{{invoice.externalId}}
→ выполнить Preview
→ получить QR с resolved URL
```

```text
создать IMAGE block
→ указать внешний URL
→ получить изображение в HTML Preview
→ получить то же изображение в PDF
```

```text
создать IMAGE block
→ указать DYNAMIC_URL с placeholder
→ получить корректный resolved resource
```

---

# 25. Definition of Done первой большой волны

Система должна выполнить end-to-end сценарий:

```text
ERP / Excel / JSON
        ↓
Import
        ↓
Customer
        ↓
Invoice
        ↓
Campaign
        ↓
CampaignRecipient
        ↓
Template
        │
        ├── Text
        ├── Logo
        ├── Image
        ├── QR
        └── PDF
        ↓
Render Context
        ↓
CommunicationMessage
        ↓
Outbox
        ↓
RabbitMQ
        ↓
Worker
        ↓
EMAIL Provider
        ↓
Delivery
```

После отправки должна быть доступна информация:

```text
кому отправлено
когда отправлено
по какому обязательству
какой template использовался
какая версия template использовалась
какой HTML был сформирован
какие assets использовались
какое значение было закодировано в QR
какой provider обработал сообщение
каков результат доставки
```

---

# 26. Архитектурное решение по ответственности компонентов

```text
Media Gallery
```

отвечает за статические reusable assets.

```text
Placeholder Resolver
```

отвечает за динамические значения.

```text
QR Generator
```

отвечает только за:

```text
String → QR image
```

```text
ExternalResourceResolver
```

отвечает за:

```text
URL → renderable resource
```

```text
Template Renderer
```

оркестрирует весь render pipeline.

QR Generator не должен знать о Customer, Invoice или Campaign. Он получает уже resolved string.

---

# 27. Целевая схема rendering

```text
                 Template
                    │
                    ▼
             Template Blocks
                    │
      ┌─────────────┼──────────────┐
      │             │              │
     TEXT          IMAGE           QR
      │             │              │
      │             ▼              │
      │      MediaAsset / URL      │
      │             │              │
      └───────┬─────┴──────────────┘
              ▼
       Placeholder Resolver
              │
              ▼
        Resolved Model
              │
     ┌────────┼─────────┐
     │        │         │
     ▼        ▼         ▼
 Resource     QR       HTML
 Resolver   Generator  Renderer
     │        │         │
     └────────┴────┬────┘
                   ▼
            Final HTML
              │       │
              ▼       ▼
            Email    PDF
```

Эта модель принимается как базовая архитектура формирования контента Collectra в рамках первой волны.

---

# 28. Что требуется от следующего review

Этот документ не является окончательным детальным design spec каждой подсистемы. Он фиксирует первую волну направления развития.

На следующем review отдельно детализировать:

1. таблицы и связи Customer / Receivable;
2. API contracts;
3. import mapping model;
4. template storage model;
5. template versioning;
6. MediaAsset lifecycle;
7. QR / DYNAMIC_URL security constraints;
8. Campaign selection rules;
9. message idempotency;
10. Outbox / Rabbit retry strategy;
11. PDF rendering lifecycle;
12. file retention и cleanup;
13. audit requirements;
14. permissions/RBAC для новых модулей.

После согласования этой волны каждую крупную подсистему следует переводить в отдельное implementation ТЗ перед разработкой.