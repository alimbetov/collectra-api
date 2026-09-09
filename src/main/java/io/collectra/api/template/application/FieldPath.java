package io.collectra.api.template.application;

import java.util.List;
import java.util.Objects;

public record FieldPath(List<String> segments) {

    public FieldPath {
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        if (segments.size() < 2) {
            throw new IllegalArgumentException("Placeholder path must contain at least two segments");
        }
        if (segments.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Placeholder path contains an empty segment");
        }
    }

    public String canonical() {
        return String.join(".", segments);
    }

    public String jsonPointer() {
        return "/" + String.join("/", segments);
    }
}
