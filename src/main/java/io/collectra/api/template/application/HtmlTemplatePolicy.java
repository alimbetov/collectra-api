package io.collectra.api.template.application;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class HtmlTemplatePolicy {
    private final Cleaner cleaner;

    public HtmlTemplatePolicy() {
        Safelist allowed =
                Safelist.relaxed()
                        .addTags("html", "head", "body", "meta", "title", "thead", "tbody", "tfoot")
                        .addAttributes(":all", "class", "id")
                        .addAttributes("meta", "charset")
                        .addAttributes("td", "colspan", "rowspan")
                        .addAttributes("th", "colspan", "rowspan");
        this.cleaner = new Cleaner(allowed);
    }

    public String sanitize(String html) {
        Document dirty = Jsoup.parseBodyFragment(html);
        Document clean = cleaner.clean(dirty);
        return clean.body().html();
    }

    public void validateStylesheet(String stylesheet) {
        if (stylesheet == null) return;
        String normalized = stylesheet.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("url(") || normalized.contains("@import"))
            throw new IllegalArgumentException(
                    "Stylesheet cannot load external resources; use managed template assets");
    }
}
