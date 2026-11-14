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
}
