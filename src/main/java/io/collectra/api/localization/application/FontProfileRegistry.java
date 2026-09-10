package io.collectra.api.localization.application;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import io.collectra.api.localization.domain.FontProfileCode;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class FontProfileRegistry {
    private static final FontProfileCode BASE_PROFILE = FontProfileCode.LATIN_CYRILLIC;

    private static final Map<FontProfileCode, FontProfile> PROFILES =
            Map.of(
                    FontProfileCode.LATIN_CYRILLIC,
                    new FontProfile(
                            "Noto Sans",
                            List.of(
                                    face("fonts/latin-cyrillic/NotoSans-Regular.ttf", 400, FontStyle.NORMAL),
                                    face("fonts/latin-cyrillic/NotoSans-Bold.ttf", 700, FontStyle.NORMAL),
                                    face("fonts/latin-cyrillic/NotoSans-Italic.ttf", 400, FontStyle.ITALIC),
                                    face("fonts/latin-cyrillic/NotoSans-BoldItalic.ttf", 700, FontStyle.ITALIC))),
                    FontProfileCode.ARMENIAN,
                    new FontProfile(
                            "Noto Sans Armenian",
                            List.of(
                                    face("fonts/armenian/NotoSansArmenian-Regular.ttf", 400, FontStyle.NORMAL),
                                    face("fonts/armenian/NotoSansArmenian-Bold.ttf", 700, FontStyle.NORMAL))),
                    FontProfileCode.GEORGIAN,
                    new FontProfile(
                            "Noto Sans Georgian",
                            List.of(
                                    face("fonts/georgian/NotoSansGeorgian-Regular.ttf", 400, FontStyle.NORMAL),
                                    face("fonts/georgian/NotoSansGeorgian-Bold.ttf", 700, FontStyle.NORMAL))),
                    FontProfileCode.CJK_SC,
                    new FontProfile(
                            "Noto Sans CJK SC",
                            List.of(
                                    face("fonts/cjk-sc/NotoSansCJKsc-Regular.otf", 400, FontStyle.NORMAL),
                                    face("fonts/cjk-sc/NotoSansCJKsc-Bold.otf", 700, FontStyle.NORMAL))));

    public FontProfile require(FontProfileCode code) {
        FontProfile profile = PROFILES.get(code);
        if (profile == null) {
            throw new IllegalArgumentException("Unsupported font profile: " + code);
        }
        return profile;
    }

    public FontProfile register(PdfRendererBuilder builder, FontProfileCode code) {
        FontProfile base = require(BASE_PROFILE);
        registerProfile(builder, base);
        FontProfile selected = require(code);
        if (code != BASE_PROFILE) {
            registerProfile(builder, selected);
        }
        return selected;
    }

    public String cssStack(FontProfileCode code) {
        FontProfile selected = require(code);
        if (code == BASE_PROFILE) {
            return quote(selected.family()) + ", sans-serif";
        }
        return quote(selected.family()) + ", " + quote(require(BASE_PROFILE).family()) + ", sans-serif";
    }

    public void verifyBundledResources() {
        PROFILES.values().stream()
                .flatMap(profile -> profile.faces().stream())
                .forEach(face -> requireResource(face.resource()));
    }

    private void registerProfile(PdfRendererBuilder builder, FontProfile profile) {
        for (FontProfile.FontFace face : profile.faces()) {
            requireResource(face.resource());
            builder.useFont(
                    () -> open(face.resource()),
                    profile.family(),
                    face.weight(),
                    face.style(),
                    true);
        }
    }

    private static FontProfile.FontFace face(String resource, int weight, FontStyle style) {
        return new FontProfile.FontFace(resource, weight, style);
    }

    private String quote(String family) {
        return "\"" + family.replace("\"", "") + "\"";
    }

    private void requireResource(String resource) {
        if (!new ClassPathResource(resource).exists()) {
            throw new IllegalStateException("Required bundled font is missing: " + resource);
        }
    }

    private InputStream open(String resource) {
        try {
            return new ClassPathResource(resource).getInputStream();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot open bundled font: " + resource, ex);
        }
    }
}
