package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.collectra.api.template.application.TemplateCompiler;
import io.collectra.api.template.application.TemplateRenderer;
import io.collectra.api.template.domain.FieldDataType;
import io.collectra.api.template.infrastructure.FieldDefinitionRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SystemFieldRoundTripIntegrationTest extends AbstractIntegrationTest {
    @Autowired FieldDefinitionRepository fields;
    @Autowired TemplateCompiler compiler;
    @Autowired TemplateRenderer renderer;
    @Autowired ObjectMapper json;

    @Test
    void everySeededSystemFieldCompilesAndResolvesAgainstRepresentativePayload() {
        var systemFields =
                fields.findAvailable(UUID.randomUUID()).stream()
                        .filter(field -> field.getTenantId() == null)
                        .filter(field -> !field.isCollection())
                        .toList();

        assertThat(systemFields).isNotEmpty();

        ObjectNode payload = json.createObjectNode();
        StringBuilder html = new StringBuilder("<html><body>");
        int index = 0;
        for (var field : systemFields) {
            String marker = putRepresentativeValue(payload, field.getKey(), field.getDataType(), index++);
            html.append("<p>{{").append(field.getKey()).append("}}</p>");
            if (field.getDataType() == FieldDataType.STRING
                    || field.getDataType() == FieldDataType.DATE
                    || field.getDataType() == FieldDataType.DATETIME) {
                assertThat(marker).isNotBlank();
            }
        }
        html.append("</body></html>");

        var compiled = compiler.compileBody(UUID.randomUUID(), html.toString(), null);
        String rendered = renderer.render(compiled, payload).html();

        assertThat(rendered).doesNotContain("{{");
        assertThat(compiled.tokens()).isNotEmpty();
    }

    private String putRepresentativeValue(
            ObjectNode root, String key, FieldDataType type, int index) {
        String[] segments = key.split("\\.");
        ObjectNode current = root;
        for (int i = 0; i < segments.length - 1; i++) {
            current = current.with(segments[i]);
        }
        String leaf = segments[segments.length - 1];
        return switch (type) {
            case STRING, OBJECT -> {
                String value = "value-" + index;
                current.put(leaf, value);
                yield value;
            }
            case DATE -> {
                String value = "2026-09-" + String.format("%02d", (index % 27) + 1);
                current.put(leaf, value);
                yield value;
            }
            case DATETIME -> {
                String value = "2026-09-23T10:15:30Z";
                current.put(leaf, value);
                yield value;
            }
            case DECIMAL -> {
                current.put(leaf, 1000.25 + index);
                yield String.valueOf(1000.25 + index);
            }
            case INTEGER -> {
                current.put(leaf, 100 + index);
                yield String.valueOf(100 + index);
            }
            case BOOLEAN -> {
                current.put(leaf, true);
                yield "true";
            }
        };
    }
}
