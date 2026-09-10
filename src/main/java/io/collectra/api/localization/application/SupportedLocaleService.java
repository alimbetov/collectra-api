package io.collectra.api.localization.application;

import io.collectra.api.localization.domain.SupportedLocale;
import io.collectra.api.localization.infrastructure.SupportedLocaleRepository;
import java.util.List;
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
        String code = LocaleTag.canonicalize(locale);
        return locales.findById(code)
                .filter(SupportedLocale::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported locale: " + code));
    }

    public String canonicalize(String locale) {
        return LocaleTag.canonicalize(locale);
    }
}
