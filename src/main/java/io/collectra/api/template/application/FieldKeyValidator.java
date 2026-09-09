package io.collectra.api.template.application;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FieldKeyValidator {
    private static final String CUSTOM_NAMESPACE = "custom";
    private static final int MIN_CUSTOM_SEGMENTS = 3;

    public FieldPath validatePlaceholderKey(String key) {
        return PlaceholderGrammar.parse(key);
    }

    public FieldPath validateCustomFieldKey(String key) {
        FieldPath path = validatePlaceholderKey(key);
        List<String> segments = path.segments();
        if (segments.size() < MIN_CUSTOM_SEGMENTS
                || !CUSTOM_NAMESPACE.equals(segments.get(0))) {
            throw new IllegalArgumentException(
                    "Custom field key must match custom.<namespace>.<name> and be a valid placeholder key");
        }
        return path;
    }
}
