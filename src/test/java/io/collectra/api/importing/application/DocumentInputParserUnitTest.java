package io.collectra.api.importing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.collectra.api.importing.domain.SourceFormat;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

class DocumentInputParserUnitTest {
    private final DocumentInputParser parser = new DocumentInputParser(new ObjectMapper());

    @Test
    void extractsJsonValuesIncludingArrayPaths() {
        ParsedInput input =
                parser.parse(
                        SourceFormat.JSON,
                        """
                        {"document":{"number":"INV-1"},"items":[{"name":"A"},{"name":"B"}]}
                        """
                                .getBytes(StandardCharsets.UTF_8),
                        List.of("document.number", "items[].name"));

        assertThat(input.valuesAt("document.number"))
                .extracting(node -> node.asText())
                .containsExactly("INV-1");
        assertThat(input.valuesAt("items[].name"))
                .extracting(node -> node.asText())
                .containsExactly("A", "B");
    }

    @Test
    void extractsSemicolonCsvColumnsAcrossRows() {
        ParsedInput input =
                parser.parse(
                        SourceFormat.CSV,
                        "Name;Amount\nAlpha;10,50\nBeta;20,00\n".getBytes(StandardCharsets.UTF_8),
                        List.of("Name", "Amount"));

        assertThat(input.valuesAt("Name"))
                .extracting(node -> node.asText())
                .containsExactly("Alpha", "Beta");
        assertThat(input.valuesAt("Amount"))
                .extracting(node -> node.asText())
                .containsExactly("10,50", "20,00");
    }

    @Test
    void extractsFirstExcelSheetByHeader() throws Exception {
        byte[] workbook;
        try (var excel = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {
            var sheet = excel.createSheet("Data");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Customer");
            header.createCell(1).setCellValue("Amount");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Alpha");
            row.createCell(1).setCellValue(125.5);
            excel.write(output);
            workbook = output.toByteArray();
        }

        ParsedInput input =
                parser.parse(SourceFormat.EXCEL, workbook, List.of("Customer", "Amount"));
        assertThat(input.valuesAt("Customer").get(0).asText()).isEqualTo("Alpha");
        assertThat(input.valuesAt("Amount").get(0).asText()).isEqualTo("125.5");
    }

    @Test
    void extractsXmlWithXPathAndRejectsDoctype() {
        ParsedInput input =
                parser.parse(
                        SourceFormat.XML,
                        """
<invoice><number>INV-1</number><items><item>A</item><item>B</item></items></invoice>
"""
                                .getBytes(StandardCharsets.UTF_8),
                        List.of("/invoice/number", "//item"));
        assertThat(input.valuesAt("//item"))
                .extracting(node -> node.asText())
                .containsExactly("A", "B");

        assertThatThrownBy(
                        () ->
                                parser.parse(
                                        SourceFormat.XML,
                                        """
<!DOCTYPE invoice [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
<invoice><number>&xxe;</number></invoice>
"""
                                                .getBytes(StandardCharsets.UTF_8),
                                        List.of("/invoice/number")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid XML");
    }
}
