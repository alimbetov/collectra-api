# Business Import Persistence — implementation slice

This slice connects the existing SourceSchema / MappingProfile / MappingExecutionService pipeline to the business core introduced in `feature/customer-receivable-core`.

## Flow

`File -> existing parser -> existing mapping -> normalized payload -> BusinessRecordPersistenceService -> Customer / Invoice / Payment`

Supported mapping profile `documentType` values in this slice:

- `CUSTOMER`
- `INVOICE`
- `PAYMENT`

The persistence step uses tenant-scoped `externalId` as the idempotency key. A repeated import of an already created business record returns `REUSED` and does not create a duplicate.

For invoice and payment records, `customer.externalId` is required. If the customer does not exist yet, a minimal customer is created from the mapped `customer.*` fields.

## API

`POST /api/v1/business-imports/mapping-profiles/{versionId}` as `multipart/form-data`, part name `file`.

The response contains total / created / reused / failed counters and one result per mapped document. Persistence errors are isolated per mapped document so one business record failure does not prevent already valid records from being processed.

## Deliberate limits

This slice does not update an existing Invoice or Payment when the same external ID is imported with changed financial data. Re-import is currently idempotent create-or-reuse. Financial correction/update policy needs explicit business rules and should not be guessed inside the import adapter.

Payment allocation to invoices remains a separate business operation and is not inferred from an imported payment unless an explicit allocation mapping/rule is added later.
