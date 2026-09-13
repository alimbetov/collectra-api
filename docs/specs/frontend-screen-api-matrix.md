# FrontendWeb — Screen to API Matrix

Status: ANALYSIS BASELINE

Purpose: связать UX с фактическим backend API до проектирования TypeScript DTO и React query/mutation layer.

## Authentication / bootstrap

| Screen / action | API | UI purpose |
|---|---|---|
| Tenant registration | `POST /api/v1/auth/tenants/register` | create tenant + first user/session |
| Login | `POST /api/v1/auth/login` | authenticate human user |
| Refresh | `POST /api/v1/auth/refresh` | renew access session |
| Logout | `POST /api/v1/auth/logout` | terminate refresh session |
| Logout all | `POST /api/v1/auth/logout-all` | revoke user sessions |
| Bootstrap current user | `GET /api/v1/identity/me` | identity/tenant membership context |
| Edit profile | `PATCH /api/v1/identity/me` | display name/locale/timezone |
| Change password | `POST /api/v1/identity/me/change-password` | security |
| Sessions | `GET /api/v1/identity/me/sessions` | session management |
| Revoke session | `DELETE /api/v1/identity/me/sessions/{id}` | session management |

## Dashboard

| Screen | API |
|---|---|
| Dashboard shell/KPIs | `GET /api/v1/dashboard/summary` |
| Receivables card | `GET /api/v1/dashboard/receivables` |
| Delivery card | `GET /api/v1/dashboard/delivery` |
| Collections card | `GET /api/v1/dashboard/collections` |

## Customers

| Screen / action | API |
|---|---|
| Customer list/search/filter | `GET /api/v1/customers` |
| Customer detail | `GET /api/v1/customers/{id}` |
| Create customer | `POST /api/v1/customers` |
| Edit profile | `PUT /api/v1/customers/{id}` |
| Change status | `PATCH /api/v1/customers/{id}/status` |
| Email list | `GET /api/v1/customers/{id}/emails` |
| Add email | `POST /api/v1/customers/{id}/emails` |
| Change email metadata/state | `PATCH /api/v1/customers/{id}/emails/{emailId}` |
| Phone list | `GET /api/v1/customers/{id}/phones` |
| Add phone | `POST /api/v1/customers/{id}/phones` |
| Change phone metadata/state | `PATCH /api/v1/customers/{id}/phones/{phoneId}` |
| Add segment membership | `POST /api/v1/customers/{id}/segments/{segmentId}` |
| Remove segment membership | `DELETE /api/v1/customers/{id}/segments/{segmentId}` |

Customer segments have a dedicated `CustomerSegmentController` and should supply segment list/detail/manage/selector screens. Exact DTOs are derived in the React contract phase.

## Contracts

| Screen / action | API |
|---|---|
| Contract list | `GET /api/v1/contracts` |
| Contract detail | `GET /api/v1/contracts/{id}` |
| Create | `POST /api/v1/contracts` |
| Edit | `PUT /api/v1/contracts/{id}` |
| Suspend | `POST /api/v1/contracts/{id}/suspend` |
| Activate | `POST /api/v1/contracts/{id}/activate` |
| Close | `POST /api/v1/contracts/{id}/close` |
| Cancel | `POST /api/v1/contracts/{id}/cancel` |

## Receivables — invoices

| Screen / action | API |
|---|---|
| Invoice list/filter | `GET /api/v1/invoices` |
| Invoice detail | `GET /api/v1/invoices/{id}` |
| Create invoice | `POST /api/v1/invoices` |
| Invoice allocations | `GET /api/v1/invoices/{id}/allocations` |

The invoice response already contains authoritative amounts/status/overdue projection. React must display them rather than recompute business state.

## Receivables — payments

| Screen / action | API |
|---|---|
| Payment list/filter | `GET /api/v1/payments` |
| Payment detail | `GET /api/v1/payments/{id}` |
| Create payment | `POST /api/v1/payments` |
| Payment allocations | `GET /api/v1/payments/{id}/allocations` |
| Allocate | `POST /api/v1/payments/{id}/allocations` |
| Reverse allocation | `POST /api/v1/payments/{paymentId}/allocations/{allocationId}/reverse` |

Allocation UI must preserve a stable `commandId` UUID when replaying the same user intent.

## Collections

