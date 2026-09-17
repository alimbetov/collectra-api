# Public money Decimal String contract

Status: APPROVED FOR IMPLEMENTATION

- Decision scope: public `/api/v1` HTTP contract only
- Database baseline: PostgreSQL `NUMERIC(19,4)`
- Frontend prerequisite: FW3, FW5, FW6 and money filters in FW7

## 1. Decision

Every public monetary amount is transported as a JSON string containing a canonical
plain decimal. Java keeps `BigDecimal`; PostgreSQL keeps `NUMERIC(19,4)`. React keeps the
wire value as a string and MUST NOT coerce it to JavaScript `number` for storage,
comparison, validation, arithmetic or formatting.

```ts
export type DecimalString = string;
export type MoneyAmount = DecimalString;
```

Example response:

```json
{
  "originalAmount": "150000.25",
  "paidAmount": "50000",
  "outstandingAmount": "100000.25",
  "currency": "KZT"
}
```

This replaces the current lossy `Decimal = number` frontend assumption. It does not
change domain arithmetic, database schema or currency ownership.

## 2. Why this is required

Jackson currently emits public `BigDecimal` values as JSON numbers. JavaScript parses
those values as IEEE-754 `number`, which cannot represent all `NUMERIC(19,4)` values
exactly. Dashboard totals and receivable balances can therefore be rounded before React
renders them or sends them back.

The contract is fixed before financial screens are built so individual features do not
introduce incompatible parsing and formatting workarounds.

## 3. Lexical and numeric contract

### 3.1 Accepted request form

A money string MUST satisfy all of the following:

- ASCII digits with an optional leading `-` and optional decimal fraction;
- regular expression `^-?(0|[1-9][0-9]{0,14})(\.[0-9]{1,4})?$`;
- no exponent, leading `+`, whitespace, grouping separator or locale decimal comma;
- precision at most 19 and scale at most 4 after parsing;
- at most 15 digits before the decimal point, matching `NUMERIC(19,4)`;
- no leading zeroes except the single integer digit `0`;
- endpoint business validation remains authoritative for sign and range.

| Input | Result |
|---|---|
| `"0"`, `"0.0001"`, `"150000.25"` | accepted lexically |
| `"-1"` | lexically valid; rejected by positive-amount commands |
| `"01"`, `"1."`, `".5"`, `"1,25"` | rejected |
| `"1e3"`, `"+1"`, `" 1 "` | rejected |
| `"0.00001"` | rejected: scale exceeds 4 |
| `"1000000000000000.0000"` | rejected: precision exceeds 19 |

The lexical pattern alone is not enough to enforce the database range. Backend validation
MUST also check `BigDecimal.precision() <= 19`, `max(scale(), 0) <= 4` and at most 15
integer digits after parsing.

### 3.2 Canonical response form

Responses use a deterministic representation:

1. assert precision/scale fit `NUMERIC(19,4)`;
2. normalize every numeric zero to `"0"`;
3. otherwise apply `stripTrailingZeros().toPlainString()`;
4. never emit exponent notation.

Therefore `150000.2500` is returned as `"150000.25"`, `10.0000` as `"10"` and
`-0.0000` as `"0"`. Display padding belongs to the currency formatter, not the API.

### 3.3 Nullability and currency

- Existing field nullability does not change.
- A nullable amount is either a valid decimal string or JSON `null`; empty string is
  never a transported value.
- Currency remains a separate uppercase ISO-4217 code supplied by the backend.
- Amounts in different currencies MUST NOT be summed into one KPI by React.

## 4. Public field inventory

The implementation PR MUST cover every current public money field, including nested page
items. This list is an audit baseline, not permission to omit a newly added money field.

| API/domain DTO | Fields |
|---|---|
| Dashboard `CurrencyTotal` | `amount` |
| Dashboard `Aging` | `current`, `days1To30`, `days31To60`, `days61To90`, `days90Plus` |
| Dashboard `CurrencyReceivables` | `outstanding` |
| Invoice request/response/list item | `originalAmount`, `paidAmount`, `outstandingAmount` |
| Invoice list query | `amountMin`, `amountMax`, `outstandingMin`, `outstandingMax` |
| Payment request/response/list item | `amount` |
| Payment list query | `amountMin`, `amountMax` |
| Allocation request/response | `amount` |
| Collection promise request/response | `amount` |
| Collection case list item | `outstandingAmount` |
| Campaign selection request/detail | `amountFrom`, `amountTo` |

