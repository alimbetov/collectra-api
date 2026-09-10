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
    public ParsedInput parse(byte[] content, Collection<String> sourcePaths, String recordPath) {
        try {
            JsonNode root = json.readTree(content);
            List<JsonNode> records = records(root, recordPath);
            List<ParsedInput.ParsedRow> rows = new ArrayList<>();
            for (int index = 0; index < records.size(); index++) {
                Map<String, JsonNode> values = new LinkedHashMap<>();
                for (String path : sourcePaths) {
                    List<JsonNode> matches = evaluate(records.get(index), path);
                    if (matches.isEmpty()) values.put(path, json.nullNode());
                    else if (matches.size() == 1) values.put(path, matches.get(0));
                    else {
                        var array = json.createArrayNode();
                        matches.forEach(array::add);
                        values.put(path, array);
                    }
                }
                rows.add(
                        new ParsedInput.ParsedRow(
                                index + 1,
                                recordPath == null || recordPath.isBlank()
                                        ? "$[" + index + "]"
                                        : recordPath + "[" + index + "]",
                                values));
            }
            return new ParsedInput(rows);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid JSON document", ex);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Cannot read JSON document", ex);
        }
    }

    private List<JsonNode> records(JsonNode root, String recordPath) {
        if (recordPath == null || recordPath.isBlank()) {
            if (!root.isArray()) return List.of(root);
            List<JsonNode> result = new ArrayList<>();
            root.forEach(result::add);
            return result;
        }
        String path = recordPath.trim().replace("$.", "").replace("[*]", "[]");
        List<JsonNode> selected = evaluate(root, path);
        if (selected.isEmpty()) throw new IllegalArgumentException("JSON record path has no records");
        return selected;
    }

    private List<JsonNode> evaluate(JsonNode root, String path) {
        if (path.startsWith("/")) {
            JsonNode value = root.at(path);
            return value.isMissingNode() ? List.of() : expand(value);
        }
        List<JsonNode> current = List.of(root);
        for (String rawSegment : path.split("\\.")) {
            boolean array = rawSegment.endsWith("[]");
            String segment = array ? rawSegment.substring(0, rawSegment.length() - 2) : rawSegment;
            List<JsonNode> next = new ArrayList<>();
            for (JsonNode node : current) {
                JsonNode value = segment.isEmpty() ? node : node.path(segment);
                if (value.isMissingNode() || value.isNull()) {
                    if (!array) next.add(json.nullNode());
                    continue;
                }
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
