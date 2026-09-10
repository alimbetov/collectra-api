package io.collectra.api.localization.application;

import io.collectra.api.localization.domain.SupportedLocale;
import io.collectra.api.localization.infrastructure.SupportedLocaleRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportedLocaleService {
    private final SupportedLocaleRepository locales;

    public SupportedLocaleService(SupportedLocaleRepository locales) {
        this.locales = locales;
    }

    @Transactional(readOnly = true)
    public List<SupportedLocale> listEnabled() {
        return locales.findAllByEnabledTrueOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public SupportedLocale requireSupported(String locale) {
        String code = canonicalize(locale);
        return locales.findById(code)
                .filter(SupportedLocale::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported locale: " + code));
    }

    public String canonicalize(String locale) {
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
