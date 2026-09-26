# JSON/XLSX → Template → HTML/PDF → Channel Smoke Specification

Status: PROPOSED
Branch: `spec/json-xlsx-template-channel-smoke`
Baseline: `main@898cd34637a21c0531c5b9e97dd065c68abf12c7`

## 1. Purpose

Add one production-like smoke scenario proving that the same business document can enter Collectra through two supported transport/input paths and converge to the same canonical template data and semantically equivalent generated outputs:

```text
JSON API ----------------------┐
                              ├─> source schema + mapping
XLSX multipart upload --------┘
                                   ↓
                            normalized payload
                                   ↓
                         published template version
                              ┌────┴────┐
                              ↓         ↓
                            HTML       PDF
                              ↓         ↓
                         email body   attachment
                              └────┬────┘
                                   ↓
                         channel-ready message
```

This is an integration/smoke contract, not a duplicate parser unit test.

## 2. Why this smoke is needed

Current coverage proves important pieces separately:

- `TabularImportBatchIntegrationTest` proves batch mapping, ordering, idempotency and failure semantics, currently using CSV/HTML.
- `AsyncDocumentGenerationIntegrationTest` proves mapping plus HTML/PDF generation, currently using CSV and service-level calls.
- `PdfRenderingAssuranceIntegrationTest` proves semantic PDF rendering and embedded fonts.
- campaign/message tests prove materialization/delivery pieces separately.

The missing proof is that real HTTP JSON and XLSX inputs carrying equivalent business values converge through the configured mapping/template pipeline and produce correct semantic HTML/PDF artifacts suitable for communication.

## 3. Scope

### Phase S1 — canonical input parity

Create one tenant-scoped fixture containing:

- published JSON source schema;
- published XLSX source schema;
- mapping profile/version for each source format;
- both profiles map to the same canonical field catalog keys;
- one published EMAIL template version;
- output formats `HTML,PDF`.

Recommended canonical fields:

```text
customer.fullName
customer.email
document.number
document.issueDate
document.dueDate
document.amount
document.currency
document.paymentReference
```

If the current field catalog uses different canonical keys, use the real existing keys. Do not invent a second vocabulary solely for the test.

### Phase S2 — JSON through real HTTP API

Submit a deterministic JSON fixture through:

```http
POST /api/v1/import-batches/json
Idempotency-Key: smoke-json-<run>
?mappingProfileVersionId=<json-profile>
&templateVersionId=<template-version>
&formats=HTML,PDF
Content-Type: application/json
```

The request must execute the real security/controller/application path. Do not call `ImportBatchService.create()` directly as the primary smoke proof.

### Phase S3 — XLSX through real multipart HTTP API

Generate a real `.xlsx` workbook in the test with Apache POI. Do not commit a binary workbook unless there is a demonstrated reason.

Workbook columns must represent the same business values as the JSON fixture.

Submit it through:

```http
POST /api/v1/import-batches
Idempotency-Key: smoke-xlsx-<run>
?mappingProfileVersionId=<xlsx-profile>
&templateVersionId=<template-version>
&formats=HTML,PDF
Content-Type: multipart/form-data
file=<generated .xlsx>
```

This must exercise `ExcelInputParser` through the actual API path.

## 4. Canonical parity invariant

JSON and XLSX may have different source paths/column names, but after mapping their normalized business payloads must be semantically equivalent for all fields consumed by the template.

The smoke must assert canonical values, not merely HTTP 202.

Example source representations:

JSON:

```json
{
  "customer": {
    "name": "Иван Петров",
    "email": "ivan.petrov@example.test"
  },
  "invoice": {
    "number": "INV-SMOKE-001",
    "issueDate": "2026-09-26",
    "dueDate": "2026-10-10",
    "amount": "125000.50",
    "currency": "KZT",
    "paymentReference": "PAY-SMOKE-001"
  }
}
```

XLSX:

