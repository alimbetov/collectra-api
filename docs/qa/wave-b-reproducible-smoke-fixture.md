# Wave B — Reproducible Smoke Fixture

Development decision: use one optional CI configuration secret named `COLLECTRA_SMOKE_CONFIG` containing a JSON payload for browser/external-environment smoke orchestration. The repository contains only the non-secret schema/example in `src/test/resources/smoke/functional-hardening-ci-config.example.json`.

The current Testcontainers integration smoke does not require persisted CI credentials: it provisions Alpha/Beta tenants and restricted users through public APIs on every run. This is safer and reproducible.

Rules:
- one CI secret payload is acceptable during development;
- payload must not use one shared password for every persona;
- bootstrap generates per-identity credentials unless explicit ephemeral credentials are injected;
- never echo the payload, JWT, refresh token or service secret;
- GitHub Actions secret is environment-scoped when browser smoke is introduced;
- production must never consume this development smoke config.

Wave-B first executable suite:
`FunctionalHardeningSecuritySmokeIntegrationTest`
- Alpha cannot read Beta customer/contract/invoice/payment/collection case;
- restricted ROLE_HUMAN characterization demonstrates current vertical permission boundary;
- expected 404 for foreign tenant resources;
- public auth flows issue real JWTs so security filter + TenantContext + service/repository predicates are exercised together.

The vertical test is intentionally characterization, not an acceptance of broad access. Its 2xx assertions make FH-005 reproducible before the capability-permission remediation is designed.
