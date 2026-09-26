# Code-Level Implementation Guide — Pre-VC9 Remediation

Status: NORMATIVE IMPLEMENTATION COMPANION
Use with: pre-vc9-functional-hardening-remediation-spec.md
Rule: examples show the intended pattern against current Collectra code. Codex must inspect current signatures before editing and preserve existing conventions where equivalent. Do not copy an example blindly if the repository has evolved.

## 0. Current-code anchors

Observed at remediation baseline:
- CustomerController, ContractController, ReceivableController and CollectionController use class-level `hasAuthority('ROLE_HUMAN')`; this is the concrete FH-005 hotspot.
- Campaign/Templates/Files already demonstrate capability-oriented authorization.
- Collections backend already exposes queue/detail/promises/disputes/actions/timeline; do not create a parallel collections API merely to build the UI.
- DecimalString is already the API money boundary; frontend `DecimalString = string`.
- React Query key factories exist for customer/receivable and should be mirrored for Collections.
- Liquibase master currently ends at 050; create a new forward migration (expected next number 051 at baseline) rather than editing old permission migrations.
- CommunicationMessagingConfig and OutboxEventRouter already define the production message-delivery exchange/routing path.
- Existing delivery integration tests can call listener/worker in-process; that is not RabbitMQ wiring evidence.

## WP-01 — Business Core RBAC

### Backend target

Keep `ROLE_HUMAN` as trust-zone protection if useful, but add capability guards at method/class level. Reads and mutations must differ.

Example pattern:

```java
@RestController
@RequestMapping("/api/v1/customers")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
class CustomerController {

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    CustomerPage list(...) { ... }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    CustomerResponse get(...) { ... }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    CustomerResponse create(...) { ... }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    CustomerResponse update(...) { ... }
}
```

Apply the same semantic split:
- Contract GET/list -> CONTRACT_READ; create/update/lifecycle -> CONTRACT_MANAGE.
- Invoice/payment/allocation GET -> RECEIVABLE_READ; create invoice/payment/allocation/reversal -> RECEIVABLE_MANAGE.
- Collection list/detail/history -> COLLECTION_READ; create/update/lifecycle/promise/dispute/action commands -> COLLECTION_MANAGE.

Do not infer manage from read in Java code. If a role should have both, assign both permissions.

### Liquibase pattern

Create a new migration, do not edit 002/017/etc.

```sql
--liquibase formatted sql
--changeset collectra:051-business-core-permissions
INSERT INTO permissions(id, code, module, description) VALUES
  (..., 'CUSTOMER_READ', 'customer', 'Read customers'),
  (..., 'CUSTOMER_MANAGE', 'customer', 'Manage customers'),
  (..., 'CONTRACT_READ', 'contract', 'Read contracts'),
  (..., 'CONTRACT_MANAGE', 'contract', 'Manage contracts'),
  (..., 'RECEIVABLE_READ', 'receivable', 'Read receivables'),
  (..., 'RECEIVABLE_MANAGE', 'receivable', 'Manage receivables'),
  (..., 'COLLECTION_READ', 'collection', 'Read collections'),
  (..., 'COLLECTION_MANAGE', 'collection', 'Manage collections');
```

Use repository UUID conventions and idempotency assumptions of existing migrations. Grant TENANT_ADMIN all new capabilities. Do not automatically grant TENANT_USER all MANAGE capabilities; smoke roles/personas are explicit compositions.

### Frontend target

Wrap routes with RequirePermission and individual mutation controls with PermissionGuard. Example:

```tsx
{
  path: 'customers',
  element: (
    <RequirePermission permission="CUSTOMER_READ">
      <CustomersPage />
    </RequirePermission>
  ),
}
```

Inside a read page:

```tsx
<PermissionGuard permission="CUSTOMER_MANAGE">
  <Button onClick={openEdit}>Edit</Button>
</PermissionGuard>
```

Direct URL remains protected by backend; frontend guard is not security.

### Security tests

Prefer extending/adding a parameterized security smoke rather than dozens of copy-paste classes.

