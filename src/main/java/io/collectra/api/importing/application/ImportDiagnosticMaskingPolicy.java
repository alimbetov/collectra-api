package io.collectra.api.importing.application;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ImportDiagnosticMaskingPolicy {
    public static final int ERROR_CODE_MAX = 100;
    public static final int SAFE_DETAIL_MAX = 500;
    public static final int FIELD_PATH_MAX = 300;
    public static final int MASKED_VALUE_MAX = 500;

    private static final Pattern EMAIL =
            Pattern.compile("(?i)([a-z0-9._%+-])[a-z0-9._%+-]*(@[a-z0-9.-]+\\.[a-z]{2,})");
    private static final Pattern PHONE =
            Pattern.compile("(?<!\\d)(\\+?\\d[\\d ()-]{6,}\\d)(?!\\d)");
    private static final Pattern LONG_DIGITS = Pattern.compile("(?<!\\d)\\d{6,}(?!\\d)");

    public String errorCode(String value) {
        String normalized =
                value == null || value.isBlank()
                        ? "IMPORT_RECORD_INVALID"
                        : value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_.-]", "_");
        return bound(normalized, ERROR_CODE_MAX);
    }

    public String safeDetail(String value) {
        String safe = value == null || value.isBlank() ? "Import record validation failed" : value.trim();
        return bound(maskText(safe), SAFE_DETAIL_MAX);
    }

    public String fieldPath(String value) {
        return value == null || value.isBlank() ? null : bound(value.trim(), FIELD_PATH_MAX);
    }

    public String maskedSourceValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return null;
        return bound(maskText(rawValue.trim()), MASKED_VALUE_MAX);
    }

    private String maskText(String value) {
        String masked = EMAIL.matcher(value).replaceAll("$1***$2");
        masked = PHONE.matcher(masked).replaceAll("***");
        return LONG_DIGITS.matcher(masked).replaceAll("***");
    }

    private String bound(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
