package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.collectra.api.document.application.PdfRenderer;
import io.collectra.api.localization.application.FontProfileRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

class PdfRenderingAssuranceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private PdfRenderer pdfRenderer;

    @Autowired private FontProfileRegistry fonts;

    @Test
    void allConfiguredFontResourcesAreBundled() {
        fonts.verifyBundledResources();
    }

    @ParameterizedTest(name = "renders semantic PDF text for locale {0}")
    @MethodSource("localeSamples")
    void rendersValidPdfWithExtractableUnicodeAndEmbeddedFonts(String locale, String sample)
            throws IOException {
        byte[] pdf =
                pdfRenderer.render(
                        "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body>"
                                + "<h1>Collectra</h1><p>"
                                + sample
                                + "</p></body></html>",
                        locale);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        try (PDDocument document = PDDocument.load(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            assertThat(new PDFTextStripper().getText(document))
                    .contains("Collectra")
                    .contains(sample);
            assertAllReferencedFontsAreEmbedded(document);
        }
    }

    @Test
    void rendersLargeTableAcrossMultiplePagesWithoutTruncatingLastRow() throws IOException {
        StringBuilder html =
                new StringBuilder(
                        "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
                                + "table{width:100%;border-collapse:collapse}td{padding:4px;border:1px solid #333}"
                                + "</style></head><body><h1>Large invoice</h1><table>");
        for (int i = 1; i <= 150; i++) {
            html.append("<tr><td>Row ")
                    .append(i)
                    .append("</td><td>")
                    .append(i * 100)
                    .append(" KZT</td></tr>");
        }
        html.append("</table></body></html>");

        byte[] pdf = pdfRenderer.render(html.toString(), "en");

        try (PDDocument document = PDDocument.load(pdf)) {
            String extracted = new PDFTextStripper().getText(document);
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            assertThat(extracted).contains("Row 1").contains("Row 150").contains("15000 KZT");
            assertAllReferencedFontsAreEmbedded(document);
        }
    }

    private static Stream<Arguments> localeSamples() {
        return Stream.of(
                Arguments.of("en", "Invoice amount 125000.50 KZT"),
                Arguments.of("ru", "Счёт на оплату 125000.50 KZT"),
                Arguments.of("kk", "Төлем сомасы 125000.50 KZT"),
                Arguments.of("zh-CN", "应付金额 125000.50 KZT"));
    }

    private void assertAllReferencedFontsAreEmbedded(PDDocument document) throws IOException {
        boolean foundFont = false;
        for (PDPage page : document.getPages()) {
            if (page.getResources() == null) {
                continue;
            }
            for (COSName fontName : page.getResources().getFontNames()) {
                PDFont font = page.getResources().getFont(fontName);
                if (font == null) {
                    continue;
                }
                foundFont = true;
                assertThat(font.isEmbedded())
                        .as("font %s must be embedded", font.getName())
                        .isTrue();
            }
        }
        assertThat(foundFont).as("at least one PDF font must be referenced").isTrue();
    }
}
