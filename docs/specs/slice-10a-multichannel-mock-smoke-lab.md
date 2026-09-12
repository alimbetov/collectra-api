# Slice 10A — Multi-channel mock smoke lab

Status: SPEC / IMPLEMENTATION PLAN

## 1. Goal

Build a production-shaped delivery test polygon for Collectra that exercises the real communication pipeline without contacting external providers.

The lab must validate the same application path that production delivery will use:

```text
business data / import
        -> template selection
        -> placeholder resolution
        -> channel materialization
        -> Message + attachments
        -> Outbox
        -> RabbitMQ
        -> MessageDeliveryWorker
        -> ChannelDeliveryRouter
        -> channel adapter
        -> mock provider response
        -> Message state transition
        -> CampaignRun counters
        -> delivery API / dashboard / metrics
```

The only substituted boundary is the external provider call. Everything before and after that boundary remains real application code and PostgreSQL state.

## 2. Architectural principle

`MessageDeliveryWorker` must not know whether a channel uses a mock provider or a real provider.

Target shape:

```text
DeliveryGateway
    |
    v
ChannelDeliveryRouter
    |
    +-- EMAIL -----> EmailDeliveryAdapter -----> mock | KumoMTA
    +-- SMS -------> SmsDeliveryAdapter -------> mock | provider
    +-- WHATSAPP --> WhatsAppDeliveryAdapter --> mock | provider
    +-- TELEGRAM --> TelegramDeliveryAdapter --> mock | provider
    +-- IN_APP ----> InAppDeliveryAdapter -----> mock | provider
```

Provider switching is configuration only. No business service, worker, state machine, retry policy or campaign code may branch on `mock/provider`.

## 3. Channel configuration

Configuration must be channel-specific so real providers can be enabled gradually.

Target configuration:

```yaml
collectra:
  communication:
    channels:
      email:
        mode: mock
        provider: kumomta
      sms:
        mode: mock
        provider: stub
      whatsapp:
        mode: mock
        provider: stub
      telegram:
        mode: mock
        provider: stub
      in-app:
        mode: mock
        provider: stub

    mock:
      default-profile: mixed-80-10-10
      profiles:
        success:
          success-rate-percent: 100
          permanent-failure-rate-percent: 0
          retryable-failure-rate-percent: 0
        mixed-80-10-10:
          success-rate-percent: 80
          permanent-failure-rate-percent: 10
          retryable-failure-rate-percent: 10
```

Modes:

```text
mock      -> deterministic local result, no external network
provider  -> real provider adapter
```

Unknown mode/provider must fail application startup. Silent fallback from `provider` to `mock` is forbidden.

## 4. Deterministic mock provider

Do not use runtime randomness in smoke tests.

The result for a message must be reproducible from stable input, initially `messageId` plus selected mock profile.

Example deterministic bucketing:

```text
bucket = floorMod(hash(messageId + profile), 100)

0..79  -> Accepted
80..89 -> Permanent rejection
90..99 -> Retryable rejection
```

Provider ids must look operationally distinct but contain no PII:

```text
mock:email:<messageId>
mock:sms:<messageId>
mock:whatsapp:<messageId>
mock:telegram:<messageId>
mock:in-app:<messageId>
```

## 5. Explicit mock scenarios

Rate profiles are useful for campaign smoke tests, but deterministic named scenarios are also required for precise tests.

Support scenario selection through test-only fixture metadata or a test provider registry, not through user-controlled production message content.

Required scenarios:

```text
ACCEPTED
RETRYABLE_TIMEOUT
RETRYABLE_RATE_LIMIT
RETRYABLE_PROVIDER_5XX
PERMANENT_INVALID_DESTINATION
PERMANENT_REJECTED_RECIPIENT
PERMANENT_CONTENT_REJECTED
DELAYED_ACCEPT
```

Expected mapping:

| Scenario | DeliveryResult | Expected Message state |
|---|---|---|
| ACCEPTED | Accepted | SENT |
| RETRYABLE_TIMEOUT | Rejected(RETRYABLE) | RETRY_WAIT |
| RETRYABLE_RATE_LIMIT | Rejected(RETRYABLE) | RETRY_WAIT |
| RETRYABLE_PROVIDER_5XX | Rejected(RETRYABLE) | RETRY_WAIT |
| PERMANENT_INVALID_DESTINATION | Rejected(PERMANENT) | FAILED |
| PERMANENT_REJECTED_RECIPIENT | Rejected(PERMANENT) | FAILED |
| PERMANENT_CONTENT_REJECTED | Rejected(PERMANENT) | FAILED |
| DELAYED_ACCEPT | Accepted after configurable delay | SENT |

