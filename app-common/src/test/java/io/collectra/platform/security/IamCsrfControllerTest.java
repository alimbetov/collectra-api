package io.collectra.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class IamCsrfControllerTest {
    @Test void returnsOnlyCsrfHeaderAndTokenWithNoStore() {
        var response = new IamCsrfController().csrf(
                new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "test-csrf-token"));
        assertThat(response.getBody()).containsEntry("headerName", "X-XSRF-TOKEN")
                .containsEntry("token", "test-csrf-token")
                .doesNotContainKeys("accessToken", "refreshToken", "clientSecret");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }
}