```text
Customer Name | Email | Invoice No | Issue Date | Due Date | Amount | Currency | Payment Reference
Иван Петров   | ...   | INV-...    | 2026-...   | ...      | 125000.50 | KZT   | PAY-...
```

Use reserved `.test` email domains and deterministic non-production fixture data.

## 5. Template contract

Use one published EMAIL template version for both imports.

The template must consume mapped canonical fields in both subject/body where supported by the current model. At minimum the HTML body must contain multiple independent mapped values so that accidental hard-coded output cannot satisfy the smoke.

Representative body:

```html
<h1>Payment notice {{document.number}}</h1>
<p>Customer: {{customer.fullName}}</p>
<p>Amount: {{document.amount}} {{document.currency}}</p>
<p>Due: {{document.dueDate}}</p>
<p>Reference: {{document.paymentReference}}</p>
```

Adapt placeholders to the actual field catalog and template grammar.

## 6. Generation execution

Import acceptance alone is insufficient.

For every generated job from both input paths:

1. observe the durable generation job;
2. execute/consume the real document-generation path used by the integration environment;
3. wait using bounded observable polling if asynchronous;
4. require terminal `COMPLETED`;
5. require exactly the requested HTML and PDF outputs;
6. retrieve outputs through the tenant-scoped application/API contract.

Do not use sleeps as synchronization.

A direct worker invocation may be acceptable for a focused integration test, but a higher-level broker-backed smoke should be added if the repository's standard generation path is RabbitMQ and infrastructure is available in the existing Testcontainers suite. The evidence must state which boundary was actually exercised.

## 7. HTML semantic assertions

For JSON and XLSX outputs:

- media type is correct;
- body is non-empty;
- expected customer/document values are present;
- dynamic values are HTML-escaped;
- no unresolved `{{...}}` placeholders remain;
- no unexpected source-column names leak into output;
- JSON and XLSX outputs are semantically equivalent after normalization of irrelevant formatting.

Do not assert only output size or status.

## 8. PDF semantic assertions

For both inputs:

- bytes begin with a valid PDF signature;
- PDFBox can reopen the document;
- expected Unicode text is extractable;
- document number, customer, amount/currency and payment reference are present;
- fonts required by the fixture locale are embedded;
- no unresolved template placeholders are extractable.

Do not compare raw PDF bytes between JSON and XLSX; metadata/object ordering may legitimately differ. Compare extracted semantic content.

## 9. Channel-ready continuation

The end-state smoke must prove that generated content can feed the communication pipeline:

```text
published EMAIL template
   ↓
campaign/run or accepted message-materialization entry point
   ↓
Message
   ├─ rendered HTML body
   └─ required PDF attachment
          ↓
      attachment READY
          ↓
MESSAGE_DELIVERY_REQUESTED
```

Required assertions:

- HTML body stored/materialized for the intended message;
- PDF is represented by the durable generated-document/message-attachment relation;
- required attachment reaches `READY`;
- delivery is not requested while required PDF is `PENDING`;
- after readiness, exactly one logical delivery request exists;
- delivery snapshot/adapter command contains the expected HTML body and PDF attachment metadata/content;
- no real external provider/network call is required; stop at the deterministic adapter boundary accepted by D12.

If the current campaign architecture cannot directly bind an import-batch generated document to a campaign message, document that as a reproduced architecture gap instead of faking the relation. The implementation task must then add the smallest coherent domain bridge or split the smoke into explicitly connected accepted boundaries.

## 10. Required test layers

Preferred new top-level test:

```text
JsonXlsxTemplateChannelSmokeIntegrationTest
```

It should orchestrate the business scenario while reusing production services/controllers and existing fixture helpers where safe.

Focused supporting tests may be added only for confirmed gaps, e.g.:

- `ExcelInputParserUnitTest` for XLSX-specific parsing edge cases;
- API authorization/tenant-isolation regression;
- import-to-generation broker smoke;
- generated-document-to-message attachment binding regression.