Counts, versions, days overdue and delivery counters remain JSON integers. Generic import
mapping values, template materialization JSON, JSONB selection storage, outbox events and
provider payloads are internal contracts and are not changed implicitly by this decision.

## 5. Backend implementation contract

### 5.1 Boundary type and Jackson

Domain entities, repositories and application services continue to use `BigDecimal`.
Conversion is owned by the REST boundary.

Add a shared API type under `io.collectra.api.shared.api`, for example:

```java
public record DecimalString(BigDecimal value) {
    // validated factory, canonical @JsonValue and strict @JsonCreator
}
```

Public request/response DTO money fields use that boundary type and map explicitly to or
from `BigDecimal`. A global `BigDecimal` serializer is forbidden because it would silently
change internal endpoints and payloads outside the reviewed inventory.

Concrete code surface:

| File/package | Required change |
|---|---|
| `shared/api/DecimalString.java` | strict parse, precision/scale validation, canonical JSON value |
| `shared/error/ApiExceptionHandler.java` | stable decimal parse/range ProblemDetail mapping |
| `dashboard/application/DashboardQueryService.java` | map public aggregate money to boundary values |
| `receivable/api/ReceivableController.java` | request, response and filter boundary migration |
| `receivable/application/ReceivableQueryService.java` | public list item boundary migration |
| `collection/api/CollectionController.java` | promise request/response boundary migration |
| `collection/application/CollectionQueryService.java` | case projection boundary migration |
| `campaign/api/CampaignController.java` | public selection DTO separated from application selection |
| `frontendweb/src/shared/api/contracts.ts` | `DecimalString`, never `number` |
| `frontendweb/src/shared/i18n/formatters.ts` | precision-safe money formatter |

`CampaignSelection` is currently both an application record and a request body. The code
PR MUST introduce an API request DTO and map it to the application record instead of
putting Jackson/OpenAPI concerns into `campaign.application`.

Required behavior:

- `@JsonValue` writes the canonical string;
- delegating `@JsonCreator` accepts JSON strings only in the final contract;
- malformed lexical values map to stable `400` ProblemDetail code
  `INVALID_DECIMAL` with a field error when field context is available;
- precision/scale overflow maps to `DECIMAL_OUT_OF_RANGE`;
- sign/range violations continue to use the command's existing validation/business code;
- logs and ProblemDetail MUST NOT echo an unbounded raw request value.

Query parameters are text on the wire already. They use the same strict parser and
precision/scale checks instead of default permissive `BigDecimal` conversion.

### 5.2 OpenAPI schema

All inventoried properties and query parameters are described as:

```yaml
type: string
pattern: '^-?(0|[1-9][0-9]{0,14})(\.[0-9]{1,4})?$'
example: '150000.25'
description: Canonical plain decimal; precision <= 19, scale <= 4; no exponent.
```

Command-specific positivity is documented additionally with prose because OpenAPI string
schemas do not express numeric minimum semantics reliably.

### 5.3 Persistence

No Liquibase migration is required: invoices, payments, allocations and promises already
use `NUMERIC(19,4)`. Aggregate SQL results MUST be checked against the same public
precision bound. An overflow is a server-side invariant failure, never silent truncation.

## 6. Compatibility and rollout

Changing a response property from JSON number to string is breaking for `/api/v1` and
the existing OpenAPI compatibility gate must reject it by default.

Implementation decision, 2026-09-17: use the MVP hard cut. The only known first-party
consumer is `frontendweb` in this repository, it is migrated in the same PR, and no
released external `/api/v1` consumer is recorded. If that inventory is found to be wrong,
this PR must not be deployed; a versioned API is required instead.

Before implementation, record one of these outcomes in the code PR:

1. **MVP hard cut (preferred now):** repository and deployment consumer inventory proves
   there is no released external `/api/v1` consumer. Backend and frontend changes land in
   one coordinated release. The OpenAPI baseline update is explicitly reviewed and the
   PR explains the accepted break.
2. **External consumer exists:** do not mutate `/api/v1`. Introduce a versioned API
   contract (normally `/api/v2`) or an explicitly time-bounded additive migration. A
   permanent `oneOf: [number, string]` response is forbidden.

