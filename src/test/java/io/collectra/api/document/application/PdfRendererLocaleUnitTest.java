package io.collectra.api.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.collectra.api.localization.application.FontProfileRegistry;
import io.collectra.api.localization.application.SupportedLocaleService;
import io.collectra.api.localization.domain.FontProfileCode;
import io.collectra.api.localization.domain.SupportedLocale;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PdfRendererLocaleUnitTest {
    @ParameterizedTest
    @MethodSource("localeSamples")
    void rendersPdfUsingBundledLocaleFont(String locale, FontProfileCode profile, String sample)
            throws IOException {
        SupportedLocaleService locales = mock(SupportedLocaleService.class);
        SupportedLocale supported = mock(SupportedLocale.class);
        when(supported.getCode()).thenReturn(locale);
        when(supported.getFontProfile()).thenReturn(profile);
        when(locales.requireSupported(locale)).thenReturn(supported);
        PdfRenderer renderer = new PdfRenderer(locales, new FontProfileRegistry());

        byte[] pdf =
                renderer.render(
                        "<html><body><strong>" + sample + "</strong></body></html>", locale);

        assertThat(pdf.length).isGreaterThan(1_000);
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(normalizePdfText(extractText(pdf))).contains(normalizePdfText(sample));
    }

    private static String extractText(byte[] pdf) throws IOException {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdf))) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static String normalizePdfText(String value) {
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static Stream<Arguments> localeSamples() {
        return Stream.of(
                Arguments.of("kk", FontProfileCode.LATIN_CYRILLIC, "Қазақша Русский O‘zbekcha"),
                Arguments.of("hy", FontProfileCode.ARMENIAN, "Հայերեն"),
                Arguments.of("ka", FontProfileCode.GEORGIAN, "ქართული"),
                Arguments.of("zh-CN", FontProfileCode.CJK_SC, "发票编号 中文测试 客户"));
    }
}
