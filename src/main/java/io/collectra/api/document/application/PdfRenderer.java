package io.collectra.api.document.application;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import io.collectra.api.localization.application.FontProfileRegistry;
import io.collectra.api.localization.application.SupportedLocaleService;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class PdfRenderer {
    private final SupportedLocaleService locales;
    private final FontProfileRegistry fonts;

    public PdfRenderer(SupportedLocaleService locales, FontProfileRegistry fonts) {
        this.locales = locales;
        this.fonts = fonts;
    }

    public byte[] render(String html, String locale) {
        var supportedLocale = locales.requireSupported(locale);
        try (var output = new ByteArrayOutputStream()) {
            var document = Jsoup.parse(html);
            document.selectFirst("html").attr("lang", supportedLocale.getCode());
            document.head()
                    .appendElement("style")
                    .attr("data-collectra-font-profile", supportedLocale.getFontProfile().name())
                    .text(
                            "html, body, body * { font-family: "
                                    + fonts.cssStack(supportedLocale.getFontProfile())
                                    + " !important; }");

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            fonts.register(builder, supportedLocale.getFontProfile());
            builder.withW3cDocument(new W3CDom().fromJsoup(document), null);
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new PdfRenderingException(
                    "Cannot render PDF for locale " + supportedLocale.getCode(), ex);
        }
    }

    public static class PdfRenderingException extends RuntimeException {
        public PdfRenderingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
