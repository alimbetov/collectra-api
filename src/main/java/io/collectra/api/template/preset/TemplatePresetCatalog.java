package io.collectra.api.template.preset;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class TemplatePresetCatalog {
    private static final String CATALOG_RESOURCE = "template-presets/catalog.json";

    private final ObjectMapper objectMapper;
    private Map<TemplatePresetCode, TemplatePreset> presets = Map.of();

    public TemplatePresetCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        try (InputStream input = new ClassPathResource(CATALOG_RESOURCE).getInputStream()) {
            TemplatePreset[] loaded = objectMapper.readValue(input, TemplatePreset[].class);
            EnumMap<TemplatePresetCode, TemplatePreset> indexed =
                    new EnumMap<>(TemplatePresetCode.class);
            for (TemplatePreset preset : loaded) {
                validate(preset);
                TemplatePreset previous = indexed.put(preset.code(), preset);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate template preset: " + preset.code());
                }
            }
            if (indexed.size() != TemplatePresetCode.values().length) {
                throw new IllegalStateException(
                        "Template preset catalog must contain exactly "
                                + TemplatePresetCode.values().length
                                + " presets, found " + indexed.size());
            }
            presets = Collections.unmodifiableMap(indexed);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load template preset catalog", e);
        }
    }

    public List<TemplatePreset> list() {
        return Arrays.stream(TemplatePresetCode.values()).map(this::require).toList();
    }

    public TemplatePreset require(TemplatePresetCode code) {
        TemplatePreset preset = presets.get(code);
        if (preset == null) {
            throw new IllegalArgumentException("Unknown template preset: " + code);
        }
        return preset;
    }

    private static void validate(TemplatePreset preset) {
        if (preset == null || preset.code() == null) {
            throw new IllegalStateException("Template preset code is required");
        }
        if (preset.category() == null || preset.category().isBlank()) {
            throw new IllegalStateException("Template preset category is required: " + preset.code());
        }
        if (preset.supportedChannels() == null || preset.supportedChannels().isEmpty()) {
            throw new IllegalStateException("Template preset channels are required: " + preset.code());
        }
        if (preset.translations() == null || preset.translations().isEmpty()) {
            throw new IllegalStateException("Template preset translations are required: " + preset.code());
        }
        for (String requiredLocale : List.of("en", "ru", "kk")) {
            TemplatePresetTranslation translation = preset.translations().get(requiredLocale);
            if (translation == null || translation.text() == null || translation.text().isBlank()
                    || translation.html() == null || translation.html().isBlank()) {
                throw new IllegalStateException(
                        "Template preset " + preset.code()
                                + " must contain complete " + requiredLocale + " translation");
            }
        }
    }
}