| Screen / action | API |
|---|---|
| Case list | `GET /api/v1/collection-cases` |
| Case detail | `GET /api/v1/collection-cases/{caseId}` |
| Create case | `POST /api/v1/collection-cases` |
| Update priority/assignee | `PUT /api/v1/collection-cases/{caseId}` |
| Start | `POST /api/v1/collection-cases/{caseId}/start` |
| Hold | `POST /api/v1/collection-cases/{caseId}/hold` |
| Close | `POST /api/v1/collection-cases/{caseId}/close` |
| Promise list | `GET /api/v1/collection-cases/{caseId}/promises` |
| Create promise | `POST /api/v1/collection-cases/{caseId}/promises` |
| Fulfill promise | `POST /api/v1/collection-cases/{caseId}/promises/{promiseId}/fulfill` |
| Break promise | `POST /api/v1/collection-cases/{caseId}/promises/{promiseId}/break` |
| Cancel promise | `POST /api/v1/collection-cases/{caseId}/promises/{promiseId}/cancel` |
| Dispute list | `GET /api/v1/collection-cases/{caseId}/disputes` |
| Create dispute | `POST /api/v1/collection-cases/{caseId}/disputes` |
| Resolve dispute | `POST /api/v1/collection-cases/{caseId}/disputes/{disputeId}/resolve` |
| Cancel dispute | `POST /api/v1/collection-cases/{caseId}/disputes/{disputeId}/cancel` |
| Action list | `GET /api/v1/collection-cases/{caseId}/actions` |
| Create action | `POST /api/v1/collection-cases/{caseId}/actions` |
| Complete action | `POST /api/v1/collection-cases/{caseId}/actions/{actionId}/complete` |
| Cancel action | `POST /api/v1/collection-cases/{caseId}/actions/{actionId}/cancel` |
| Timeline | `GET /api/v1/collection-cases/{caseId}/timeline` |

## Templates

| Screen / action | API |
|---|---|
| Template list | `GET /api/v1/templates` |
| Create template | `POST /api/v1/templates` |
| Rename template | `PUT /api/v1/templates/{id}` |
| Archive template | `DELETE /api/v1/templates/{id}` |
| Version list | `GET /api/v1/templates/{id}/versions` |
| Create version | `POST /api/v1/templates/{id}/versions` |
| Edit version | `PUT /api/v1/templates/versions/{id}` |
| Validate | `POST /api/v1/templates/versions/{id}/validate` |
| Preview | `POST /api/v1/templates/versions/{id}/preview` |
| Publish | `POST /api/v1/templates/versions/{id}/publish` |
| Reopen | `POST /api/v1/templates/versions/{id}/reopen` |
| Archive version | `POST /api/v1/templates/versions/{id}/archive` |

Field catalogue and preset/builder controllers provide supporting authoring APIs; their exact request/response types will be included in the DTO audit.

## Campaigns

| Screen / action | API |
|---|---|
| Campaign list | `GET /api/v1/campaigns` |
| Campaign detail | `GET /api/v1/campaigns/{campaignId}` |
| Create | `POST /api/v1/campaigns` |
| Activate | `POST /api/v1/campaigns/{campaignId}/activate` |
| Prepare run | `POST /api/v1/campaigns/{campaignId}/runs` |
| Run list | `GET /api/v1/campaigns/{campaignId}/runs` |
| Recipient list | `GET /api/v1/campaigns/{campaignId}/runs/{runId}/recipients` |
| Eligibility recheck | `POST /api/v1/campaigns/runs/{runId}/eligibility-recheck` |

Creation requires `name`, `templateVersionId`, `channel`, optional `scheduledAt`, and `CampaignSelection`.

## Messages / delivery monitoring

| Screen / action | API |
|---|---|
| Run message list | `GET /api/v1/campaigns/{campaignId}/runs/{runId}/messages` |
| Message detail | `GET /api/v1/campaigns/{campaignId}/runs/{runId}/messages/{messageId}` |

Current public message UI is primarily read/monitoring. Filters: status, channel, customerId, page, size.

Channel provider is deliberately hidden behind this provider-neutral API. Mock/real adapters must not require different React screens.

## Imports

| Screen / action | API |
|---|---|
| Upload tabular file | `POST /api/v1/import-batches` multipart |
| Submit JSON | `POST /api/v1/import-batches/json` |
| Submit XML | `POST /api/v1/import-batches/xml` |
| Batch result | `GET /api/v1/import-batches/{batchId}` |

All create operations use `Idempotency-Key` and require mapping profile version + template version + output formats.

Supporting setup APIs exist for source schemas and mapping profiles and should be exposed through configuration screens only to users with appropriate permissions.

## Files

| Screen / action | API |
|---|---|
| Upload | `POST /api/v1/files` multipart |
| Metadata | `GET /api/v1/files/{fileId}` |
| Download content | `GET /api/v1/files/{fileId}/content` |
| Presigned download | `GET /api/v1/files/{fileId}/download-url` |
| Delete | `DELETE /api/v1/files/{fileId}` |

## Administration

Current identity module exposes controllers for:

```text
TenantInvitationController
TenantMembershipController
TenantPermissionController
TenantRoleController
AccountLifecycleController
```

React administration feature should be generated from their concrete endpoints/DTOs during the next API-contract pass. PlatformAdministrator/PlatformAuth surfaces belong to a separate platform admin route tree and must not be mixed into tenant workspace.

## API gaps / design checks before DTO freeze

Before generating final TypeScript contracts, explicitly audit:

1. CustomerSegment endpoint/DTO details and permission semantics.
2. Complete Tenant administration endpoint matrix.
3. SourceSchema and MappingProfile endpoint/DTO details.
4. Template builder/preset/field catalogue exact contracts.
5. File category enum and metadata model.
6. Dashboard response shapes.
7. CampaignSelection exact polymorphic/structured shape.
8. Message list/detail exact response fields and status enums.
9. Common page response differences (`Page` vs slice-style response).
10. Current `ProblemDetail` extension fields and business error codes.

These are contract-audit tasks, not reasons to redesign the business process.
