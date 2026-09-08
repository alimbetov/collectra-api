package io.collectra.api.importing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ExcelInputParser implements InputParser {
    private static final int MAX_ROWS = 10_000;
    private final ObjectMapper json;

    ExcelInputParser(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public ParsedInput parse(byte[] content, Collection<String> sourcePaths) {
        Map<String, List<JsonNode>> result = new LinkedHashMap<>();
        sourcePaths.forEach(path -> result.put(path, new ArrayList<>()));
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0)
                throw new IllegalArgumentException("Excel workbook has no sheets");
            var sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) throw new IllegalArgumentException("Excel header row is missing");
            DataFormatter formatter = new DataFormatter(java.util.Locale.ROOT);
            var evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Map<String, Integer> columns = new LinkedHashMap<>();
            for (int column = header.getFirstCellNum();
                    column < header.getLastCellNum();
                    column++) {
                String name = formatter.formatCellValue(header.getCell(column), evaluator).trim();
                if (!name.isEmpty()) columns.put(name, column);
            }
            int rows = 0;
            for (int index = header.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) continue;
                if (++rows > MAX_ROWS)
                    throw new IllegalArgumentException("Excel exceeds 10000 rows");
                for (String path : sourcePaths) {
                    Integer column = columns.get(path);
                    if (column == null) continue;
                    String value = formatter.formatCellValue(row.getCell(column), evaluator).trim();
                    if (!value.isEmpty())
                        result.get(path).add(json.getNodeFactory().textNode(value));
                }
            }
            Map<String, List<JsonNode>> immutable = new LinkedHashMap<>();
            result.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
            return new ParsedInput(immutable);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid Excel document", ex);
        }
    }
}
