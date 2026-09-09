package io.collectra.api.importing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonInputParser implements InputParser {
    private final ObjectMapper json;

    JsonInputParser(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public ParsedInput parse(byte[] content, Collection<String> sourcePaths) {
        try {
            JsonNode root = json.readTree(content);
            Map<String, List<JsonNode>> result = new LinkedHashMap<>();
            for (String path : sourcePaths) result.put(path, evaluate(root, path));
            return new ParsedInput(result);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid JSON document", ex);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Cannot read JSON document", ex);
        }
    }

    private List<JsonNode> evaluate(JsonNode root, String path) {
        if (path.startsWith("/")) {
            JsonNode value = root.at(path);
            return value.isMissingNode() || value.isNull() ? List.of() : expand(value);
        }
        List<JsonNode> current = List.of(root);
        for (String rawSegment : path.split("\\.")) {
            boolean array = rawSegment.endsWith("[]");
            String segment = array ? rawSegment.substring(0, rawSegment.length() - 2) : rawSegment;
            List<JsonNode> next = new ArrayList<>();
            for (JsonNode node : current) {
                JsonNode value = segment.isEmpty() ? node : node.path(segment);
                if (value.isMissingNode() || value.isNull()) continue;
                if (array) {
                    if (!value.isArray())
                        throw new IllegalArgumentException("JSON path expects array: " + path);
                    value.forEach(next::add);
                } else next.add(value);
            }
            current = next;
        }
        List<JsonNode> values = new ArrayList<>();
        current.forEach(value -> values.addAll(expand(value)));
        return List.copyOf(values);
    }

    private List<JsonNode> expand(JsonNode value) {
        if (!value.isArray()) return List.of(value);
        List<JsonNode> values = new ArrayList<>();
        value.forEach(values::add);
        return List.copyOf(values);
    }
}