Delay must be capped in tests and must never hold a PostgreSQL row lock.

## 6. Channel validation

Each adapter validates only channel-specific delivery constraints before calling its provider boundary.

### EMAIL

Required:

```text
destination
subject
body
```

Exercise:

- plain text;
- HTML body;
- RU/KZ/CJK text;
- long subject boundary;
- attachment metadata;
- multiple attachments;
- QR/image references after materialization.

### SMS

Required:

```text
destination
body
```

Exercise:

- GSM-like short text;
- Unicode RU/KZ/CJK;
- long text / segmentation boundary;
- invalid phone destination;
- empty body rejection.

No SMS billing/segment calculation becomes authoritative business state in this slice.

### WhatsApp

Required:

```text
destination
body or provider-template payload
```

Exercise:

- text body;
- template identifier + parameters model;
- optional document/media reference;
- invalid destination;
- provider template rejection.

Real Meta/WhatsApp API integration remains disabled until credentials/provider contract are available.

### Telegram

Required:

```text
destination/chat target
body
```

Exercise:

- plain text;
- Markdown/HTML-like source treated according to adapter contract;
- document reference;
- invalid chat target;
- provider rejection.

### In-App / Push

Required:

```text
destination/device or application recipient key
body
```

Exercise:

- title/body payload;
- deep-link/custom data projection;
- invalid/expired target;
- provider retryable failure.

The mock adapter must not invent Firebase-specific DTOs in the application layer.

## 7. Template and placeholder polygon

Smoke fixtures must validate the existing template compiler/materialization path before provider delivery.

Required placeholder groups:

```text
customer.*
contract.*
invoice.*
payment.*
collection.*
custom.customer.*
custom.invoice.*
```

Required cases:

1. all placeholders resolved;
2. optional value absent;
3. required value absent -> materialization failure, no delivery;
4. unknown placeholder -> validation failure;
5. HTML escaping for untrusted values;
6. RU/KZ Unicode;
7. CJK text to protect the fixed font path;
8. numeric amount/currency formatting;
9. LocalDate/date-time rendering under configured business zone;
10. large body boundary;
11. per-channel variant selection;
12. same business input rendered into EMAIL/SMS/WhatsApp/Telegram/In-App variants.

Smoke tests must assert both rendered output and resulting delivery state. Provider mock success must never hide template errors.

## 8. Import/parsing polygon

Exercise source ingestion through the real import pipeline for supported formats:

```text
JSON
XML
CSV
Excel
```

Fixtures must cover:

- canonical fields;
- custom fields;
- header aliases;
- lowercase/uppercase variations where mapping allows them;
- missing required field;
- invalid date;
- invalid decimal amount;
- unknown header;
- duplicate external id;
- nested recipient data where supported;
- multiple emails/phones;
- mixed RU/KZ/CJK content.

Target smoke flow:

```text
fixture file
  -> parser
  -> mapping profile
  -> persisted business data
  -> campaign/materialization
  -> template rendering
  -> mock provider
  -> delivery outcome
```

The polygon must therefore detect failures in parsing/mapping as well as delivery.

## 9. Attachments and document generation

Required cases:

```text
no attachment
optional attachment READY
mandatory attachment READY
mandatory attachment PENDING -> delivery blocked
mandatory attachment FAILED -> delivery blocked/failed according to current contract
multiple attachments
PDF generated document
expired/deleted storage object negative path
```

External provider mock receives only resolved delivery attachment metadata/content according to the existing `DeliveryCommand`; it must not bypass `MessageAttachmentContentResolver`.

## 10. Retry and recovery smoke cases

Required end-to-end cases:

```text
QUEUED -> PROCESSING -> SENT
QUEUED -> PROCESSING -> RETRY_WAIT
RETRY_WAIT -> QUEUED -> PROCESSING -> SENT
retry exhaustion -> FAILED
stale PROCESSING -> recovery -> RETRY_WAIT/FAILED
broker duplicate delivery -> idempotent no-op
terminal SENT redelivery -> no second send
terminal FAILED redelivery -> no second send
```

Campaign counters must remain consistent after duplicate delivery and retries.

## 11. Campaign-level smoke profiles

### Small deterministic campaign

```text
5 recipients
one message for each channel
all ACCEPTED
```

Purpose: channel routing and basic materialization.

### Mixed provider outcome campaign

```text
100 messages
profile mixed-80-10-10
expected deterministic distribution:
~80 accepted
~10 permanent failures
~10 retryable failures
```

Assertions must use the exact deterministic bucket results generated for fixture message ids, not probabilistic ranges.

