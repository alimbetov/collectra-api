package io.collectra.api.template.application;

import com.fasterxml.jackson.databind.JsonNode;

import io.collectra.api.template.domain.TemplateVersion;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TemplateRenderer {
    private final TemplateCompiler compiler;

    public TemplateRenderer(TemplateCompiler compiler) {
        this.compiler = compiler;
    }

    public RenderResult render(TemplateVersion version, JsonNode normalizedPayload) {
        return render(compiler.compile(version), normalizedPayload);
    }

    public RenderResult render(CompiledTemplate template, JsonNode normalizedPayload) {
        StringBuilder rendered = new StringBuilder();
        List<String> missing = new ArrayList<>();

        for (TemplateToken token : template.tokens()) {
            if (token instanceof TemplateToken.Text text) {
                rendered.append(text.value());
                continue;
            }

            TemplateToken.Placeholder placeholder = (TemplateToken.Placeholder) token;
            FieldPath path = placeholder.path();
            JsonNode value = normalizedPayload.at(path.jsonPointer());
            if (value.isMissingNode() || value.isNull()) {
                missing.add(path.canonical());
            } else {
                String text = value.isValueNode() ? value.asText() : value.toString();
                rendered.append(HtmlUtils.htmlEscape(text));
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Template values are missing: " + String.join(", ", missing));
        }

        return new RenderResult(
                template.templateVersionId(),
                addDocumentStructure(rendered.toString(), template.stylesheet()));
    }

    private String addDocumentStructure(String html, String stylesheet) {
        String style =
                stylesheet == null || stylesheet.isBlank()
                        ? ""
                        : "<style>" + stylesheet + "</style>";
        String normalized = html.toLowerCase(Locale.ROOT);
        if (html.regionMatches(true, 0, "<!DOCTYPE", 0, 9) || normalized.contains("<html")) {
            int headEnd = normalized.indexOf("</head>");
            return headEnd >= 0
                    ? html.substring(0, headEnd) + style + html.substring(headEnd)
                    : style + html;
        }
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
                + style
                + "</head><body>"
                + html
                + "</body></html>";
    }

    public record RenderResult(java.util.UUID templateVersionId, String html) {}
}
