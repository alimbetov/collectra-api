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
    public ParsedInput parse(byte[] content, Collection<String> sourcePaths, String recordPath) {
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
            List<ParsedInput.ParsedRow> parsedRows = new ArrayList<>();
            for (CSVRecord row : parser) {
                if (++rows > MAX_ROWS) throw new IllegalArgumentException("CSV exceeds 10000 rows");
                Map<String, JsonNode> values = new LinkedHashMap<>();
                for (String path : sourcePaths) {
                    String value = parser.getHeaderMap().containsKey(path) ? row.get(path) : null;
                    values.put(path, value == null || value.isBlank()
                            ? json.nullNode() : json.getNodeFactory().textNode(value));
                }
                parsedRows.add(new ParsedInput.ParsedRow(rows,
                        "row:" + row.getRecordNumber(), values));
            }
            return new ParsedInput(parsedRows);
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

}
