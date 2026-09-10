package io.collectra.api.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;

class ApiExceptionHandlerUnitTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final HttpServletRequest request = request("/api/v1/test");

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void unauthorizedUsesStableStatusAndCode() {
        var problem =
                handler.unauthorized(new BadCredentialsException("Invalid credentials"), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problem.getDetail()).isEqualTo("Invalid credentials");
        assertThat(problem.getProperties()).containsEntry("code", "UNAUTHORIZED");
    }

    @Test
    void notFoundUsesStableStatusAndCode() {
        var problem = handler.notFound(new NoSuchElementException("Missing resource"), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getProperties()).containsEntry("code", "NOT_FOUND");
        assertThat(problem.getInstance().toString()).isEqualTo("/api/v1/test");
    }

    @Test
    void internalErrorDoesNotExposeExceptionMessageOrInfrastructureDetails() {
        String sensitive = "password=secret jdbc:postgresql://db/internal /etc/passwd";
        var problem = handler.internal(new RuntimeException(sensitive), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).isEqualTo("Internal server error");
        assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
        assertThat(problem.toString()).doesNotContain(sensitive, "jdbc:postgresql", "/etc/passwd");
    }

    @Test
    void problemIncludesTracingContextWithoutPuttingItInDetail() {
        MDC.put("traceId", "trace-123");
        MDC.put("correlationId", "corr-456");

        var problem = handler.internal(new RuntimeException("boom"), request);

        assertThat(problem.getProperties())
                .containsEntry("traceId", "trace-123")
                .containsEntry("correlationId", "corr-456");
        assertThat(problem.getDetail()).doesNotContain("trace-123", "corr-456");
    }

    private static HttpServletRequest request(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }
}
