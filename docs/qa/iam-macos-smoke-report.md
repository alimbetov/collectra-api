# IAM macOS functional smoke report

Run UTC: 2026-11-14 17:53:06  
Source HEAD: `b2397c7e8e9ea1c0015fb9bfec562bad696f196c`  
Working tree: `1 changed paths (including this report)`  
macOS: `15.6` / `arm64`  
Java: `openjdk version "17.0.9" 2023-10-17`  
Docker: `28.3.2`  
Keycloak: `Keycloak 26.4.2`  

**Collectra IAM-MVP smoke: PASS (SMOKE-01..06 and 08).** Full two-Consumer acceptance: **BLOCKED** only at SMOKE-07 because no real App-B implementation is available. This is not a failure of the executed Collectra path, and the operation correctly remains `PROPAGATING` rather than falsely reporting `ENFORCED`.

| Scenario | Result | Evidence |
| --- | --- | --- |
| SMOKE-01 | PASS | Keycloak password grant JWT accepted by Collectra tenant API (HTTP 200) |
| SMOKE-02 | PASS | user denied platform block (403) and foreign tenant selector (401) |
| SMOKE-03 | PASS | App Common HTTP block committed BLOCKED v2 in PostgreSQL |
| SMOKE-04 | PASS | pre-block JWT rejected by Collectra after consumer synchronization (HTTP 401) |
| SMOKE-05 | PASS | OAuth Consumer fetched BLOCKED v2 over HTTP |
| SMOKE-06 | PASS | service OAuth HTTP ACK persisted v2 in PostgreSQL |
| SMOKE-07 | BLOCKED | no real App-B executable in repository; no second Consumer/ACK simulated |
| SMOKE-08 | PASS | idempotent replay/restart preserved BLOCKED v2; Keycloak disabled user; operation PROPAGATING (not ENFORCED) |

Bootstrap prerequisites: operator access state, Consumer OAuth binding and Collectra verified identity link were provisioned in disposable PostgreSQL after Keycloak and tenant creation. They are setup, not HTTP E2E evidence. No App-B implementation was present in this repository; a second ACK was not simulated.

Observed defects and fixes: invalid JSON escaping in an App Common ACK test; ARM64-incompatible Java image; missing Keycloak user `sub` and admin client-role mappers; Keycloak subject used as a local Collectra user ID; live JAR replacement on rerun. The public issuer and internal Keycloak URL are configured separately.

Focused regression checks: App Common `RevocationHttpAckPostgresIT` 2/2 PASS; Collectra `CurrentUserControllerPrincipalTest` 2/2 PASS. The changed Collectra Java files were formatted with the repository Spotless formatter; the normal ratchet check cannot resolve Git metadata in this linked worktree, so no full Spotless PASS is claimed.

Remaining limitations: App-B is absent, so two-Consumer ENFORCED acceptance is BLOCKED. Identity linking and Consumer OAuth binding currently require privileged bootstrap; no end-user HTTP linking workflow is claimed. Browser authorization-code/PKCE login was outside this smoke.

Run: `./scripts/smoke-iam-macos.sh`  
Stop: `./scripts/stop-iam-macos.sh`  
Local secrets: `.env` (Git-ignored); safe template: `.env.example`.