```java
@ParameterizedTest(name = "{0} read={1} manage={2}")
@MethodSource("businessCoreCases")
void businessCoreCapabilityMatrix(...) {
   // obtain token for role with exact permissions
   // assert GET expected
   // snapshot state
   // assert mutation expected
   // on denial assert state unchanged
}
```

Must include restricted role with only USER_READ to prove the current characterization becomes 403.

## WP-02 — Collections Workspace

### Do not rebuild backend first

CollectionController already has:
- GET /api/v1/collection-cases with filters/paging/sort;
- GET /{caseId};
- POST/PUT case;
- start/hold/close;
- promises + fulfill/break/cancel;
- disputes + resolve/cancel;
- actions + complete/cancel;
- timeline.

Start frontend by creating:
```text
frontendweb/src/entities/collection/
  api/collection.api.ts
  api/collection.queries.ts
  model/collection.types.ts
frontendweb/src/features/collections/
  model/collection-list-filters.ts
  model/collection-mutations.ts
  ui/CollectionFilters.tsx
  ui/CollectionTable.tsx
  ui/PromiseDialog.tsx
  ui/DisputeDialog.tsx
  ui/ActionDialog.tsx
frontendweb/src/pages/collections/
  CollectionsPage.tsx
  CollectionCasePage.tsx
```

Exact naming may follow existing feature conventions.

### Query-key pattern

```ts
export const collectionKeys = {
  all: ['collections'] as const,
  lists: () => [...collectionKeys.all, 'list'] as const,
  list: (query: CollectionListQuery) => [...collectionKeys.lists(), query] as const,
  detail: (id: string) => [...collectionKeys.all, 'detail', id] as const,
  promises: (id: string, page: number) => [...collectionKeys.detail(id), 'promises', page] as const,
  disputes: (id: string, page: number) => [...collectionKeys.detail(id), 'disputes', page] as const,
  actions: (id: string, page: number) => [...collectionKeys.detail(id), 'actions', page] as const,
  timeline: (id: string, page: number) => [...collectionKeys.detail(id), 'timeline', page] as const,
};
```

After mutation invalidate detail plus affected histories/list. Do not use a global reload.

### Route replacement

Replace current PlaceholderPage route with COLLECTION_READ guarded routes. Add detail route:
```tsx
{ path: 'collections', element: <RequirePermission permission="COLLECTION_READ"><CollectionsPage /></RequirePermission> },
{ path: 'collections/:caseId', element: <RequirePermission permission="COLLECTION_READ"><CollectionCasePage /></RequirePermission> },
```

Mutation buttons require COLLECTION_MANAGE.

### 409 behavior

Use the current ProblemDetail/API error pattern. On optimistic conflict:
1. show conflict notification;
2. invalidate/refetch case/detail;
3. preserve user input where safe for manual re-apply;
4. never automatically replay a stale command.

## WP-03 — Tenant Isolation

### Repository/service invariant

Existing pattern such as `findByIdAndTenantId` is canonical. Never:
```java
repository.findById(id)
```
for tenant-owned API lookup followed by a Java tenant comparison if a scoped repository query can enforce it.

Foreign body references must also be resolved with tenant scope:
```java
Customer customer = customerRepository.findByIdAndTenantId(customerId, tenantId)
    .orElseThrow(notFound());
```

### Parameterized attack matrix

Define resource cases with:
- create fixture in Beta;
- Alpha request factory;
- Beta-state snapshot/assertion.

Examples:
```text
GET /customers/{betaCustomer}
GET /contracts?customerId={betaCustomer}
POST /invoices { customerId: betaCustomer }
POST /collection-cases { customerId: betaCustomer, invoiceId: betaInvoice }
POST campaign/update referencing beta template/customer/segment
template asset referencing beta file
```

Denied mutation assertion must query Beta with Beta context/token afterward and prove unchanged state.

## WP-04 — Financial Correctness

ReceivableController already transports amounts through DecimalString. Preserve that.

### Required service invariants

For allocation:
```text
payment.tenant == invoice.tenant == auth tenant
payment.customer == invoice.customer
payment.currency == invoice.currency
amount > 0
amount <= payment.unallocated
amount <= invoice.outstanding
(commandId, paymentId) replay with same intent => same effect
same id + different intent => conflict
```

