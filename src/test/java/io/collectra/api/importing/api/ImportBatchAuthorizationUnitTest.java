package io.collectra.api.importing.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class ImportBatchAuthorizationUnitTest {
    private final ImportBatchAuthorization authorization = new ImportBatchAuthorization();

    @Test
    void acceptsHumanPermissionOrServiceScopeAndRejectsPartialAuthority() {
        assertThat(authorization.canCreate(authentication("ROLE_HUMAN", "DOCUMENT_GENERATE"))).isTrue();
        assertThat(authorization.canCreate(authentication("ROLE_SERVICE", "SCOPE_document:generate"))).isTrue();
        assertThat(authorization.canCreate(authentication("ROLE_SERVICE", "DOCUMENT_GENERATE"))).isFalse();
        assertThat(authorization.canCreate(authentication("ROLE_HUMAN"))).isFalse();

        assertThat(authorization.canRead(authentication("ROLE_HUMAN", "DOCUMENT_READ"))).isTrue();
        assertThat(authorization.canRead(authentication("ROLE_SERVICE", "SCOPE_document:read"))).isTrue();
        assertThat(authorization.canRead(authentication("ROLE_SERVICE", "SCOPE_document:generate"))).isFalse();
    }

    private TestingAuthenticationToken authentication(String... authorities) {
        var authentication = new TestingAuthenticationToken("subject", null,
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        authentication.setAuthenticated(true);
        return authentication;
    }
}
