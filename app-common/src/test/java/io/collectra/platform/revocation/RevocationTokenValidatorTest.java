package io.collectra.platform.revocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class RevocationTokenValidatorTest {
    private Jwt jwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test")
            .header("alg", "RS256")
            .issuer("https://id.example/realms/shared")
            .subject("user-1")
            .audience(List.of("app-common-api"))
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
    }

    @Test
    void rejectsPreviouslyIssuedJwtWhenBlocked() {
        SubjectAccessService service = mock(SubjectAccessService.class);
        when(service.isAllowed("https://id.example/realms/shared", "user-1")).thenReturn(false);
        assertThat(new RevocationTokenValidator(service).validate(jwt()).hasErrors()).isTrue();
    }

    @Test
    void rejectsJwtWhenDatabaseUnavailable() {
        SubjectAccessService service = mock(SubjectAccessService.class);
        when(service.isAllowed(anyString(), anyString())).thenThrow(new IllegalStateException("database unavailable"));
        assertThat(new RevocationTokenValidator(service).validate(jwt()).hasErrors()).isTrue();
    }

    @Test
    void allowsJwtWhenLedgerAllows() {
        SubjectAccessService service = mock(SubjectAccessService.class);
        when(service.isAllowed("https://id.example/realms/shared", "user-1")).thenReturn(true);
        assertThat(new RevocationTokenValidator(service).validate(jwt()).hasErrors()).isFalse();
    }

    private Jwt consumerJwt(String client, String scope) {
        Instant now=Instant.now();
        return Jwt.withTokenValue("machine")
            .header("alg","RS256")
            .issuer("https://id.example/realms/shared")
            .subject("service-account-subject")
            .audience(List.of("app-common-api"))
            .issuedAt(now).expiresAt(now.plusSeconds(300))
            .claim("azp",client).claim("preferred_username","service-account-"+client)
            .claim("scope",scope).build();
    }

    @Test
    void registeredMachineConsumerDoesNotRequireHumanEnrollment() {
        SubjectAccessService service=mock(SubjectAccessService.class);
        when(service.isEnabledConsumerClient("collectra-client")).thenReturn(true);
        assertThat(new RevocationTokenValidator(service).validate(
            consumerJwt("collectra-client","revocations.read revocations.ack")).hasErrors()).isFalse();
        verify(service,never()).isAllowed(anyString(),anyString());
    }

    @Test
    void humanJwtWithRevocationScopesCannotUseMachineBypass() {
        SubjectAccessService service=mock(SubjectAccessService.class);
        when(service.isEnabledConsumerClient("collectra-client")).thenReturn(true);
        Jwt human=Jwt.withTokenValue("human")
            .header("alg","RS256")
            .issuer("https://id.example/realms/shared")
            .subject("user-1")
            .audience(List.of("app-common-api"))
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
            .claim("azp","collectra-client")
            .claim("preferred_username","human-user")
            .claim("scope","revocations.read").build();
        assertThat(new RevocationTokenValidator(service).validate(human).hasErrors()).isTrue();
        verify(service,never()).isEnabledConsumerClient(anyString());
    }

    @Test
    void unregisteredMachineConsumerFailsClosed() {
        SubjectAccessService service=mock(SubjectAccessService.class);
        assertThat(new RevocationTokenValidator(service).validate(
            consumerJwt("unknown-client","revocations.ack")).hasErrors()).isTrue();
    }

    @Test
    void ordinaryTokenWithAzpStillRequiresHumanLedger() {
        SubjectAccessService service=mock(SubjectAccessService.class);
        assertThat(new RevocationTokenValidator(service).validate(
            consumerJwt("collectra-client","profile email")).hasErrors()).isTrue();
        verify(service).isAllowed(anyString(),anyString());
    }

    @Test
    void registryFailureDeniesMachineConsumer() {
        SubjectAccessService service=mock(SubjectAccessService.class);
        when(service.isEnabledConsumerClient("collectra-client"))
            .thenThrow(new IllegalStateException("database unavailable"));
        assertThat(new RevocationTokenValidator(service).validate(
            consumerJwt("collectra-client","revocations.ack")).hasErrors()).isTrue();
    }
}
