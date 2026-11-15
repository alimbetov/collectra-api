package io.collectra.api.shared.security;

/** Server-owned credential authority during migration. Never read from request headers. */
public enum CredentialAuthorityMode {
    LEGACY,
    MIGRATION,
    KEYCLOAK_ONLY
}
