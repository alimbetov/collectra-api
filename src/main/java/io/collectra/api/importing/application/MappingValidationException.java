package io.collectra.api.importing.application;

import java.util.List;

public class MappingValidationException extends IllegalArgumentException {
    private final List<Violation> violations;

    public MappingValidationException(List<Violation> violations) {
        super("Input document does not satisfy mapping requirements");
        this.violations = List.copyOf(violations);
    }

    public List<Violation> getViolations() {
        return violations;
    }

    public record Violation(String sourcePath, String targetKey, String code, String message) {}
}
