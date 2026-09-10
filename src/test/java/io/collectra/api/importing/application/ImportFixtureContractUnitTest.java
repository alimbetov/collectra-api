package io.collectra.api.importing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.collectra.api.importing.domain.SourceFormat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

class ImportFixtureContractUnitTest {
    private static final String FIXTURES = "/importing/fixtures/";

    private static final List<String> FLAT_PATHS =
            List.of(
                    "invoice_number",
                    "customer_name",
                    "recipient_email",
                    "recipient_phone",
                    "recipient_whatsapp",
                    "recipient_telegram",
                    "recipient_locale",
                    "preferred_channel",
                    "line_no",
                    "item_code",
                    "item_name",
                    "qty",
                    "price",
                    "amount");

    private final DocumentInputParser parser = new DocumentInputParser(new ObjectMapper());

    @Test
    void csvKeepsTwentyFourOrderedDetailRowsAndBlankValues() throws Exception {
        ParsedInput input =
                parser.parse(
                        SourceFormat.CSV,
                        fixture("invoices-with-items.csv"),
                        FLAT_PATHS);

        assertFlatFixture(input);
    }

    @Test
    void excelKeepsSameTwentyFourOrderedDetailRowsAndBlankValues() throws Exception {
        ParsedInput input =
                parser.parse(
                        SourceFormat.EXCEL,
                        fixture("invoices-with-items.xlsx"),
                        FLAT_PATHS);

        assertFlatFixture(input);
    }

    @Test
    void jsonKeepsThreeDocumentsAndOrderedNestedDetailsIncludingNull() throws Exception {
        ParsedInput input =
                parser.parse(
                        SourceFormat.JSON,
                        fixture("invoices-with-items.json"),
                        List.of(
                                "invoice_number",
                                "recipient_email",
                                "recipient_whatsapp",
                                "preferred_channel",
                                "items[].line_no",
                                "items[].item_code",
                                "items[].item_name"),
                        "$.documents[*]");

        assertThat(input.rows()).hasSize(3);
        assertThat(input.rows())
                .extracting(row -> row.values().get("invoice_number").asText())
                .containsExactly("INV-1001", "INV-1002", "INV-1003");
        assertThat(input.rows())
                .extracting(row -> row.values().get("preferred_channel").asText())
                .containsExactly("EMAIL", "WHATSAPP", "TELEGRAM");
        assertDetailCounts(input, "items[].line_no", 7, 8, 9);

        JsonNode thirdNames = input.rows().get(2).values().get("items[].item_name");
        assertThat(thirdNames).isNotNull();
        assertThat(thirdNames.isArray()).isTrue();
        assertThat(thirdNames.size()).isEqualTo(9);
        assertThat(thirdNames.get(4).isNull()).isTrue();
        assertThat(input.rows().get(2).values().get("recipient_whatsapp").asText()).isEmpty();
    }

    @Test
    void xmlKeepsThreeDocumentsAndRepeatedItemOrder() throws Exception {
        ParsedInput input =
                parser.parse(
                        SourceFormat.XML,
                        fixture("invoices-with-items.xml"),
                        List.of(
                                "invoice_number",
                                "recipient/recipient_email",
                                "recipient/recipient_whatsapp",
                                "recipient/preferred_channel",
                                "items/item/line_no",
                                "items/item/item_code",
                                "items/item/item_name"),
                        "/invoices/invoice");

        assertThat(input.rows()).hasSize(3);
        assertThat(input.rows())
                .extracting(row -> row.values().get("invoice_number").asText())
                .containsExactly("INV-1001", "INV-1002", "INV-1003");
        assertDetailCounts(input, "items/item/line_no", 7, 8, 9);

        JsonNode thirdNames = input.rows().get(2).values().get("items/item/item_name");
        assertThat(thirdNames.size()).isEqualTo(9);
        assertThat(thirdNames.get(4).isNull()).isTrue();
        assertThat(input.rows().get(2).values().get("recipient/recipient_whatsapp").isNull())
                .isTrue();
    }

