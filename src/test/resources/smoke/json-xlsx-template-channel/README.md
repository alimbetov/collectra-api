# JSON/XLSX template-channel smoke fixtures

These fixtures are the deterministic source data for `docs/qa/json-xlsx-template-channel-smoke.md`.

## Files

- `input.json` — payload for the real JSON import HTTP endpoint.
- `input-xlsx.csv` — human-readable tabular source of truth used **only to generate a real XLSX workbook in test code**.
- `expected-canonical.json` — canonical mapped business meaning expected from both input paths.
- `template.html` — shared template body used by both paths.
- `manifest.json` — field inventory and semantic assertions.

## XLSX rule

The smoke must create an actual Office Open XML workbook with Apache POI from the row/header values in `input-xlsx.csv`, then upload those bytes through the multipart XLSX API path. Passing the CSV bytes directly to the importer is not valid evidence.

We intentionally do not commit a binary `.xlsx` fixture. Runtime generation keeps the business fixture reviewable, avoids opaque binary diffs, and still exercises `WorkbookFactory` / `ExcelInputParser`.

## Canonical parity

The JSON paths and XLSX column names are intentionally different source representations. Their mapping profiles must converge to the same existing Collectra field-catalog keys:

- `customer.externalId`
- `customer.displayName`
- `customer.locale`
- `customer.timezone`
- `invoice.externalId`
- `invoice.invoiceNumber`
- `invoice.invoiceDate`
- `invoice.dueDate`
- `invoice.amount`
- `invoice.currency`
- `payment.reference`

Do not create alternative canonical fields merely to make the smoke easier.

## Safety

All identifiers are deterministic synthetic test values. No production customer data, credentials, access tokens, provider addresses or live endpoints belong in this fixture directory.
