package io.collectra.api.importing.application;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ParsedInput(List<ParsedRow> rows) {
    public ParsedInput {
        rows = List.copyOf(rows);
    }

    /** Compatibility constructor for callers that do not have row-aware input yet. */
    public ParsedInput(Map<String, List<JsonNode>> columns) {
        this(fromColumns(columns));
    }

    public List<JsonNode> valuesAt(String sourcePath) {
        List<JsonNode> result = new ArrayList<>();
        for (ParsedRow row : rows) {
            JsonNode value = row.values().get(sourcePath);
            if (value != null && !value.isNull() && !value.isMissingNode()) {
                if (value.isArray()) value.forEach(result::add);
                else result.add(value);
            }
        }
        return List.copyOf(result);
    }

    private static List<ParsedRow> fromColumns(Map<String, List<JsonNode>> columns) {
        int size = columns.values().stream().mapToInt(List::size).max().orElse(0);
        List<ParsedRow> result = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            Map<String, JsonNode> values = new LinkedHashMap<>();
            for (var column : columns.entrySet()) {
                values.put(column.getKey(), index < column.getValue().size()
                        ? column.getValue().get(index) : null);
            }
            result.add(new ParsedRow(index + 1, "legacy:" + (index + 1), values));
        }
        return result;
    }

    public record ParsedRow(int order, String sourceLocation, Map<String, JsonNode> values) {
        public ParsedRow {
            if (order < 1) throw new IllegalArgumentException("Row order must be positive");
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }
}
