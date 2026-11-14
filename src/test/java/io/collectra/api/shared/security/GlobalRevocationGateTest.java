package io.collectra.api.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class GlobalRevocationGateTest {
    @Test
    void missingIssuerFailsClosedWithoutDatabaseQuery() {
        var db = mock(JdbcClient.class);
        var gate = new GlobalRevocationGate(db, Duration.ofSeconds(30));
        assertThat(gate.allows(null, "subject")).isFalse();
        verifyNoInteractions(db);
    }

    @Test
    void zeroFreshnessFailsClosed() {
        var db = mock(JdbcClient.class);
        var gate = new GlobalRevocationGate(db, Duration.ZERO);
        assertThat(gate.allows("issuer", "subject")).isFalse();
        verifyNoInteractions(db);
    }
}
