package io.collectra.api.template.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.template.domain.TemplateVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

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
        String rendered = renderContent(template, normalizedPayload, true);
        return new RenderResult(
                template.templateVersionId(), addDocumentStructure(rendered, template.stylesheet()));
    }

    public String renderText(CompiledTemplate template, JsonNode normalizedPayload) {
        return renderContent(template, normalizedPayload, false);
    }

    private String renderContent(
            CompiledTemplate template, JsonNode normalizedPayload, boolean escapeHtml) {
        StringBuilder rendered = new StringBuilder();
        List<String> missing = new ArrayList<>();
        renderRange(
                template.tokens(),
                0,
                template.tokens().size(),
                normalizedPayload,
                null,
                rendered,
                missing,
                escapeHtml);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Template values are missing: " + String.join(", ", missing));
        }
        return rendered.toString();
    }

    private void renderRange(
            List<TemplateToken> tokens,
            int from,
            int to,
            JsonNode payload,
            JsonNode currentItem,
            StringBuilder rendered,
            List<String> missing,
            boolean escapeHtml) {
        int index = from;
        while (index < to) {
            TemplateToken token = tokens.get(index);
            if (token instanceof TemplateToken.Text text) {
                rendered.append(text.value());
                index++;
                continue;
            }
            if (token instanceof TemplateToken.Placeholder placeholder) {
                appendPlaceholder(
                        placeholder.path(), payload, currentItem, rendered, missing, escapeHtml);
                index++;
                continue;
            }
            if (token instanceof TemplateToken.EachStart each) {
                int end = findEachEnd(tokens, index + 1, to);
                JsonNode collection = payload.path(each.collectionKey());
                if (!collection.isArray()) {
                    missing.add(each.collectionKey() + "[]");
                } else {
                    for (JsonNode item : collection) {
                        renderRange(
                                tokens,
                                index + 1,
                                end,
                                payload,
                                item,
                                rendered,
                                missing,
                                escapeHtml);
                    }
                }
                index = end + 1;
                continue;
            }
            if (token instanceof TemplateToken.EachEnd) {
                throw new IllegalStateException("Unexpected each end in compiled template");
            }
        }
    }

    private int findEachEnd(List<TemplateToken> tokens, int from, int to) {
        for (int i = from; i < to; i++) {
            if (tokens.get(i) instanceof TemplateToken.EachEnd) return i;
        }
        throw new IllegalStateException("Compiled each block has no end token");
    }

    private void appendPlaceholder(
            FieldPath path,
            JsonNode payload,
            JsonNode currentItem,
            StringBuilder rendered,
            List<String> missing,
            boolean escapeHtml) {
        JsonNode value;
        String canonical = path.canonical();
        if (canonical.startsWith("item.")) {
            if (currentItem == null) {
                missing.add(canonical);
                return;
            }
            String relative =
                    "/" + String.join("/", path.segments().subList(1, path.segments().size()));
            value = currentItem.at(relative);
        } else {
            value = payload.at(path.jsonPointer());
        }
        if (value.isMissingNode() || value.isNull()) {
            missing.add(canonical);
            return;
        }
        String text = value.isValueNode() ? value.asText() : value.toString();
        rendered.append(escapeHtml ? HtmlUtils.htmlEscape(text) : text);
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
