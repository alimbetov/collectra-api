# Collectra FrontendWeb

`frontendweb/` is the browser client for the Collectra tenant workspace and platform administration surfaces. It is colocated with the Spring Boot backend while remaining runtime-decoupled through `/api/v1/**`.

> Historical note: this README originally described the FW0 shell. The repository has since implemented multiple business slices. Current architecture/readiness is tracked by `docs/architecture/collectra-system-overview.md` and `docs/architecture/technical-audit-ledger.md`.

## Current application surface

The repository now contains application routes/pages and API entities for, among other areas:

- authentication and tenant workspace bootstrap;
- dashboard;
- customers, contacts, segments and contracts;
- receivables;
- collections;
- campaigns;
- message monitoring;
- imports;
- file registry/upload;
- Integration Setup Center, including Integration Sources, Source Schemas, Mapping Profiles and Service Clients;
- Template Studio, including templates, version editor and template assets;
- platform administration.

Presence of a page is not a production-readiness claim. Current subsystem audit status is maintained in the technical audit ledger.

## Local development

```bash
npm install
npm run typecheck
npm run dev
```

By default Vite proxies `/api` to `http://localhost:8080`. `API_BASE_URL` is the standard backend origin used by local Vite proxy and build-time browser API calls:

```bash
API_BASE_URL=http://localhost:8081 npm run dev
```

For deployed browser builds, expose the same value to Vite as `VITE_API_BASE_URL` during build time.

## Build

```bash
npm run build
```

## Architecture rules

- API transport contracts live in `src/shared/api` or feature-local `contracts.ts` files.
- Feature business code must stay in its own `features/<feature>` module.
- The browser must not derive authoritative financial or workflow state.
- Tenant ID must not be supplied by ordinary workspace screens; tenant context comes from the authenticated backend session/token.
- Provider-specific delivery DTOs must not leak into frontend contracts.

The frozen contract is documented in `../docs/specs/frontend-react-api-contract.md`.
