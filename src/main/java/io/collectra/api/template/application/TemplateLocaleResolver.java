package io.collectra.api.template.application;

import io.collectra.api.localization.application.SupportedLocaleService;
import io.collectra.api.localization.application.TenantLocaleService;
import io.collectra.api.localization.domain.SupportedLocale;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersionStatus;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateLocaleResolver {
    static final String PLATFORM_DEFAULT_LOCALE = "en";

    private final TenantLocaleService tenantLocales;
    private final SupportedLocaleService supportedLocales;
    private final DocumentTemplateRepository templates;
    private final TemplateVersionRepository versions;

    public TemplateLocaleResolver(
            TenantLocaleService tenantLocales,
            SupportedLocaleService supportedLocales,
            DocumentTemplateRepository templates,
            TemplateVersionRepository versions) {
        this.tenantLocales = tenantLocales;
        this.supportedLocales = supportedLocales;
        this.templates = templates;
        this.versions = versions;
    }

    @Transactional(readOnly = true)
    public ResolvedTemplateLocale resolve(
            UUID tenantId,
            UUID templateId,
            TemplateChannel channel,
            String requestedLocale) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (templateId == null) throw new IllegalArgumentException("templateId is required");
        if (channel == null) throw new IllegalArgumentException("channel is required");

        templates.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new TemplateLocaleResolutionException(
                        "Template " + templateId + " does not belong to tenant " + tenantId));

        String requested = supportedLocales.canonicalize(requestedLocale);
        SupportedLocale requestedDefinition = supportedLocales.requireSupported(requested);

        if (isUsable(tenantId, templateId, channel, requested)) {
            return new ResolvedTemplateLocale(
                    requested, requested, TemplateLocaleResolutionSource.EXACT);
        }

        Set<String> visited = new HashSet<>();
        visited.add(requested);
        SupportedLocale current = requestedDefinition;
        while (current.getFallbackLocale() != null && !current.getFallbackLocale().isBlank()) {
            String fallback = supportedLocales.canonicalize(current.getFallbackLocale());
            if (!visited.add(fallback)) {
                throw new TemplateLocaleResolutionException(
                        "Locale fallback cycle detected for " + requested);
            }
            current = supportedLocales.requireSupported(fallback);
            if (isUsable(tenantId, templateId, channel, fallback)) {
                return new ResolvedTemplateLocale(
                        requested, fallback, TemplateLocaleResolutionSource.LOCALE_FALLBACK);
            }
        }

        String tenantDefault = tenantLocales.requireDefault(tenantId).getLocale();
        if (!visited.contains(tenantDefault)
                && isUsable(tenantId, templateId, channel, tenantDefault)) {
            return new ResolvedTemplateLocale(
                    requested, tenantDefault, TemplateLocaleResolutionSource.TENANT_DEFAULT);
        }

        if (!visited.contains(PLATFORM_DEFAULT_LOCALE)
                && !PLATFORM_DEFAULT_LOCALE.equals(tenantDefault)
                && isUsable(tenantId, templateId, channel, PLATFORM_DEFAULT_LOCALE)) {
            return new ResolvedTemplateLocale(
                    requested,
                    PLATFORM_DEFAULT_LOCALE,
                    TemplateLocaleResolutionSource.PLATFORM_DEFAULT);
        }

        throw new TemplateLocaleResolutionException(
                "No published " + channel + " template locale can be resolved for template "
                        + templateId + " and requested locale " + requested);
    }

    private boolean isUsable(
            UUID tenantId,
            UUID templateId,
            TemplateChannel channel,
            String locale) {
        return tenantLocales.isEnabled(tenantId, locale)
                && versions.existsByTemplateIdAndLocaleAndChannelAndStatus(
                        templateId, locale, channel, TemplateVersionStatus.PUBLISHED);
    }
}
