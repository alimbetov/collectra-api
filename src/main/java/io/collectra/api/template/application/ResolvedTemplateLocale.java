package io.collectra.api.template.application;

public record ResolvedTemplateLocale(
        String requestedLocale,
        String resolvedLocale,
        TemplateLocaleResolutionSource source) {}