Concurrency must be solved transactionally in ReceivableService/repository constraints/locks/versioning, not with synchronized or test sleeps.

### Frontend
Never:
```ts
const outstanding = Number(original) - Number(paid)
```
Use backend `outstandingAmount`. DecimalString stays string for display/input validation.

### Reversal
Require version + reason as current API already does. Verify exact restoration and audit fields `reversedAt/reversedBy/reversalReason`.

## WP-05 — Ingestion/Import

Use existing IngestionController/ApplicationService/Worker, Import controllers/services and IngestionMessagingConfig. Do not create a second ingestion pipeline.

Test through real service authentication for the smoke layer. Direct service tests remain valid lower-layer tests.

Idempotency test shape:
```text
POST key=K body=A -> accepted/success
POST key=K body=A -> same logical operation/no duplicate domain row
POST key=K body=B -> conflict
```

Canonicalization asserts actual persisted Customer/Invoice/Payment fields, not merely batch SUCCESS.

For diagnostics assert bounded record errors and masking policy; never persist/log raw secrets or unnecessary source payload.

## WP-06 — Customer 360

Current CustomerDetailPage already has overview/contacts/contracts routes. Extend by composition, not a giant all-data endpoint unless measurement proves necessary.

Preferred architecture:
- bounded backend projections for each tab/domain;
- stable deep links using IDs;
- React Query independent keys;
- invalidate only affected domains.

If a screen requires N requests per row, add a bounded backend projection/query service rather than frontend fan-out.

Suggested tabs/links:
Overview | Contacts | Contracts | Receivables | Collections | Communications.
Only expose tabs whose domain is MVP and permission is present.

## WP-07 — Content/Campaign/File SoD

Follow existing permissions:
CAMPAIGN_READ/MANAGE,
TEMPLATE_READ/MANAGE/PUBLISH,
FILE_READ/UPLOAD/DELETE/ADMIN.

Reference validation example:
campaign using templateVersion must resolve the published version in the same tenant; template asset must resolve StoredFile in same tenant and acceptable state/category.

Do not grant TEMPLATE_PUBLISH to CAMPAIGN_MANAGE merely because campaigns need templates.

Unsafe content tests should target HtmlTemplatePolicy/renderer plus API/frontend preview boundary.

## WP-08 — Eligibility + Delivery

Use CampaignEligibilityService, CampaignMessageMaterializer, MessageDeliveryRequestService/Worker and adapter abstraction already present.

Golden race:
```text
prepare overdue recipient
-> create/post payment
-> allocate fully
-> eligibility recheck
-> recipient SKIPPED with PAID reason
-> zero delivery request/provider call
```

### Ambiguous acceptance

Do not map accept-then-timeout to an ordinary retryable "definitely not sent" result.

Target contract concept:
```java
sealed interface DeliveryOutcome {
  record Accepted(String providerMessageId) implements DeliveryOutcome {}
  record Rejected(String code, String message) implements DeliveryOutcome {}
  record RetryableBeforeAcceptance(String code, String message) implements DeliveryOutcome {}
  record AcceptanceUnknown(String deliveryKey, String code, String message) implements DeliveryOutcome {}
}
```

This is illustrative: adapt to current adapter types. The invariant is important, not these exact class names. `AcceptanceUnknown` must not cause blind physical resend without provider idempotency/reconciliation semantics.

## WP-09 — Recovery / Outbox / Concurrency

Keep a single state-transition authority. Recovery must not independently mutate Message/CampaignRun counters in a different semantic path from normal worker completion.

OutboxPublisher already claims and routes OutboxEvent. Verify:
```text
two publishers claim same ready set -> one owner per event
publish succeeds -> SENT exactly once
publish failure -> retry/dead policy
duplicate broker event -> downstream business idempotent
late event after terminal message -> no backward transition
```

Use database concurrency tests with barriers/latches, not sleeps.

## WP-10 — Real RabbitMQ Testcontainers

CommunicationMessagingConfig constants are the topology source:
- exchange collectra.communication
- queue collectra.communication.message-delivery
- routing key message.delivery.requested
- DLX/dead queue.

OutboxEventRouter maps MessageDeliveryRequested to that route.