### Mixed channel campaign

Example:

```text
20 EMAIL
20 SMS
20 WHATSAPP
20 TELEGRAM
20 IN_APP
```

Assert each message reaches only its configured channel adapter and provider id prefix matches that channel.

## 12. Test layers

### Unit tests

```text
ChannelDeliveryRouterTest
MockDeliveryProviderTest
Channel adapter validation tests
provider mode configuration tests
```

### PostgreSQL integration tests

```text
Message state transitions
retry/recovery
campaign counters
tenant isolation
attachments gate
```

### Smoke tests

Prefer dedicated test class/package so smoke scenarios can run independently:

```text
src/test/java/io/collectra/api/smoke/
```

Suggested classes:

```text
MultiChannelDeliverySmokeTest
TemplateRenderingSmokeTest
ImportToDeliverySmokeTest
AttachmentDeliverySmokeTest
RetryRecoverySmokeTest
CampaignDeliverySmokeTest
```

Smoke suite command should be separable from full verify, for example through a Maven profile/tag, while all deterministic core tests remain part of normal `mvn verify` when runtime cost is acceptable.

## 13. Test fixtures

Target fixture structure:

```text
src/test/resources/smoke/
  imports/
    customers-valid.json
    customers-invalid.json
    invoices-valid.xml
    invoices-valid.csv
    invoices-valid.xlsx
  templates/
    email/
    sms/
    whatsapp/
    telegram/
    in-app/
  expected/
    rendered/
  attachments/
    sample.pdf
    sample.txt
```

Fixtures must contain synthetic data only. Never copy real customer email addresses, phones, invoice ids or production documents into the repository.

## 14. Security and observability assertions

Smoke tests must additionally verify:

- tenant A cannot inspect tenant B messages;
- destination is masked in frontend/support APIs where required;
- raw body/destination is not emitted in operational logs;
- provider error text is normalized/safe;
- metrics contain channel/status/result dimensions without PII;
- mock provider ids contain no recipient data.

## 15. Real provider activation contract

The purpose of this slice is to make later activation small and isolated.

Example future rollout:

```yaml
collectra.communication.channels.email.mode: provider
collectra.communication.channels.sms.mode: mock
collectra.communication.channels.whatsapp.mode: mock
collectra.communication.channels.telegram.mode: mock
collectra.communication.channels.in-app.mode: mock
```

Then, progressively:

```text
EMAIL -> KumoMTA
SMS -> selected SMS provider
WhatsApp -> selected official provider/API
Telegram -> Bot API/provider
In-App -> FCM/APNs-backed provider
```

Changing a mode must not require changes to campaign, template, message, retry, outbox or worker code.

## 16. Explicit non-goals

Not part of this slice:

- purchasing provider accounts;
- storing real provider credentials in Git;
- live SMS/WhatsApp/Telegram/Push sending;
- provider billing/reconciliation;
- delivery receipts/webhooks from real providers;
- advanced provider failover/routing;
- marketing consent redesign;
- generic workflow engine.

These become follow-up slices after the mock polygon proves the pipeline.

## 17. Definition of Done

The implementation slice is complete when:

1. all five channels route through one production-shaped channel boundary;
2. every channel supports `mock` mode;
3. EMAIL preserves a real KumoMTA provider path;
4. other channels have explicit provider boundaries ready for later implementations;
5. provider selection is channel-specific configuration;
6. deterministic accepted/retryable/permanent mock outcomes are supported;
7. template/placeholder smoke fixtures cover success and validation failures;
8. JSON/XML/CSV/Excel import fixtures reach the same business pipeline where supported;
9. attachment gates are covered;
10. retry/recovery/duplicate delivery are covered;
11. tenant isolation and PII-safe logging/API behavior are asserted;
12. campaign counters remain correct;
13. `mvn verify` is green;
14. dedicated smoke suite is reproducible and does not contact external networks.

## 18. Recommended implementation order

```text
1. Introduce channel provider configuration model
2. Introduce ChannelDeliveryRouter without changing worker contract
3. Adapt existing KumoMTA + simulation code behind EMAIL adapter
4. Add mock-capable SMS adapter
5. Add mock-capable WhatsApp adapter
6. Add mock-capable Telegram adapter
7. Add mock-capable In-App adapter
8. Add deterministic scenario registry/profiles
9. Add template/placeholder fixtures
10. Add import fixtures JSON/XML/CSV/Excel
11. Add attachment smoke cases
12. Add retry/recovery/counter smoke cases
13. Add security/observability assertions
14. Run full PostgreSQL/Testcontainers verify
```

No external network call is permitted in mock smoke tests.
