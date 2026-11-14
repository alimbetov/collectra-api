# APP-COMMON-R1.4 — real Keycloak audience isolation harness

Local development only; Keycloak `start-dev` is NOT a production deployment.
This harness deliberately uses **two confidential machine test clients**, not web BFFs.
It proves JWT signature, issuer and audience isolation, **not browser SSO**.

1. Export unique secrets: `KEYCLOAK_DB_PASSWORD`, `KC_BOOTSTRAP_ADMIN_PASSWORD`, `COLLECTRA_TEST_CLIENT_SECRET`, `APP_B_TEST_CLIENT_SECRET`.
2. `docker compose -f app-common/infra/compose.identity.yaml up -d`.
3. Import `app-common/infra/realm-shared.json` into Keycloak via Admin UI (Create realm → Browse file). Import once; ensure no duplicate realm.
4. `bash app-common/infra/provision-test-clients.sh`.
5. In two shells, run `cd app-common && APP_COMMON_API_AUDIENCE=collectra-api APP_COMMON_PORT=8090 mvn spring-boot:run` and `APP_COMMON_API_AUDIENCE=app-b-api APP_COMMON_PORT=8091 mvn spring-boot:run`.
6. `bash app-common/infra/check-audience.sh`.

Issuer is `http://localhost:8180/realms/shared`; configure a single stable hostname for Docker host + browser. Test scripts must run from the host. This test probes the same App Common code under two different audience settings; **it is not evidence that Collectra's existing API has been migrated to Keycloak**.

Fail the acceptance gate if any cross-audience request returns HTTP 200. Save actual logs and environment image digests for independent S2. PostgreSQL registry and global blocking are the **next** implementation slice, not completed here.
