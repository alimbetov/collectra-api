package io.collectra.api.template.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TemplateChannelVersionUnitTest {
    @Test
    void emailRequiresSubjectAndKeepsChannel() {
        assertThatThrownBy(
                        () ->
                                new TemplateVersion(
                                        UUID.randomUUID(),
                                        1,
                                        "ru",
                                        TemplateChannel.EMAIL,
                                        null,
                                        "<p>Hello</p>",
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subject");

        TemplateVersion version =
                new TemplateVersion(
                        UUID.randomUUID(),
                        1,
                        "ru",
                        TemplateChannel.EMAIL,
                        "Invoice {{document.number}}",
                        "<p>Hello</p>",
                        null);

        assertThat(version.getChannel()).isEqualTo(TemplateChannel.EMAIL);
        assertThat(version.getSubject()).isEqualTo("Invoice {{document.number}}");
    }

    @Test
    void legacyConstructorDefaultsToPdf() {
        TemplateVersion version =
                new TemplateVersion(UUID.randomUUID(), 1, "ru", "<p>PDF</p>", null);
        assertThat(version.getChannel()).isEqualTo(TemplateChannel.PDF);
        assertThat(version.getSubject()).isNull();
    }
}
