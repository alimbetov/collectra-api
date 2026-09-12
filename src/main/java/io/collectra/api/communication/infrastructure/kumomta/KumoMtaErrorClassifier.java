package io.collectra.api.communication.infrastructure.kumomta;

import io.collectra.api.communication.application.DeliveryFailureKind;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.util.Locale;

@Component
public class KumoMtaErrorClassifier {
    Classification classify(Throwable failure) {
        if (failure instanceof RestClientResponseException responseFailure) {
            return classifyStatus(responseFailure.getStatusCode());
        }
        if (containsCause(failure, HttpConnectTimeoutException.class)) {
            return retryable("KUMO_CONNECT_TIMEOUT", "KumoMTA connection timed out");
        }
        if (containsCause(failure, SocketTimeoutException.class)) {
            return retryable("KUMO_READ_TIMEOUT", "KumoMTA response timed out");
        }
        if (failure instanceof ResourceAccessException
                || containsCause(failure, ConnectException.class)) {
            return retryable("KUMO_CONNECTION_ERROR", "KumoMTA is unavailable");
        }
        return retryable("KUMO_CLIENT_ERROR", "KumoMTA client failed");
    }

    Classification classifyResponse(KumoMtaInjectResponse response) {
        if (response == null) {
            return retryable("KUMO_INVALID_RESPONSE", "KumoMTA returned an empty response");
        }
        String message = sanitize(response.errors());
        if (isExplicitlyTransient(message)) {
            return retryable("KUMO_RECIPIENT_TEMPORARY_FAILURE", message);
        }
        return permanent("KUMO_RECIPIENT_REJECTED", message);
    }

    Classification classifyStatus(HttpStatusCode status) {
        int value = status.value();
        if (value == 408) {
            return retryable("KUMO_TIMEOUT", "KumoMTA request timed out");
        }
        if (value == 429) {
            return retryable("KUMO_RATE_LIMITED", "KumoMTA rate limit reached");
        }
        if (status.is5xxServerError()) {
            return retryable("KUMO_SERVER_ERROR", "KumoMTA server error");
        }
        if (value == 401 || value == 403) {
            return permanent("KUMO_AUTH_ERROR", "KumoMTA authentication failed");
        }
        if (value == 400 || value == 422) {
            return permanent("KUMO_INVALID_REQUEST", "KumoMTA rejected invalid content");
        }
        if (status.is4xxClientError()) {
            return permanent("KUMO_REQUEST_REJECTED", "KumoMTA rejected the request");
        }
        return retryable("KUMO_HTTP_ERROR", "Unexpected KumoMTA HTTP status");
    }

    private boolean isExplicitlyTransient(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("temporar")
                || normalized.contains("try again")
                || normalized.contains("rate limit")
                || normalized.contains("timeout")
                || normalized.contains("unavailable")
                || normalized.contains("overload");
    }

    private String sanitize(Iterable<String> errors) {
        StringBuilder result = new StringBuilder();
        for (String error : errors) {
            if (error == null || error.isBlank()) {
                continue;
            }
            if (result.length() > 0) {
                result.append("; ");
            }
            result.append(error.replaceAll("[\\r\\n\\t]+", " ").trim());
            if (result.length() >= 256) {
                break;
            }
        }
        if (result.length() == 0) {
            return "KumoMTA rejected recipient";
        }
        return result.substring(0, Math.min(result.length(), 256));
    }

    private boolean containsCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Classification retryable(String code, String message) {
        return new Classification(DeliveryFailureKind.RETRYABLE, code, message);
    }

    private Classification permanent(String code, String message) {
        return new Classification(DeliveryFailureKind.PERMANENT, code, message);
    }

    record Classification(DeliveryFailureKind kind, String code, String message) {}
}
