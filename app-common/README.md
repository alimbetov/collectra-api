# App Common — provisional standalone Spring Boot service

APP-COMMON-R1.3 first implementation slice in existing branch of collectra-api.
This folder is a **separate Maven project** (not part of Collectra runtime).
Target independent repository/deployment is pending approval. Do not deploy as production IAM.

Implemented: Spring Boot 3.5.16 / Java 17 bootstrap; JWT signature/JWKS, issuer,
expiry and API audience validation on authenticated endpoints; public health check.
Not implemented: BFF, shared-realm provisioning, global revocation ledger,
notifications, migrations, PostgreSQL and full two-app SSO.

Run:
```sh
cd app-common
APP_COMMON_ISSUER_URI=https://keycloak.example/realms/shared \
APP_COMMON_JWKS_URI=https://keycloak.example/realms/shared/protocol/openid-connect/certs \
APP_COMMON_API_AUDIENCE=app-common-api mvn spring-boot:run
```

Run tests: `mvn test`. Three unit tests check accepted, wrong and absent audiences. These do not
prove signature verification or integration with real Keycloak; the real-dependency
integration gate remains pending.
No test execution or deployment is claimed by this commit.
