package io.collectra.api.template.application;

import io.collectra.api.template.domain.TemplateVersion;

import org.springframework.stereotype.Component;

@Component
public class TemplateCompiler {
    private final HtmlTemplatePolicy policy;
    private final PlaceholderScanner scanner;

    public TemplateCompiler(HtmlTemplatePolicy policy, PlaceholderScanner scanner) {
        this.policy = policy;
        this.scanner = scanner;
    }

    public CompiledTemplate compile(TemplateVersion version) {
        policy.validateStylesheet(version.getStylesheet());
        String sanitized = policy.sanitize(version.getContentHtml());
        return new CompiledTemplate(
                version.getId(), scanner.scan(sanitized), version.getStylesheet());
    }
}
