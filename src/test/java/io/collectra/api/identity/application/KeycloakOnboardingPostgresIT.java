package io.collectra.api.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.collectra.api.AbstractIntegrationTest;
import io.collectra.api.identity.domain.SystemRole;
import io.collectra.api.identity.domain.UserAccount;
import io.collectra.api.identity.infrastructure.UserAccountRepository;
import io.collectra.api.shared.security.CredentialAuthorityMigrationService;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Real PostgreSQL/Liquibase onboarding reservation test. The Keycloak Admin API is mocked; real
 * Keycloak SMTP verification is a separate browser/integration acceptance gate.
 */
class KeycloakOnboardingPostgresIT extends AbstractIntegrationTest {
    private static final String ISSUER = "https://id.example/realms/shared";

    @Autowired TenantRepository tenants;
    @Autowired UserAccountRepository users;
    @Autowired JdbcClient db;

    private UserAccount user() {
        Tenant tenant =
                tenants.saveAndFlush(
                        new Tenant("onboard-" + UUID.randomUUID(), "Onboarding regression"));
        return users.saveAndFlush(
                new UserAccount(
                        tenant.getId(),
                        "onboard-" + UUID.randomUUID() + "@example.invalid",
                        "test-hash",
                        SystemRole.TENANT_USER));
    }

    private KeycloakOnboardingService service(KeycloakOnboardingClient keycloak) {
        return new KeycloakOnboardingService(
                db, users, keycloak, mock(CredentialAuthorityMigrationService.class), ISSUER);
    }

    private static HttpClientErrorException conflict() {
        return HttpClientErrorException.create(
                HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, new byte[0],
                StandardCharsets.UTF_8);
    }

    @Test
    void repeatedRegistrationReusesReservationAndDoesNotResendDuringCooldown() {
        UserAccount user = user();
        KeycloakOnboardingClient keycloak = mock(KeycloakOnboardingClient.class);
        KeycloakOnboardingService onboarding = service(keycloak);

        UUID first = onboarding.registerExisting(user.getId());
        UUID subject =
                db.sql("SELECT subject_id FROM collectra_keycloak_onboarding WHERE id=:id")
                        .param("id", first)
                        .query(UUID.class)
                        .single();

        doThrow(conflict()).when(keycloak).createUnverified(subject, user.getEmail());
        when(keycloak.matchesReservedIdentity(subject, user.getEmail())).thenReturn(true);

        UUID second = onboarding.registerExisting(user.getId());

        assertThat(second).isEqualTo(first);
        assertThat(db.sql(
                        "SELECT count(*) FROM collectra_keycloak_onboarding WHERE local_user_id=:id")
                        .param("id", user.getId())
                        .query(Long.class)
                        .single())
                .isEqualTo(1L);
        verify(keycloak, times(2)).createUnverified(subject, user.getEmail());
        verify(keycloak).matchesReservedIdentity(subject, user.getEmail());
        verify(keycloak, times(1)).sendVerifyEmail(subject);
    }

    @Test
    void mismatchedKeycloakIdentityCannotTriggerEmailOrVerifiedLink() {
        UserAccount user = user();
        KeycloakOnboardingClient keycloak = mock(KeycloakOnboardingClient.class);
        KeycloakOnboardingService onboarding = service(keycloak);

        doThrow(conflict()).when(keycloak).createUnverified(any(UUID.class), eq(user.getEmail()));
        when(keycloak.matchesReservedIdentity(any(UUID.class), eq(user.getEmail())))
                .thenReturn(false);

        assertThatThrownBy(() -> onboarding.registerExisting(user.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reserved Keycloak subject conflict");

        assertThat(db.sql(
                        "SELECT count(*) FROM collectra_keycloak_onboarding WHERE local_user_id=:id AND status='PENDING' AND verification_requested_at IS NULL")
                        .param("id", user.getId())
                        .query(Long.class)
                        .single())
                .isEqualTo(1L);
        assertThat(db.sql(
                        "SELECT count(*) FROM collectra_identity_links WHERE collectra_user_id=:id")
                        .param("id", user.getId())
                        .query(Long.class)
                        .single())
                .isZero();
        verify(keycloak, never()).sendVerifyEmail(any(UUID.class));
    }
}
