package io.collectra.api.template.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.template.domain.TemplateChannel;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class TemplateBuilderDocumentCompiler {
    private static final String SCHEMA_VERSION = "1.0";

    public String compile(JsonNode document) {
        return compile(document, TemplateChannel.PDF);
    }

    public String compile(JsonNode document, TemplateChannel channel) {
        validateRoot(document);
        TemplateChannel effectiveChannel = channel == null ? TemplateChannel.PDF : channel;
        StringBuilder result = new StringBuilder();
        for (JsonNode block : document.path("blocks")) {
            renderBlock(block, result, effectiveChannel);
        }
        return result.toString();
    }

    public void validate(JsonNode document) {
        compile(document);
    }

    private void validateRoot(JsonNode document) {
        if (document == null || !document.isObject()) {
            throw new IllegalArgumentException("builderJson must be an object");
        }
        String version = document.path("version").asText();
        if (!SCHEMA_VERSION.equals(version)) {
            throw new IllegalArgumentException("Unsupported builder schema version: " + version);
        }
        JsonNode blocks = document.path("blocks");
        if (!blocks.isArray()) {
            throw new IllegalArgumentException("builderJson.blocks must be an array");
        }
    }

    private void renderBlock(JsonNode block, StringBuilder out, TemplateChannel channel) {
        requireObject(block, "block");
        String type = requiredText(block, "type", "block.type");
        JsonNode props = block.path("props");
        if (!props.isMissingNode() && !props.isObject()) {
            throw new IllegalArgumentException("block.props must be an object");
        }

        if (isTextChannel(channel)) {
            renderTextBlock(type, block, props, out, channel);
            return;
        }

        switch (type) {
            case "header" -> renderHtmlContainer("header", block, out, channel);
            case "footer" -> renderHtmlContainer("footer", block, out, channel);
            case "row" -> renderHtmlContainer("div class=\"builder-row\"", block, out, channel);
            case "column" -> renderHtmlContainer("div class=\"builder-column\"", block, out, channel);
            case "richText" -> renderRichText(props, out, true);
            case "itemsTable" -> renderItemsTable(props, out);
            case "image" -> renderImage(props, out);
            case "spacer" -> renderHtmlSpacer(props, out);
            default -> throw new IllegalArgumentException("Unsupported builder block type: " + type);
        }
    }

    private void renderTextBlock(
            String type,
            JsonNode block,
            JsonNode props,
            StringBuilder out,
            TemplateChannel channel) {
        switch (type) {
            case "header", "footer" -> renderTextContainer(block, out, channel);
            case "richText" -> renderRichText(props, out, false);
            case "spacer" -> out.append('\n');
            case "row", "column", "image", "itemsTable" ->
                    throw new IllegalArgumentException(
                            "Builder block " + type + " is not supported for text channel " + channel);
            default -> throw new IllegalArgumentException("Unsupported builder block type: " + type);
        }
    }

    private void renderHtmlContainer(
            String tag, JsonNode block, StringBuilder out, TemplateChannel channel) {
        String element = tag.contains(" ") ? tag.substring(0, tag.indexOf(' ')) : tag;
        out.append('<').append(tag).append('>');
        renderChildren(block, out, channel);
        out.append("</").append(element).append('>');
    }

    private void renderTextContainer(
            JsonNode block, StringBuilder out, TemplateChannel channel) {
        renderChildren(block, out, channel);
        if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
    }

    private void renderChildren(
            JsonNode block, StringBuilder out, TemplateChannel channel) {
        JsonNode children = block.path("children");
        if (children.isMissingNode()) return;
        if (!children.isArray()) {
            throw new IllegalArgumentException("block.children must be an array");
        }
        for (JsonNode child : children) renderBlock(child, out, channel);
    }

    private void renderRichText(JsonNode props, StringBuilder out, boolean html) {
        JsonNode content = props.path("content");
        if (!content.isArray()) {
            throw new IllegalArgumentException("richText.props.content must be an array");
        }
        if (html) out.append("<div class=\"builder-rich-text\">");
        for (JsonNode node : content) {
            requireObject(node, "richText content node");
            String type = requiredText(node, "type", "richText.content[].type");
            switch (type) {
                case "text" -> {
                    String value = node.path("value").asText("");
                    out.append(html ? HtmlUtils.htmlEscape(value) : value);
                }
                case "placeholder" -> {
                    String key = requiredText(node, "key", "richText.content[].key");
                    PlaceholderGrammar.parse(key);
                    out.append("{{").append(key).append("}}");
                }
                default -> throw new IllegalArgumentException("Unsupported richText node type: " + type);
            }
        }
        if (html) out.append("</div>");
    }

    private void renderItemsTable(JsonNode props, StringBuilder out) {
        String dataSource = requiredText(props, "dataSource", "itemsTable.props.dataSource");
        if (!"items".equals(dataSource)) {
            throw new IllegalArgumentException("itemsTable dataSource must be items");
        }
        JsonNode columns = props.path("columns");
        if (!columns.isArray() || columns.isEmpty()) {
            throw new IllegalArgumentException("itemsTable.props.columns must be a non-empty array");
        }

        out.append("<table class=\"builder-items\"><thead><tr>");
        for (JsonNode column : columns) {
            String key = itemKey(column);
            String label = requiredText(column, "label", "itemsTable.columns[].label");
            out.append("<th>").append(HtmlUtils.htmlEscape(label)).append("</th>");
            PlaceholderGrammar.parse("items." + key);
        }
        out.append("</tr></thead><tbody>{{#each items}}<tr>");
        for (JsonNode column : columns) {
            String key = itemKey(column);
            out.append("<td>{{item.").append(key).append("}}</td>");
        }
        out.append("</tr>{{/each}}</tbody></table>");
    }

    private String itemKey(JsonNode column) {
        requireObject(column, "itemsTable column");
        String key = requiredText(column, "key", "itemsTable.columns[].key");
        if (!key.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid itemsTable column key: " + key);
        }
        return key;
    }

    private void renderImage(JsonNode props, StringBuilder out) {
        String assetKey = requiredText(props, "assetKey", "image.props.assetKey");
        PlaceholderGrammar.parse("asset." + assetKey);
        String alt = props.path("alt").asText("");
        out.append("<img src=\"{{asset.")
                .append(assetKey)
                .append("}}\" alt=\"")
                .append(HtmlUtils.htmlEscape(alt))
                .append("\">");
    }

    private void renderHtmlSpacer(JsonNode props, StringBuilder out) {
        int height = props.path("heightPx").asInt(16);
        if (height < 0 || height > 500) {
            throw new IllegalArgumentException("spacer heightPx must be between 0 and 500");
        }
        int lines = Math.max(1, Math.min(10, (height + 15) / 16));
        out.append("<div class=\"builder-spacer\">");
        for (int i = 0; i < lines; i++) out.append("<br>");
        out.append("</div>");
    }

    private boolean isTextChannel(TemplateChannel channel) {
        return channel == TemplateChannel.SMS
                || channel == TemplateChannel.WHATSAPP
                || channel == TemplateChannel.TELEGRAM;
    }

    private void requireObject(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(path + " must be an object");
        }
    }

    private String requiredText(JsonNode node, String field, String path) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(path + " is required");
        }
        return value.asText().trim();
    }
}