Add Testcontainers RabbitMQ module compatible with the project's Testcontainers version. Test shape:

```java
@Testcontainers
@SpringBootTest
class RabbitMqMessageDeliverySmokeIntegrationTest {
  @Container
  static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:<pinned-compatible-version>");

  @DynamicPropertySource
  static void rabbitProps(DynamicPropertyRegistry r) {
    r.add("spring.rabbitmq.host", rabbit::getHost);
    r.add("spring.rabbitmq.port", rabbit::getAmqpPort);
  }

  @Test
  void outboxEventTraversesRealBrokerAndPersistsDeliveryResult() {
    // arrange tenant/campaign/message using canonical fixtures/APIs
    // create MESSAGE_DELIVERY_REQUESTED through application path
    // trigger/await outbox publisher if scheduler is disabled in test
    // await observable persisted terminal state
    // assert adapter call count=1 and CampaignRun counters
  }
}
```

Do not publish directly to the queue as the only test; that would skip OutboxEventRouter/publisher. Do not call listener directly.

Pin image/version consistent with CI policy; never use floating latest.

## WP-11 — Frontend Coherence

Router currently has unguarded Customers/Contracts/Receivables and Collections placeholder. Align all with accepted capabilities.

For each primary page use MSW/API mocks to prove:
- authorized load;
- forbidden route;
- empty/error;
- URL filter serialization;
- mutation invalidation;
- 409 behavior where versioned.

Follow existing page tests such as CustomersPage.test.tsx / ContractsPage.test.tsx rather than inventing a second frontend test harness.

Platform analytics/audit/operations placeholders: analytics remains VC-9; audit/operations are deferred per D04 and must not appear as finished navigation.

## WP-12 — Golden Journey

Build one orchestration test, but keep detailed assertions in focused suites. The Golden test proves wiring, not every edge case.

Prefer public HTTP/service-auth boundaries for setup and actions. Repository reads may be used for final invariant verification where no public read exists; do not bypass the business path to create all state directly.

Sequence:
tenant/register -> permissions/personas -> service client -> schema/mapping/source -> service token -> ingest customer/invoice/payment -> allocation -> collection -> template publish -> campaign -> recheck -> materialize -> outbox -> real RabbitMQ -> worker -> deterministic adapter -> monitoring.

At critical steps assert Alpha cannot use Beta IDs.

## 13. Concrete files likely to change

Backend:
```text
customer/api/CustomerController.java
contract/api/ContractController.java
receivable/api/ReceivableController.java
collection/api/CollectionController.java
identity permission seed/migrations
shared/outbox/*
communication/* delivery/messaging/recovery
campaign/* eligibility/materialization
integration/* ingestion
```

Frontend:
```text
app/router.tsx
navigation/menu definitions
features/auth PermissionGuard/RequirePermission usage
new entities/collection + features/collections + pages/collections
customer detail composition
receivable mutation/query UI
page tests/MSW fixtures
```

DB/test:
```text
db/changelog/changes/051-...sql (expected next at baseline; re-evaluate before creating)
db.changelog-master.yaml
FunctionalHardeningSecuritySmokeIntegrationTest
RoleAccessSmokeIntegrationTest
CustomerReceivableCoreIntegrationTest
CampaignStabilizationIntegrationTest
Slice10aP08ConcurrencyIntegrationTest
new RabbitMQ broker smoke
new/expanded frontend page tests
```

This is a candidate impact map, not permission to modify every file. Keep diffs minimal and cohesive.

## 14. Forbidden implementation shortcuts

- no new parallel API when existing controller already supports the workflow;
- no role-name checks in React or domain services instead of permissions;
- no `findById` then tenant check for tenant-owned request paths when scoped lookup is possible;
- no JS Number for authoritative money;
- no Thread.sleep for async/concurrency tests;
- no direct listener call presented as RabbitMQ integration;
- no test profile that bypasses security for security smoke;
- no editing old Liquibase migration to add new production permission;
- no blanket TENANT_USER all-permissions grant to make UI work;
- no catch-and-return-200 for conflicts/errors;
- no weakening 404/403 assertions to current behavior without checking accepted contract;
- no giant Golden Journey replacing focused regression tests.
