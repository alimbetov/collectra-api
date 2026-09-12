package io.collectra.api.importing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.importing.domain.SourceFormat;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class Slice10aP02ParserSecurityUnitTest {
    private final DocumentInputParser parser = new DocumentInputParser(new ObjectMapper());

    @Test
    void rejectsBillionLaughsStyleEntityExpansionBeforeExpansionCanOccur() {
        String payload =
                """
                <?xml version="1.0"?>
                <!DOCTYPE lolz [
                  <!ENTITY lol "lol">
                  <!ELEMENT lolz (#PCDATA)>
                  <!ENTITY lol1 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">
                  <!ENTITY lol2 "&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;">
                  <!ENTITY lol3 "&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;">
                ]>
                <lolz>&lol3;</lolz>
                """;

        assertThatThrownBy(
                        () ->
                                parser.parse(
                                        SourceFormat.XML,
                                        payload.getBytes(StandardCharsets.UTF_8),
                                        List.of("/lolz")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid XML");
    }

    @Test
    void rejectsInputLargerThanTenMegabytesBeforeFormatParserRuns() {
        byte[] payload = new byte[DocumentInputParser.MAX_INPUT_BYTES + 1];

        assertThatThrownBy(() -> parser.parse(SourceFormat.JSON, payload, List.of("id")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 MB");
    }

    @Test
    void rejectsCsvBeyondTenThousandRows() {
        StringBuilder csv = new StringBuilder("Id,Value\n");
        for (int i = 0; i < 10_001; i++) {
            csv.append(i).append(',').append("value").append(i).append('\n');
        }

        assertThatThrownBy(
                        () ->
                                parser.parse(
                                        SourceFormat.CSV,
                                        csv.toString().getBytes(StandardCharsets.UTF_8),
                                        List.of("Id", "Value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10000 rows");
    }

    @Test
    void rejectsExcelBeyondTenThousandRows() throws Exception {
        byte[] workbook;
        try (var excel = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = excel.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("Id");
            for (int i = 1; i <= 10_001; i++) {
                sheet.createRow(i).createCell(0).setCellValue(i);
            }
            excel.write(output);
            workbook = output.toByteArray();
        }

        assertThatThrownBy(() -> parser.parse(SourceFormat.EXCEL, workbook, List.of("Id")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10000 rows");
    }

    @Test
    void rejectsCorruptExcelDocument() {
        byte[] corrupt = "not-an-xlsx".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(SourceFormat.EXCEL, corrupt, List.of("Id")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Excel");
    }

    @Test
    void csvFormulaLookingValueRemainsLiteralDataAndIsNeverEvaluatedByImportParser() {
        String dangerous = "=CMD|' /C calc'!A0";
        String csv = "Value\n\"" + dangerous + "\"\n";

        ParsedInput input =
                parser.parse(
                        SourceFormat.CSV,
                        csv.getBytes(StandardCharsets.UTF_8),
                        List.of("Value"));

        assertThat(input.rows()).hasSize(1);
        assertThat(input.rows().get(0).values().get("Value").asText()).isEqualTo(dangerous);
    }

    @Test
    void excelFormulaIsConvertedToAValueRatherThanPersistedAsFormulaText() throws Exception {
        byte[] workbook;
        try (var excel = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = excel.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("Value");
            sheet.createRow(1).createCell(0).setCellFormula("1+1");
            excel.write(output);
            workbook = output.toByteArray();
        }

        ParsedInput input = parser.parse(SourceFormat.EXCEL, workbook, List.of("Value"));

        assertThat(input.rows()).hasSize(1);
        assertThat(input.rows().get(0).values().get("Value").asText()).isEqualTo("2");
        assertThat(input.rows().get(0).values().get("Value").asText()).doesNotStartWith("=");
    }
}
