package io.collectra.api.importing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CsvInputParser implements InputParser {
    private static final int MAX_ROWS = 10_000;
    private final ObjectMapper json;

    CsvInputParser(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public ParsedInput parse(byte[] content, Collection<String> sourcePaths) {
        Map<String, List<JsonNode>> result = emptyResult(sourcePaths);
        char delimiter = detectDelimiter(content);
        CSVFormat format =
                CSVFormat.DEFAULT
                        .builder()
                        .setDelimiter(delimiter)
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setIgnoreEmptyLines(true)
                        .setTrim(true)
                        .get();
        try (var reader =
                        new InputStreamReader(
                                new ByteArrayInputStream(withoutBom(content)),
                                StandardCharsets.UTF_8);
                CSVParser parser = format.parse(reader)) {
            int rows = 0;
            for (CSVRecord row : parser) {
                if (++rows > MAX_ROWS) throw new IllegalArgumentException("CSV exceeds 10000 rows");
                for (String path : sourcePaths) {
                    if (!parser.getHeaderMap().containsKey(path)) continue;
                    String value = row.get(path);
                    if (!value.isBlank())
                        result.get(path).add(json.getNodeFactory().textNode(value));
                }
            }
            return immutable(result);
        } catch (java.io.IOException | IllegalArgumentException ex) {
            if (ex instanceof IllegalArgumentException argument) throw argument;
            throw new IllegalArgumentException("Invalid CSV document", ex);
        }
    }

    private char detectDelimiter(byte[] content) {
        String text = new String(withoutBom(content), StandardCharsets.UTF_8);
        String firstLine = text.lines().findFirst().orElse("");
        return firstLine.chars().filter(value -> value == ';').count()
                        > firstLine.chars().filter(value -> value == ',').count()
                ? ';'
                : ',';
    }

    private byte[] withoutBom(byte[] content) {
        if (content.length >= 3
                && content[0] == (byte) 0xEF
                && content[1] == (byte) 0xBB
                && content[2] == (byte) 0xBF)
            return java.util.Arrays.copyOfRange(content, 3, content.length);
        return content;
    }

    private Map<String, List<JsonNode>> emptyResult(Collection<String> paths) {
        Map<String, List<JsonNode>> result = new LinkedHashMap<>();
        paths.forEach(path -> result.put(path, new ArrayList<>()));
        return result;
    }

    private ParsedInput immutable(Map<String, List<JsonNode>> source) {
        Map<String, List<JsonNode>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return new ParsedInput(result);
    }
}
