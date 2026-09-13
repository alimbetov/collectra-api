# Collectra FrontendWeb

`frontendweb/` is the browser client for the Collectra tenant workspace. It is intentionally colocated with the Spring Boot backend while remaining runtime-decoupled through `/api/v1/**`.

## Current slice

FW0 provides only the project shell:

- React + TypeScript + Vite;
- React Router application routes;
- TanStack Query client;
- common transport DTO primitives;
- HTTP client boundary;
- desktop-first application shell;
- local `/api` proxy to the backend.

Business screens and authentication are not implemented in FW0. FW1 owns login/session/bootstrap and refresh behavior.

## Local development

```bash
npm install
npm run typecheck
npm run dev
```

By default Vite proxies `/api` to `http://localhost:8080`. To override the backend target for the Vite development server:

```bash
COLLECTRA_API_URL=http://localhost:8081 npm run dev
```

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
- Authentication token refresh is deliberately deferred to FW1.

The frozen contract is documented in `../docs/specs/frontend-react-api-contract.md`.
