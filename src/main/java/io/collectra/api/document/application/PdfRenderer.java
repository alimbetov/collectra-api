package io.collectra.api.document.application;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class PdfRenderer {
    public byte[] render(String html) {
        try (var output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withW3cDocument(new W3CDom().fromJsoup(Jsoup.parse(html)), null);
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new PdfRenderingException("Cannot render PDF", ex);
        }
    }

    public static class PdfRenderingException extends RuntimeException {
        public PdfRenderingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
