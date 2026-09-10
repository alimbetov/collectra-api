package io.collectra.api.template.application;

import io.collectra.api.template.domain.TemplateVersion;
import java.util.UUID;
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
        return compileBody(
                version.getId(), version.getContentHtml(), version.getStylesheet());
    }

    public CompiledTemplate compileBody(UUID templateVersionId, String content, String stylesheet) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Template content is required");
        }
        policy.validateStylesheet(stylesheet);
        String sanitized = policy.sanitize(content);
        return new CompiledTemplate(templateVersionId, scanner.scan(sanitized), stylesheet);
    }

    public CompiledTemplate compileText(UUID templateVersionId, String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Template text is required");
        }
        return new CompiledTemplate(templateVersionId, scanner.scan(text), null);
    }
}
