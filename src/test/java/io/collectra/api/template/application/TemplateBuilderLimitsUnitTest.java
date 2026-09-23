package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.shared.error.InvalidRequestException;
import org.junit.jupiter.api.Test;

class TemplateBuilderLimitsUnitTest {
    private final TemplateBuilderLimits limits = new TemplateBuilderLimits();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void rejectsOversizedTemplateContent() {
        String content = "x".repeat(TemplateBuilderLimits.MAX_CONTENT_BYTES + 1);

        assertThatThrownBy(() -> limits.validateDraft(content, null))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code")
                .isEqualTo("TEMPLATE_CONTENT_TOO_LARGE");
    }

    @Test
    void rejectsDeepPreviewPayload() {
        var root = json.createObjectNode();
        var current = root;
        for (int i = 0; i < TemplateBuilderLimits.MAX_JSON_DEPTH + 2; i++) {
            current = current.putObject("n" + i);
        }

        assertThatThrownBy(() -> limits.validatePreviewPayload(root))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code")
                .isEqualTo("PREVIEW_PAYLOAD_TOO_LARGE");
    }
}
