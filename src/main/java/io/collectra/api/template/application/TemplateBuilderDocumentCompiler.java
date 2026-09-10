package io.collectra.api.template.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class TemplateBuilderDocumentCompiler {
    private static final String SCHEMA_VERSION = "1.0";

    public String compile(JsonNode document) {
        validateRoot(document);
        StringBuilder html = new StringBuilder();
        for (JsonNode block : document.path("blocks")) {
            renderBlock(block, html);
        }
        return html.toString();
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

    private void renderBlock(JsonNode block, StringBuilder html) {
        requireObject(block, "block");
        String type = requiredText(block, "type", "block.type");
        JsonNode props = block.path("props");
        if (!props.isMissingNode() && !props.isObject()) {
            throw new IllegalArgumentException("block.props must be an object");
        }

        switch (type) {
            case "header" -> renderContainer("header", block, html);
            case "footer" -> renderContainer("footer", block, html);
            case "row" -> renderContainer("div class=\"builder-row\"", block, html);
            case "column" -> renderContainer("div class=\"builder-column\"", block, html);
            case "richText" -> renderRichText(props, html);
            case "itemsTable" -> renderItemsTable(props, html);
            case "image" -> renderImage(props, html);
            case "spacer" -> renderSpacer(props, html);
            default -> throw new IllegalArgumentException("Unsupported builder block type: " + type);
        }
    }

    private void renderContainer(String tag, JsonNode block, StringBuilder html) {
        String element = tag.contains(" ") ? tag.substring(0, tag.indexOf(' ')) : tag;
        html.append('<').append(tag).append('>');
        JsonNode children = block.path("children");
        if (!children.isMissingNode()) {
            if (!children.isArray()) {
                throw new IllegalArgumentException("block.children must be an array");
            }
            for (JsonNode child : children) renderBlock(child, html);
        }
        html.append("</").append(element).append('>');
    }

    private void renderRichText(JsonNode props, StringBuilder html) {
        JsonNode content = props.path("content");
        if (!content.isArray()) {
            throw new IllegalArgumentException("richText.props.content must be an array");
        }
        html.append("<div class=\"builder-rich-text\">");
        for (JsonNode node : content) {
            requireObject(node, "richText content node");
            String type = requiredText(node, "type", "richText.content[].type");
            switch (type) {
                case "text" -> html.append(HtmlUtils.htmlEscape(node.path("value").asText("")));
                case "placeholder" -> {
                    String key = requiredText(node, "key", "richText.content[].key");
                    PlaceholderGrammar.parse(key);
                    html.append("{{").append(key).append("}}");
                }
                default -> throw new IllegalArgumentException("Unsupported richText node type: " + type);
            }
        }
        html.append("</div>");
    }

    private void renderItemsTable(JsonNode props, StringBuilder html) {
        String dataSource = requiredText(props, "dataSource", "itemsTable.props.dataSource");
        if (!"items".equals(dataSource)) {
            throw new IllegalArgumentException("itemsTable dataSource must be items");
        }
        JsonNode columns = props.path("columns");
        if (!columns.isArray() || columns.isEmpty()) {
            throw new IllegalArgumentException("itemsTable.props.columns must be a non-empty array");
        }

        html.append("<table class=\"builder-items\"><thead><tr>");
        for (JsonNode column : columns) {
            String key = itemKey(column);
            String label = requiredText(column, "label", "itemsTable.columns[].label");
            html.append("<th>").append(HtmlUtils.htmlEscape(label)).append("</th>");
            PlaceholderGrammar.parse("items." + key);
        }
        html.append("</tr></thead><tbody>{{#each items}}<tr>");
        for (JsonNode column : columns) {
            String key = itemKey(column);
            html.append("<td>{{item.").append(key).append("}}</td>");
        }
        html.append("</tr>{{/each}}</tbody></table>");
    }

    private String itemKey(JsonNode column) {
        requireObject(column, "itemsTable column");
        String key = requiredText(column, "key", "itemsTable.columns[].key");
        if (!key.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid itemsTable column key: " + key);
        }
        return key;
    }

    private void renderImage(JsonNode props, StringBuilder html) {
        String assetKey = requiredText(props, "assetKey", "image.props.assetKey");
        PlaceholderGrammar.parse("asset." + assetKey);
        String alt = props.path("alt").asText("");
        html.append("<img src=\"{{asset.")
                .append(assetKey)
                .append("}}\" alt=\"")
                .append(HtmlUtils.htmlEscape(alt))
                .append("\">");
    }

    private void renderSpacer(JsonNode props, StringBuilder html) {
        int height = props.path("heightPx").asInt(16);
        if (height < 0 || height > 500) {
            throw new IllegalArgumentException("spacer heightPx must be between 0 and 500");
        }
        html.append("<div class=\"builder-spacer\" style=\"height:")
                .append(height)
                .append("px\"></div>");
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
