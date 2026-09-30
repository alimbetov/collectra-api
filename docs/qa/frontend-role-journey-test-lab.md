# Frontend Role Journey Test Lab

Documentation lifecycle: ACTIVE CONTRACT — executable persona and authorization acceptance model.

## Scope

The frontend role journey lab verifies that workspace visibility, direct-route access and business command affordances follow the same capability boundaries enforced by backend controllers.

The executable frontend contracts are:

- `frontendweb/src/app/role-journey.matrix.ts` — tenant personas and route surfaces;
- `frontendweb/src/app/role-journey.matrix.test.ts` — positive/forbidden route and navigation simulation;
- `frontendweb/src/app/command-capability.matrix.ts` — mutation-to-permission mapping;
- `frontendweb/src/app/command-capability.matrix.test.ts` — command-level READ-vs-MANAGE simulation;
- `RequirePermission` / `RequireAnyPermission` tests — direct deep-link fail-closed behavior.

## Backend evidence bridge

The frontend lab does not replace server authorization. Backend evidence remains authoritative for HTTP enforcement and tenant isolation:

| Concern | Backend evidence |
|---|---|
| Business READ vs MANAGE | `FunctionalHardeningSecuritySmokeIntegrationTest.businessCoreReadAndManageCapabilitiesAreComposedExplicitly` |
| Cross-tenant object reads | `FunctionalHardeningSecuritySmokeIntegrationTest.alphaCannotReadBetaCustomerContractInvoicePaymentOrCollectionCase` |
| Cross-tenant references/filters | `FunctionalHardeningSecuritySmokeIntegrationTest.alphaCannotInjectBetaBusinessReferencesOrUseFiltersAsExistenceOracle` |
| Role/access baseline | `RoleAccessSmokeIntegrationTest` |
| Tenant RBAC administration | `TenantSecurityManagementIntegrationTest` |
| General authentication/authorization | `SecurityIntegrationTest` |
| Service-client trust zone | `ServiceClientSecurityIntegrationTest` |
| Message tenant/security boundary | `Slice10aP11MessageSecurityIntegrationTest` |

## Acceptance invariant

For every protected user journey:

```text
navigation visibility
    = direct-route capability
    = command affordance capability
    <= backend controller authorization
```

A frontend denial is UX hardening, not the security boundary. The backend must independently return 401/403/404 according to its authorization and non-disclosure contract.

## Current personas

Tenant Administrator, Receivables Manager, Collection Operator, Campaign Manager, Template Manager, Integration Administrator, Read-only Auditor and Minimal Tenant User.

Platform administration and machine service-client journeys remain separate trust zones and are not modeled as tenant-human personas.
