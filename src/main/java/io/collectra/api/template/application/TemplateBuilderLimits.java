package io.collectra.api.template.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.shared.error.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class TemplateBuilderLimits {
    public static final int MAX_CONTENT_BYTES = 256 * 1024;
    public static final int MAX_STYLESHEET_BYTES = 128 * 1024;
    public static final int MAX_BUILDER_JSON_BYTES = 512 * 1024;
    public static final int MAX_PREVIEW_PAYLOAD_BYTES = 512 * 1024;
    public static final int MAX_JSON_DEPTH = 24;
    public static final int MAX_JSON_NODES = 5_000;
    public static final int MAX_RENDERED_CONTENT_BYTES = 2 * 1024 * 1024;
    public static final int MAX_PDF_BYTES = 10 * 1024 * 1024;

    public void validateDraft(String content, String stylesheet) {
        requireStringBytes(
                content,
                MAX_CONTENT_BYTES,
                "TEMPLATE_CONTENT_TOO_LARGE",
                "Template content exceeds 256 KB");
        requireStringBytes(
                stylesheet,
                MAX_STYLESHEET_BYTES,
                "TEMPLATE_STYLESHEET_TOO_LARGE",
                "Template stylesheet exceeds 128 KB");
    }

    public void validateBuilder(JsonNode builderJson) {
        validateJson(
                builderJson,
                MAX_BUILDER_JSON_BYTES,
                "BUILDER_DOCUMENT_TOO_COMPLEX",
                "Builder document exceeds supported size or complexity");
    }

    public void validatePreviewPayload(JsonNode payload) {
        validateJson(
                payload,
                MAX_PREVIEW_PAYLOAD_BYTES,
                "PREVIEW_PAYLOAD_TOO_LARGE",
                "Preview payload exceeds supported size or complexity");
    }

    public void validateRenderedContent(String content) {
        requireStringBytes(
                content,
                MAX_RENDERED_CONTENT_BYTES,
                "PREVIEW_OUTPUT_TOO_LARGE",
                "Rendered preview exceeds 2 MB");
    }

    public void validatePdf(byte[] pdf) {
        if (pdf != null && pdf.length > MAX_PDF_BYTES) {
            throw new InvalidRequestException(
                    "PREVIEW_OUTPUT_TOO_LARGE", "Rendered PDF preview exceeds 10 MB");
        }
    }

    private void validateJson(JsonNode node, int maxBytes, String code, String message) {
        if (node == null || node.isNull()) {
            return;
        }
        int bytes = node.toString().getBytes(StandardCharsets.UTF_8).length;
        JsonStats stats = stats(node, 1);
        if (bytes > maxBytes
                || stats.depth() > MAX_JSON_DEPTH
                || stats.nodes() > MAX_JSON_NODES) {
            throw new InvalidRequestException(code, message);
        }
    }

    private JsonStats stats(JsonNode node, int depth) {
        int nodes = 1;
        int maxDepth = depth;
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                JsonStats childStats = stats(child, depth + 1);
                nodes += childStats.nodes();
                maxDepth = Math.max(maxDepth, childStats.depth());
                if (nodes > MAX_JSON_NODES || maxDepth > MAX_JSON_DEPTH) {
                    break;
                }
            }
        }
        return new JsonStats(nodes, maxDepth);
    }

    private void requireStringBytes(String value, int maxBytes, String code, String message) {
        if (value != null && value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new InvalidRequestException(code, message);
        }
    }

    private record JsonStats(int nodes, int depth) {}
}
