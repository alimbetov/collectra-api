package io.collectra.api.localization.application;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import java.util.List;

public record FontProfile(String family, List<FontFace> faces) {
    public FontProfile {
        faces = List.copyOf(faces);
    }

    public record FontFace(String resource, int weight, FontStyle style) {}
}
