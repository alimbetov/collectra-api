package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.template.domain.TemplateVersion;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TemplateRendererUnitTest {
    private final ObjectMapper json = new ObjectMapper();
    private final HtmlTemplatePolicy policy = new HtmlTemplatePolicy();
    private final TemplateCompiler compiler = new TemplateCompiler(policy, new PlaceholderScanner());
    private final TemplateRenderer renderer = new TemplateRenderer(compiler);

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
    void textRenderingDoesNotHtmlEscapeMessageValues() throws Exception {
        CompiledTemplate compiled =
                compiler.compileText(UUID.randomUUID(), "Hello {{recipient.name}}: {{invoice.number}}");

        String text =
                renderer.renderText(
                        compiled,
                        json.readTree(
                                """
                                {"recipient":{"name":"A&B <Company>"},"invoice":{"number":"INV&1"}}
                                """));

        assertThat(text).isEqualTo("Hello A&B <Company>: INV&1");
    }

    @Test
    void rendersCompiledTemplateWithoutRequiringSourceTemplateAgain() throws Exception {
        TemplateVersion version =
                new TemplateVersion(
                        UUID.randomUUID(),
                        1,
                        "ru-KZ",
                        "<p>{{customer.name}} / {{document.number}}</p>",
                        null);
        CompiledTemplate compiled = compiler.compile(version);

        String html =
                renderer.render(
                                compiled,
                                json.readTree(
                                        "{\"customer\":{\"name\":\"Acme\"},\"document\":{\"number\":\"42\"}}"))
                        .html();

        assertThat(html).contains("<p>Acme / 42</p>");
        assertThat(compiled.templateVersionId()).isEqualTo(version.getId());
    }

    @Test
    void rejectsMissingAndNullValuesButKeepsFalseZeroAndEmptyString() throws Exception {
        TemplateVersion version =
                new TemplateVersion(
                        UUID.randomUUID(),
                        1,
                        "ru-KZ",
                        "{{data.zero}}|{{data.flag}}|{{data.empty}}",
                        null);

        String html =
                renderer.render(
                                version,
                                json.readTree(
                                        "{\"data\":{\"zero\":0,\"flag\":false,\"empty\":\"\"}}"))
                        .html();
        assertThat(html).contains("0|false|");

        assertThatThrownBy(() -> renderer.render(version, json.readTree("{\"data\":{}}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data.zero", "data.flag", "data.empty");

        assertThatThrownBy(
                        () ->
                                renderer.render(
                                        version,
                                        json.readTree(
                                                "{\"data\":{\"zero\":null,\"flag\":false,\"empty\":\"\"}}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data.zero");
    }

    @Test
    void rejectsNonCanonicalAndUnescapedExpressions() throws Exception {
        TemplateVersion uppercase =
                new TemplateVersion(
                        UUID.randomUUID(), 1, "ru-KZ", "<p>{{Customer.Name}}</p>", null);
        assertThatThrownBy(() -> renderer.render(uppercase, json.readTree("{}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical lowercase");

        TemplateVersion unsafe =
                new TemplateVersion(
                        UUID.randomUUID(), 1, "ru-KZ", "<p>{{{customer.html}}}</p>", null);
        assertThatThrownBy(
                        () ->
                                renderer.render(
                                        unsafe,
                                        json.readTree("{\"customer\":{\"html\":\"<b>x</b>\"}}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nested or unescaped");
    }

    @Test
    void removesExecutableHtmlAndRejectsExternalCssResources() throws Exception {
        TemplateVersion unsafeHtml =
                new TemplateVersion(
                        UUID.randomUUID(),
                        1,
                        "ru-KZ",
                        "<p onclick=\"alert(1)\">Hello</p><script>alert(1)</script>",
                        null);
        assertThat(renderer.render(unsafeHtml, json.readTree("{}")).html())
                .contains("<p>Hello</p>")
                .doesNotContain("onclick", "script", "alert(1)");

        TemplateVersion externalCss =
                new TemplateVersion(
                        UUID.randomUUID(),
                        1,
                        "ru-KZ",
                        "<p>Hello</p>",
                        "body { background: url(http://internal/image); }");
        assertThatThrownBy(() -> renderer.render(externalCss, json.readTree("{}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("managed template assets");
    }
}
