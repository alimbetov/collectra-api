package io.collectra.api.importing.application;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public record ParsedInput(Map<String, List<JsonNode>> values) {
    public ParsedInput {
        values = Map.copyOf(values);
    }

    public List<JsonNode> valuesAt(String sourcePath) {
        return values.getOrDefault(sourcePath, List.of());
    }
}
