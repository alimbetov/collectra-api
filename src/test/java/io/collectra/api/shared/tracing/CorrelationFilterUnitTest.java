package io.collectra.api.shared.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class CorrelationFilterUnitTest {

    private final Tracer tracer = mock(Tracer.class);
    private final CorrelationFilter filter = new CorrelationFilter(tracer);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void preservesValidCorrelationIdAndUsesCurrentTraceId() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(request.getHeader(CorrelationFilter.CORRELATION_HEADER)).thenReturn("corr-123");
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("trace-456");

        filter.doFilter(
                request,
                response,
                (ignoredRequest, ignoredResponse) -> {
                    assertThat(MDC.get("correlationId")).isEqualTo("corr-123");
                    assertThat(MDC.get("traceId")).isEqualTo("trace-456");
                });

        verify(response).setHeader(CorrelationFilter.CORRELATION_HEADER, "corr-123");
        verify(response).setHeader("X-Trace-Id", "trace-456");
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void invalidCorrelationIdIsReplacedAndMdcIsClearedAfterFailure() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader(CorrelationFilter.CORRELATION_HEADER))
                .thenReturn("invalid value with spaces");
        when(tracer.currentSpan()).thenReturn(null);

        assertThatThrownBy(
                        () ->
                                filter.doFilter(
                                        request,
                                        response,
                                        (ignoredRequest, ignoredResponse) -> {
                                            assertThat(MDC.get("correlationId"))
                                                    .matches("[0-9a-fA-F-]{36}");
                                            assertThat(MDC.get("traceId")).matches("[0-9a-f]{32}");
                                            throw new IllegalStateException("downstream failure");
                                        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("downstream failure");

        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("traceId")).isNull();
    }
}
