package io.collectra.api.shared.tracing;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationFilter extends OncePerRequestFilter {
    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    private final Tracer tracer;

    public CorrelationFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = valid(request.getHeader(CORRELATION_HEADER));
        String traceId =
                tracer.currentSpan() == null
                        ? UUID.randomUUID().toString().replace("-", "")
                        : tracer.currentSpan().context().traceId();
        try {
            MDC.put("correlationId", correlationId);
            MDC.put("traceId", traceId);
            response.setHeader(CORRELATION_HEADER, correlationId);
            response.setHeader("X-Trace-Id", traceId);
            chain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("traceId");
        }
    }

    private String valid(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,100}")
                ? value
                : UUID.randomUUID().toString();
    }
}
