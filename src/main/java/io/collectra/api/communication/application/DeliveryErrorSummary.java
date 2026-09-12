package io.collectra.api.communication.application;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DeliveryErrorSummary {
    public String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return null;
        }
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        if (code.contains("TIMEOUT")) {
            return "CONNECTION_TIMEOUT";
        }
        if (code.contains("RATE") || code.contains("429")) {
            return "RATE_LIMITED";
        }
        if (code.contains("INVALID") || code.contains("RECIPIENT")) {
            return "INVALID_DESTINATION";
        }
        if (code.contains("5XX") || code.startsWith("5")) {
            return "PROVIDER_5XX";
        }
        if (code.contains("PERMANENT") || code.contains("REJECT")) {
            return "PERMANENT_PROVIDER_REJECTION";
        }
        return "UNKNOWN";
    }

    public String summary(String normalizedCode) {
        if (normalizedCode == null) {
            return null;
        }
        return switch (normalizedCode) {
            case "CONNECTION_TIMEOUT" -> "Provider connection timed out";
            case "RATE_LIMITED" -> "Provider rate limit was reached";
            case "INVALID_DESTINATION" -> "Destination was rejected as invalid";
            case "PROVIDER_5XX" -> "Provider returned a temporary server error";
            case "PERMANENT_PROVIDER_REJECTION" -> "Provider permanently rejected delivery";
            default -> "Delivery failed";
        };
    }
}
