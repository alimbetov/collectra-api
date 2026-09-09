package io.collectra.api.importing.application;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.collectra.api.importing.domain.SourceFormat;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

@Service
public class DocumentInputParser {
    static final int MAX_INPUT_BYTES = 10 * 1024 * 1024;
    private final Map<SourceFormat, InputParser> parsers;

    public DocumentInputParser(ObjectMapper json) {
        EnumMap<SourceFormat, InputParser> configured = new EnumMap<>(SourceFormat.class);
        configured.put(SourceFormat.JSON, new JsonInputParser(json));
        configured.put(SourceFormat.XML, new XmlInputParser(json));
        configured.put(SourceFormat.CSV, new CsvInputParser(json));
        configured.put(SourceFormat.EXCEL, new ExcelInputParser(json));
        this.parsers = Map.copyOf(configured);
    }

    public ParsedInput parse(SourceFormat format, byte[] content, Collection<String> sourcePaths) {
        return parse(format, content, sourcePaths, null);
    }

    public ParsedInput parse(SourceFormat format, byte[] content, Collection<String> sourcePaths,
            String recordPath) {
        if (content == null || content.length == 0)
            throw new IllegalArgumentException("Input document is empty");
        if (content.length > MAX_INPUT_BYTES)
            throw new IllegalArgumentException("Input document exceeds 10 MB limit");
        if (sourcePaths == null || sourcePaths.isEmpty())
            throw new IllegalArgumentException("Source schema has no fields");
        return parsers.get(format).parse(content, sourcePaths, recordPath);
    }
}
