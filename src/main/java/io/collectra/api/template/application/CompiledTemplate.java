package io.collectra.api.template.application;

import java.util.List;
import java.util.UUID;

public record CompiledTemplate(UUID templateVersionId, List<TemplateToken> tokens, String stylesheet) {
    public CompiledTemplate {
        tokens = List.copyOf(tokens);
    }
}
