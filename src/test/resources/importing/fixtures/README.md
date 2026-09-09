# Import fixtures: documents with detail rows and contact channels

These fixtures define one business scenario represented through several source formats.

## Scenario

- 3 invoices.
- 24 detail rows total: 7 + 8 + 9.
- Every invoice has header/business fields and recipient delivery fields.
- Delivery channels covered: EMAIL, WHATSAPP, TELEGRAM.
- One detail row intentionally has an empty `item_name`.
- One document intentionally has an empty WhatsApp destination.

## Canonical intent

Header fields:
- `invoice.number`
- `customer.name`
- `customer.bin`
- `invoice.amount`
- `invoice.currency`
- `invoice.due_date`

Recipient fields:
- `recipient.email`
- `recipient.phone`
- `recipient.whatsapp`
- `recipient.telegram`
- `recipient.locale`
- `recipient.preferred_channel`

Detail fields:
- `items[].line_no`
- `items[].item_code`
- `items[].item_name`
- `items[].qty`
- `items[].price`
- `items[].amount`

## Required invariants for parser/mapping tests

1. Physical row order is preserved.
2. Empty values remain attached to their row and are not removed.
3. `INV-1001`, `INV-1002`, and `INV-1003` remain separate document aggregates.
4. Their detail order is preserved as 1..7, 1..8, and 1..9.
5. JSON arrays and repeated XML `item` nodes represent the same ordered detail concept.
6. Flat CSV/XLSX rows are groupable by `invoice_number` without mixing detail rows between documents.
7. Recipient/contact data belongs to the document header and must survive normalization.
8. A missing channel destination must not shift or corrupt other fields.

The fixtures are intentionally stable and are meant to become reusable golden inputs for parser, SourceSchema, mapping, normalization, and delivery-routing tests.
