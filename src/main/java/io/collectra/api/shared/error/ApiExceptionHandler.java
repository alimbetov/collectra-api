package io.collectra.api.shared.error;

import io.collectra.api.shared.tenant.MissingTenantException;
import io.collectra.api.shared.security.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail p = base(HttpStatus.BAD_REQUEST, "Validation failed", request);
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        p.setProperty("errors", errors); return p;
    }
    @ExceptionHandler({BadCredentialsException.class, InvalidRefreshTokenException.class})
    ProblemDetail unauthorized(RuntimeException ex, HttpServletRequest request) {
        return base(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }
    @ExceptionHandler(MissingTenantException.class)
    ProblemDetail tenant(MissingTenantException ex, HttpServletRequest request) {
        return base(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail conflict(IllegalArgumentException ex, HttpServletRequest request) {
        return base(HttpStatus.CONFLICT, ex.getMessage(), request);
    }
    @ExceptionHandler(RateLimitExceededException.class)
    ProblemDetail rateLimit(RateLimitExceededException ex, HttpServletRequest request) {
        ProblemDetail problem = base(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
        problem.setProperty("code", "RATE_LIMIT_EXCEEDED");
        return problem;
    }
    @ExceptionHandler(java.util.NoSuchElementException.class)
    ProblemDetail notFound(java.util.NoSuchElementException ex, HttpServletRequest request) {
        return base(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }
    private ProblemDetail base(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(status.getReasonPhrase()); p.setInstance(URI.create(request.getRequestURI()));
        p.setProperty("traceId", MDC.get("traceId")); p.setProperty("correlationId", MDC.get("correlationId"));
        return p;
    }
}
