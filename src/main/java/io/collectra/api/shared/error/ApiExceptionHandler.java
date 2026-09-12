package io.collectra.api.shared.error;

import io.collectra.api.file.application.FileNotFoundException;
import io.collectra.api.file.application.FileNotReadyException;
import io.collectra.api.file.application.FileTooLargeException;
import io.collectra.api.file.domain.IllegalFileStateException;
import io.collectra.api.file.infrastructure.storage.FileStorageException;
import io.collectra.api.importing.application.ImportBatchFailedException;
import io.collectra.api.shared.security.RateLimitExceededException;
import io.collectra.api.shared.tenant.MissingTenantException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail p = base(HttpStatus.BAD_REQUEST, "Validation failed", request);
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        p.setProperty("errors", errors);
        p.setProperty("code", "VALIDATION_FAILED");
        return p;
    }

    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        HandlerMethodValidationException.class,
        ConstraintViolationException.class
    })
    ProblemDetail invalidRequest(Exception ex, HttpServletRequest request) {
        return withCode(
                base(HttpStatus.BAD_REQUEST, "Invalid request parameter", request),
                "INVALID_REQUEST");
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalidRequest(InvalidRequestException ex, HttpServletRequest request) {
        return withCode(base(HttpStatus.BAD_REQUEST, ex.getMessage(), request), ex.getCode());
    }

    @ExceptionHandler({BadCredentialsException.class, InvalidRefreshTokenException.class})
    ProblemDetail unauthorized(RuntimeException ex, HttpServletRequest request) {
        return withCode(base(HttpStatus.UNAUTHORIZED, ex.getMessage(), request), "UNAUTHORIZED");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(AccessDeniedException ex, HttpServletRequest request) {
        return withCode(base(HttpStatus.FORBIDDEN, "Access denied", request), "FORBIDDEN");
    }

    @ExceptionHandler(MissingTenantException.class)
    ProblemDetail tenant(MissingTenantException ex, HttpServletRequest request) {
        return withCode(base(HttpStatus.FORBIDDEN, ex.getMessage(), request), "TENANT_REQUIRED");
    }

    @ExceptionHandler(FileNotFoundException.class)
    ProblemDetail fileNotFound(FileNotFoundException ex, HttpServletRequest request) {
        return withCode(base(HttpStatus.NOT_FOUND, ex.getMessage(), request), "FILE_NOT_FOUND");
    }

    @ExceptionHandler({FileNotReadyException.class, IllegalFileStateException.class})
    ProblemDetail fileState(RuntimeException ex, HttpServletRequest request) {
        return withCode(base(HttpStatus.CONFLICT, ex.getMessage(), request), "FILE_STATE_CONFLICT");
    }

    @ExceptionHandler(FileTooLargeException.class)
    ProblemDetail fileTooLarge(FileTooLargeException ex, HttpServletRequest request) {
        return withCode(
                base(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage(), request), "FILE_TOO_LARGE");
    }

    @ExceptionHandler(FileStorageException.class)
    ProblemDetail fileStorage(FileStorageException ex, HttpServletRequest request) {
        return withCode(
                base(HttpStatus.SERVICE_UNAVAILABLE, "Object storage operation failed", request),
                "FILE_STORAGE_UNAVAILABLE");
    }

    @ExceptionHandler(BusinessConflictException.class)
    ProblemDetail businessConflict(BusinessConflictException ex, HttpServletRequest request) {
        return withCode(base(HttpStatus.CONFLICT, ex.getMessage(), request), ex.getCode());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail conflict(IllegalArgumentException ex, HttpServletRequest request) {
        return withCode(base(HttpStatus.CONFLICT, ex.getMessage(), request), "CONFLICT");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ProblemDetail rateLimit(RateLimitExceededException ex, HttpServletRequest request) {
        return withCode(
                base(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request),
                "RATE_LIMIT_EXCEEDED");
    }

    @ExceptionHandler(ImportBatchFailedException.class)
    ProblemDetail importFailed(ImportBatchFailedException ex, HttpServletRequest request) {
        ProblemDetail problem = base(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
        problem.setProperty("code", ex.getErrorCode());
        problem.setProperty("batchId", ex.getBatchId());
        return problem;
    }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    ProblemDetail notFound(java.util.NoSuchElementException ex, HttpServletRequest request) {
        return withCode(base(HttpStatus.NOT_FOUND, ex.getMessage(), request), "NOT_FOUND");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail internal(Exception ex, HttpServletRequest request) {
        return withCode(
                base(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request),
                "INTERNAL_ERROR");
    }

    private ProblemDetail withCode(ProblemDetail problem, String code) {
        problem.setProperty("code", code);
        return problem;
    }

    private ProblemDetail base(HttpStatus status, String detail, HttpServletRequest request) {
        String safeDetail = detail == null || detail.isBlank() ? status.getReasonPhrase() : detail;
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, safeDetail);
        p.setTitle(status.getReasonPhrase());
        p.setInstance(URI.create(request.getRequestURI()));
        p.setProperty("traceId", MDC.get("traceId"));
        p.setProperty("correlationId", MDC.get("correlationId"));
        return p;
    }
}