Do not duplicate assertions already exhaustively covered by renderer/parser unit tests unless they establish this cross-module contract.

## 11. Security and tenant isolation

The smoke fixture must be tenant-scoped.

At minimum prove:

1. Alpha can submit JSON/XLSX using Alpha mapping/template versions.
2. Alpha cannot use Beta mapping profile/version.
3. Alpha cannot use Beta template version.
4. Alpha cannot read Beta batch/job/output.
5. denied cross-tenant combinations create no generation job/document in the other tenant.
6. required human capability checks remain enforced at the HTTP boundary.

Use existing permission vocabulary and fixture conventions.

## 12. Idempotency

For each transport:

- same `Idempotency-Key` + same logical request returns/replays the same batch;
- replay does not create duplicate generation jobs/documents;
- same key + different content fails closed;
- JSON key and XLSX key are independent because transport bytes differ.

Do not require JSON and XLSX to share one idempotency key or request hash.

## 13. Negative smoke cases

Keep the main smoke compact, but add focused negative coverage for high-value failures:

- malformed JSON;
- invalid/corrupt XLSX;
- missing required source field;
- mapping conversion failure;
- unpublished/foreign mapping profile;
- unpublished/foreign template version;
- missing required template value;
- generation failure;
- required PDF attachment failure prevents delivery;
- duplicate generation/completion event does not duplicate delivery request.

## 14. Observability/evidence

A failed smoke must make the broken stage obvious:

```text
INPUT_JSON
INPUT_XLSX
MAPPING
NORMALIZED_PAYLOAD
TEMPLATE_RENDER
HTML_OUTPUT
PDF_OUTPUT
ATTACHMENT_READY
MESSAGE_MATERIALIZATION
DELIVERY_REQUEST
```

Prefer assertion messages and stable IDs over verbose payload logging. Never log credentials, JWTs, customer secrets or raw production-like PII.

## 15. Acceptance matrix

| Check | JSON | XLSX |
|---|---|---|
| Real HTTP ingestion | REQUIRED | REQUIRED |
| Correct parser selected | REQUIRED | REQUIRED |
| Published mapping used | REQUIRED | REQUIRED |
| Canonical payload correct | REQUIRED | REQUIRED |
| Generation job durable | REQUIRED | REQUIRED |
| HTML generated | REQUIRED | REQUIRED |
| PDF generated | REQUIRED | REQUIRED |
| HTML semantic content | REQUIRED | REQUIRED |
| PDF semantic content | REQUIRED | REQUIRED |
| Tenant isolation | REQUIRED | REQUIRED |
| Idempotent replay | REQUIRED | REQUIRED |
| Channel-ready continuation | REQUIRED | REQUIRED |

Cross-format invariant: both paths render the same canonical business meaning from equivalent input values.

## 16. Definition of Done

This smoke is complete only when:

- the real JSON API path passes;
- the real XLSX multipart API path passes;
- both produce verified canonical mapped data;
- both generate semantically correct HTML and PDF;
- PDF is verified with PDFBox, not just `%PDF`;
- the communication continuation proves HTML body + required PDF attachment readiness to the deterministic delivery boundary, or a genuine architecture gap is explicitly captured and resolved;
- tenant-isolation negatives pass;
- idempotency/replay behavior passes;
- the test is deterministic and suitable for CI;
- the focused test passes on the exact implementation SHA;
- the relevant full verification gate is rerun after the final implementation commit.

## 17. Implementation discipline for Codex

Before coding, inspect current controllers, source formats, field catalog, mapping profile lifecycle, template model, generation worker/event transport, campaign template binding and message attachment orchestration.

Do not invent APIs that already exist.

For every discovered gap:

```text
reproduce
 -> classify
 -> root cause
 -> smallest coherent production fix
 -> regression
 -> rerun this smoke
```

Do not weaken assertions to fit current behavior. Do not claim this smoke passes until it has actually executed successfully on the stated SHA.
