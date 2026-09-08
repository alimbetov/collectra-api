package io.collectra.api.template.application;

import com.fasterxml.jackson.databind.JsonNode;

import io.collectra.api.template.domain.TemplateVersion;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TemplateRenderer {
    private static final Pattern PLACEHOLDER =
            Pattern.compile(
                    "\\{\\{\\s*([a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+)\\s*}}",
                    Pattern.CASE_INSENSITIVE);

    public RenderResult render(TemplateVersion version, JsonNode normalizedPayload) {
        String template = version.getContentHtml();
        if (template.contains("{{{"))
            throw new IllegalArgumentException("Unescaped template expressions are forbidden");
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer rendered = new StringBuffer();
        List<String> missing = new ArrayList<>();
        while (matcher.find()) {
            String key = matcher.group(1);
            JsonNode value = normalizedPayload.at("/" + key.replace('.', '/'));
            if (value.isMissingNode() || value.isNull()) {
                missing.add(key);
                matcher.appendReplacement(rendered, "");
            } else {
                String text = value.isValueNode() ? value.asText() : value.toString();
                matcher.appendReplacement(
                        rendered, Matcher.quoteReplacement(HtmlUtils.htmlEscape(text)));
            }
        }
        matcher.appendTail(rendered);
        if (!missing.isEmpty())
            throw new IllegalArgumentException(
                    "Template values are missing: " + String.join(", ", missing));
        if (rendered.indexOf("{{") >= 0)
            throw new IllegalArgumentException("Unsupported template expression");
        return new RenderResult(
                version.getId(),
                addDocumentStructure(rendered.toString(), version.getStylesheet()));
    }

    private String addDocumentStructure(String html, String stylesheet) {
        String style =
                stylesheet == null || stylesheet.isBlank()
                        ? ""
                        : "<style>" + stylesheet + "</style>";
        if (html.regionMatches(true, 0, "<!DOCTYPE", 0, 9)
                || html.toLowerCase(java.util.Locale.ROOT).contains("<html")) {
            int headEnd = html.toLowerCase(java.util.Locale.ROOT).indexOf("</head>");
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
