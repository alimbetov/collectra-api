package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.collectra.api.template.domain.TemplateVersion;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class TemplateRendererUnitTest {
    private final ObjectMapper json = new ObjectMapper();
    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void rendersNestedValuesEscapesDataAndAddsStylesheet() throws Exception {
        TemplateVersion version =
                new TemplateVersion(
                        UUID.randomUUID(),
                        1,
                        "ru-KZ",
                        "<h1>{{document.number}}</h1><p>{{customer.name}}</p>",
                        "h1 { color: navy; }");
        var payload =
                json.readTree(
                        """
                        {"document":{"number":"INV-1"},"customer":{"name":"A&B <Company>"}}
                        """);

        String html = renderer.render(version, payload).html();

        assertThat(html)
                .contains(
                        "<!DOCTYPE html>",
                        "<h1>INV-1</h1>",
                        "A&amp;B &lt;Company&gt;",
                        "<style>h1 { color: navy; }</style>");
    }

    @Test
    void rejectsMissingValuesAndUnescapedExpressions() throws Exception {
        TemplateVersion missing =
                new TemplateVersion(
                        UUID.randomUUID(), 1, "ru-KZ", "<p>{{customer.name}}</p>", null);
        assertThatThrownBy(() -> renderer.render(missing, json.readTree("{}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("customer.name");

        TemplateVersion unsafe =
                new TemplateVersion(
                        UUID.randomUUID(), 1, "ru-KZ", "<p>{{{customer.html}}}</p>", null);
        assertThatThrownBy(
                        () ->
                                renderer.render(
                                        unsafe,
                                        json.readTree("{\"customer\":{\"html\":\"<b>x</b>\"}}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unescaped");
    }
}