During a hard-cut rollout, requests MAY accept legacy JSON numbers for one explicitly
dated compatibility window only if a deployed first-party client requires it. Responses
switch once and emit strings only. Any temporary numeric request path must have telemetry,
a removal issue and tests; it is not part of the final contract.

The baseline file is regenerated with `scripts/update-openapi-baseline.sh` only after the
breaking-change decision is reviewed. Updating the baseline merely to make CI green is
forbidden.

## 7. Frontend implementation contract

- Replace `Decimal = number` with `DecimalString`/`MoneyAmount` in generated or handwritten
  public DTOs.
- Keep form values as canonical strings; locale separators are display/input concerns and
  never enter request payloads.
- Do not call `Number`, `parseFloat`, unary `+` or `.toFixed()` on money.
- Comparison/addition, when a feature genuinely needs it, uses a reviewed arbitrary-
  precision decimal utility. Most screens render backend-owned values and do no arithmetic.
- The shared money formatter accepts `(amount: DecimalString, currency, locale)` and must
  preserve all significant digits. It may use `Intl` only through a path proven not to
  coerce the decimal to an unsafe binary number.
- Currency minor-unit display padding is presentation-only. It must not rewrite the stored
  canonical value.
- Query keys contain the canonical string, not a parsed number or locale-formatted text.

## 8. Tests

### Backend unit/JSON tests

- strict valid/invalid lexical table from section 3;
- `999999999999999.9999` round-trips exactly;
- `0.1`, trailing zero normalization and negative zero;
- serializer never emits a JSON number or exponent;
- overflow and invalid syntax produce stable ProblemDetail codes;
- generic non-public/internal `BigDecimal` serialization is not changed globally.

Suggested focused test: `shared/api/DecimalStringTest`.

### PostgreSQL/API integration tests

- create invoice/payment/allocation/promise from string and read exact string back;
- dashboard aggregate and receivable list/detail use quoted decimal strings;
- query filters accept canonical values and reject exponent/comma/scale overflow;
- tenant isolation and paging behavior remain unchanged;
- a stored `NUMERIC(19,4)` boundary value survives an API round trip exactly.

Extend `ReceivableFrontendApiIntegrationTest` and add focused Dashboard/Collection API
coverage; do not rely only on `CustomerReceivableCoreIntegrationTest` domain assertions.

### OpenAPI tests

- every field in section 4 is `type: string` with pattern/example;
- no public money field remains `number`/`double`;
- the intentional compatibility result and reviewed baseline diff are attached to the PR.

`OpenApiCompatibilityIntegrationTest` must still demonstrate that the old baseline is
breaking before the approved baseline is replaced; `OpenApiCompatibilityUnitTest` keeps
covering the gate itself.

### Frontend tests

- contract fixtures reject numeric money values;
- RU and KK formatting preserve a 19-digit boundary value;
- forms submit canonical strings and never locale-formatted values;
- filter URL/query keys round-trip decimal strings;
- no unsafe money conversion is introduced (architecture/lint guard or focused source
  test for the shared money path).

## 9. Implementation order

1. Confirm consumer inventory and choose hard cut versus versioned API.
2. Add strict shared REST boundary type/parser and unit tests.
3. Migrate request DTOs and query parameter conversion.
4. Migrate response DTOs for Dashboard, Receivables, Collections and Campaign selection.
5. Add API/PostgreSQL contract tests and stable error mapping.
6. Update OpenAPI annotations/schema and review the compatibility report.
7. Update frontend DTOs, forms and precision-safe formatter in the same coordinated
   release train.
8. Regenerate the reviewed OpenAPI baseline and run backend/frontend CI gates.

## 10. Out of scope

- database precision or currency conversion changes;
- cross-currency aggregation;
- tax, rounding or accounting policy;
- changing internal template/message payload snapshots;
- accepting localized numeric strings at the REST boundary;
- arbitrary-precision calculations in individual React pages.

## 11. Definition of Done

- consumer-inventory decision is recorded and compatible rollout path selected;
- all inventoried public money fields and filters use the string schema consistently;
- PostgreSQL remains `NUMERIC(19,4)` and exact round-trip tests pass;
- stable validation/error codes and bounded logging are covered;
- OpenAPI compatibility outcome and baseline diff are explicitly reviewed;
- frontend contains no `number` money state or unsafe conversion path;
- FW3/FW5/FW6 money-contract readiness gate is marked closed only after both backend and
  frontend contract changes are merged.
