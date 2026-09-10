package io.collectra.api.template.application;

import java.util.Locale;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class HtmlTemplatePolicy {
    private static final Pattern ASSET_PLACEHOLDER =
            Pattern.compile("\\{\\{\\s*asset\\.[a-z][a-z0-9_]*\\s*}}", Pattern.CASE_INSENSITIVE);
    private static final String MANAGED_ASSET_ATTRIBUTE = "data-collectra-managed-asset";
    private static final String MANAGED_ASSET_SENTINEL =
            "data:image/gif;base64,R0lGODlhAQABAAAAACw=";

    private final Cleaner cleaner;

    public HtmlTemplatePolicy() {
        Safelist allowed =
                Safelist.relaxed()
                        .addTags(
                                "html",
                                "head",
                                "body",
                                "meta",
                                "title",
                                "thead",
                                "tbody",
                                "tfoot")
                        .addAttributes(":all", "class", "id")
                        .addAttributes("meta", "charset")
                        .addAttributes("td", "colspan", "rowspan")
                        .addAttributes("th", "colspan", "rowspan")
                        .addAttributes("img", MANAGED_ASSET_ATTRIBUTE)
                        .addProtocols("img", "src", "data");
        this.cleaner = new Cleaner(allowed);
    }

    public String sanitize(String html) {
        Document dirty = Jsoup.parseBodyFragment(html);
        dirty.select("img[src]")
                .forEach(
                        image -> {
                            String raw = image.attr("src").trim();
                            String source = raw.toLowerCase(Locale.ROOT);
                            boolean inlineImage =
                                    source.startsWith("data:image/png;base64,")
                                            || source.startsWith("data:image/jpeg;base64,")
                                            || source.startsWith("data:image/gif;base64,");
                            boolean managedAsset = ASSET_PLACEHOLDER.matcher(raw).matches();
                            if (!inlineImage && !managedAsset) {
                                throw new IllegalArgumentException(
                                        "External image URLs are forbidden; use {{asset.<key>}} with a managed template asset");
                            }
                            if (managedAsset) {
                                image.attr(MANAGED_ASSET_ATTRIBUTE, raw);
                                image.attr("src", MANAGED_ASSET_SENTINEL);
                            }
                        });
        Document clean = cleaner.clean(dirty);
        clean.select("img[" + MANAGED_ASSET_ATTRIBUTE + "]")
                .forEach(
                        image -> {
                            image.attr("src", image.attr(MANAGED_ASSET_ATTRIBUTE));
                            image.removeAttr(MANAGED_ASSET_ATTRIBUTE);
                        });
        return clean.body().html();
    }

    public void validateStylesheet(String stylesheet) {
        if (stylesheet == null) return;
        String normalized = stylesheet.toLowerCase(Locale.ROOT);
        if (normalized.contains("url(") || normalized.contains("@import")) {
            throw new IllegalArgumentException(
                    "Stylesheet cannot load external resources; use managed template assets");
        }
    }
}
