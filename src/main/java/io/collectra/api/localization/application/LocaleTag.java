package io.collectra.api.localization.application;

import java.util.Locale;

public final class LocaleTag {
    private LocaleTag() {}

    public static String canonicalize(String locale) {
        if (locale == null || locale.isBlank()) {
            throw new IllegalArgumentException("Locale is required");
        }
        String candidate = locale.trim().replace('_', '-');
        Locale parsed = Locale.forLanguageTag(candidate);
        String canonical = parsed.toLanguageTag();
        if (canonical.isBlank() || "und".equalsIgnoreCase(canonical)) {
            throw new IllegalArgumentException("Invalid locale: " + locale);
        }
        return canonical;
    }
}