    @Test
    void oneCFixtureKeepsRecipientAndDetailSemantics() throws Exception {
        ParsedInput input =
                parser.parse(
                        SourceFormat.JSON,
                        fixture("invoices-with-items-1c.json"),
                        List.of(
                                "НомерДокумента",
                                "Получатель.Email",
                                "Получатель.WhatsApp",
                                "Получатель.ПредпочтительныйКанал",
                                "Строки[].НомерСтроки",
                                "Строки[].КодНоменклатуры",
                                "Строки[].Наименование"),
                        "$.Документы[*]");

        assertThat(input.rows()).hasSize(3);
        assertThat(input.rows())
                .extracting(row -> row.values().get("НомерДокумента").asText())
                .containsExactly("INV-1001", "INV-1002", "INV-1003");
        assertDetailCounts(input, "Строки[].НомерСтроки", 7, 8, 9);
        assertThat(input.rows().get(2).values().get("Получатель.WhatsApp").isNull()).isTrue();
        assertThat(input.rows().get(2).values().get("Строки[].Наименование").get(4).isNull())
                .isTrue();
    }

    @Test
    void erpFixtureKeepsExternalFieldNamesWithoutLosingDocumentBoundaries() throws Exception {
        ParsedInput input =
                parser.parse(
                        SourceFormat.JSON,
                        fixture("invoices-with-items-erp.json"),
                        List.of(
                                "BELNR",
                                "CONTACT.SMTP_ADDR",
                                "CONTACT.WHATSAPP",
                                "CONTACT.CHANNEL",
                                "ITEMS[].POSNR",
                                "ITEMS[].MATNR",
                                "ITEMS[].MAKTX"),
                        "$.records[*]");

        assertThat(input.rows()).hasSize(3);
        assertThat(input.rows())
                .extracting(row -> row.values().get("BELNR").asText())
                .containsExactly("INV-1001", "INV-1002", "INV-1003");
        assertThat(input.rows())
                .extracting(row -> row.values().get("CONTACT.CHANNEL").asText())
                .containsExactly("EMAIL", "WHATSAPP", "TELEGRAM");
        assertDetailCounts(input, "ITEMS[].POSNR", 7, 8, 9);
        assertThat(input.rows().get(2).values().get("ITEMS[].MAKTX").get(4).isNull()).isTrue();
    }

    private void assertFlatFixture(ParsedInput input) {
        assertThat(input.rows()).hasSize(24);
        assertThat(input.rows())
                .extracting(ParsedInput.ParsedRow::order)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 24).boxed().toList());

        assertThat(input.rows().subList(0, 7))
                .allMatch(row -> "INV-1001".equals(row.values().get("invoice_number").asText()));
        assertThat(input.rows().subList(7, 15))
                .allMatch(row -> "INV-1002".equals(row.values().get("invoice_number").asText()));
        assertThat(input.rows().subList(15, 24))
                .allMatch(row -> "INV-1003".equals(row.values().get("invoice_number").asText()));

        assertThat(input.rows().subList(0, 7))
                .extracting(row -> row.values().get("line_no").asText())
                .containsExactly("1", "2", "3", "4", "5", "6", "7");
        assertThat(input.rows().subList(7, 15))
                .extracting(row -> row.values().get("line_no").asText())
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
        assertThat(input.rows().subList(15, 24))
                .extracting(row -> row.values().get("line_no").asText())
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");

        ParsedInput.ParsedRow thirdDocumentFifthDetail = input.rows().get(19);
        assertThat(thirdDocumentFifthDetail.values().get("item_code").asText())
                .isEqualTo("SKU-03-005");
        assertThat(thirdDocumentFifthDetail.values().get("item_name").isNull()).isTrue();
        assertThat(thirdDocumentFifthDetail.values().get("recipient_whatsapp").isNull()).isTrue();
        assertThat(thirdDocumentFifthDetail.values().get("recipient_telegram").asText())
                .isEqualTo("@orion_fin");
    }

    private void assertDetailCounts(ParsedInput input, String path, int... expected) {
        for (int index = 0; index < expected.length; index++) {
            JsonNode details = input.rows().get(index).values().get(path);
            assertThat(details.isArray()).isTrue();
            assertThat(details.size()).isEqualTo(expected[index]);
            assertThat(details.get(0).asInt()).isEqualTo(1);
            assertThat(details.get(expected[index] - 1).asInt()).isEqualTo(expected[index]);
        }
    }

    private byte[] fixture(String name) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(FIXTURES + name)) {
            if (input == null) throw new IllegalStateException("Missing fixture: " + name);
            return input.readAllBytes();
        }
    }
}
