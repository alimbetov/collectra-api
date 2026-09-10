package io.collectra.api.template.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TemplateVersionUnitTest {

    @Test
    void supportsDraftValidatePublishArchiveLifecycle() {
        TemplateVersion version = version();

        assertThat(version.getStatus()).isEqualTo(TemplateVersionStatus.DRAFT);

        version.validated();
        assertThat(version.getStatus()).isEqualTo(TemplateVersionStatus.VALIDATED);

        version.publish();
        assertThat(version.getStatus()).isEqualTo(TemplateVersionStatus.PUBLISHED);

        version.archive();
        assertThat(version.getStatus()).isEqualTo(TemplateVersionStatus.ARCHIVED);
    }

    @Test
    void allowsEditingOnlyDraftVersions() {
        TemplateVersion version = version();
        version.update("<p>{{customer.name}}</p>", "p { margin: 0; }");

        assertThat(version.getContentHtml()).contains("customer.name");
        assertThat(version.getStylesheet()).contains("margin");

        version.validated();
        assertThatThrownBy(() -> version.update("<p>changed</p>", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only draft");
    }

    @Test
    void builderManagedVersionCannotBeOverwrittenThroughRawHtmlUpdate() throws Exception {
        var builderJson =
                new ObjectMapper()
                        .readTree(
                                """
                                {"version":"1.0","blocks":[]}
                                """);
        TemplateVersion version =
                new TemplateVersion(
                        UUID.randomUUID(),
                        1,
                        "ru",
                        TemplateChannel.PDF,
                        null,
                        builderJson,
                        "<p>{{customer.name}}</p>",
                        null);

        assertThat(version.isBuilderManaged()).isTrue();
        assertThatThrownBy(() -> version.update("<p>raw bypass</p>", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("builder API");
        assertThat(version.getBuilderJson()).isEqualTo(builderJson);
    }

    @Test
    void builderUpdateAtomicallyReplacesTreeAndDerivedHtml() throws Exception {
        ObjectMapper json = new ObjectMapper();
        var first = json.readTree("{\"version\":\"1.0\",\"blocks\":[]}");
        var second =
                json.readTree(
                        """
                        {"version":"1.0","blocks":[{"type":"richText","props":{"content":[]}}]}
                        """);
        TemplateVersion version =
                new TemplateVersion(
                        UUID.randomUUID(),
                        1,
                        "ru",
                        TemplateChannel.PDF,
                        null,
                        first,
                        "<p>first</p>",
                        null);

        version.updateBuilder(null, second, "<div>second</div>", "div { margin: 0; }");

        assertThat(version.getBuilderJson()).isEqualTo(second);
        assertThat(version.getContentHtml()).isEqualTo("<div>second</div>");
        assertThat(version.getStylesheet()).contains("margin");
    }

    @Test
    void validatedVersionCanBeReopenedForEditing() {
        TemplateVersion version = version();
        version.validated();
        version.reopen();

        assertThat(version.getStatus()).isEqualTo(TemplateVersionStatus.DRAFT);
        version.update("<p>{{document.number}}</p>", null);
        assertThat(version.getContentHtml()).contains("document.number");
    }

    @Test
    void rejectsInvalidLifecycleTransitions() {
        TemplateVersion draft = version();
        assertThatThrownBy(draft::publish).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(draft::archive).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(draft::reopen).isInstanceOf(IllegalStateException.class);

        TemplateVersion validated = version();
        validated.validated();
        assertThatThrownBy(validated::archive).isInstanceOf(IllegalStateException.class);
    }

    private TemplateVersion version() {
        return new TemplateVersion(
                UUID.randomUUID(), 1, "ru-kz", "<p>{{customer.name}}</p>", null);
    }
}
