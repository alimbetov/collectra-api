package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TemplateRenderingAssuranceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TemplateCompiler compiler;
    private TemplateRenderer renderer;
    private HtmlTemplatePolicy htmlPolicy;

    @BeforeEach
    void setUp() {
        htmlPolicy = new HtmlTemplatePolicy();
        compiler = new TemplateCompiler(htmlPolicy, new PlaceholderScanner());
        renderer = new TemplateRenderer(compiler);
    }

    @Test
    void rendersCanonicalFieldsAndItemCollectionWithoutUnresolvedExpressions() throws Exception {
        JsonNode payload =
                objectMapper.readTree(
                        """
                        {
                          "customer": {"displayName": "ТОО Астана Трейд"},
                          "invoice": {
                            "invoiceNumber": "INV-2026-001",
                            "outstandingAmount": 125000.50,
                            "currency": "KZT"
                          },
                          "items": [
                            {"name": "Service A", "amount": 100000},
                            {"name": "Service B", "amount": 25000.50}
                          ]
                        }
                        """);

        CompiledTemplate template =
                compiler.compileBody(
                        UUID.randomUUID(),
                        """
                        <h1>Уважаемый {{customer.displayName}}</h1>
                        <p>Счет {{invoice.invoiceNumber}}</p>
                        <p>Сумма: {{invoice.outstandingAmount}} {{invoice.currency}}</p>
                        <table>{{#each items}}<tr><td>{{item.name}}</td><td>{{item.amount}}</td></tr>{{/each}}</table>
                        """,
                        "table { border-collapse: collapse; }");

        String html = renderer.render(template, payload).html();

        assertThat(html)
                .contains("ТОО Астана Трейд")
                .contains("INV-2026-001")
                .contains("125000.5")
                .contains("KZT")
                .contains("Service A")
                .contains("Service B")
                .doesNotContain("{{customer.displayName}}")
                .doesNotContain("{{invoice.invoiceNumber}}")
                .doesNotContain("{{item.name}}")
                .doesNotContain("{{#each");
    }

    @Test
    void escapesPlaceholderValuesInsertedIntoHtml() throws Exception {
        JsonNode payload =
                objectMapper.readTree(
                        "{\"customer\":{\"displayName\":\"<script>alert('x')</script>\"}}");
        CompiledTemplate template =
                compiler.compileBody(UUID.randomUUID(), "<p>{{customer.displayName}}</p>", null);

        String html = renderer.render(template, payload).html();

        assertThat(html)
                .contains("&lt;script&gt;alert('x')&lt;/script&gt;")
                .doesNotContain("<script>alert('x')</script>");
    }

    @Test
    void failsClosedWhenRequiredPlaceholderValueIsMissing() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"customer\":{\"displayName\":\"ACME\"}}");
        CompiledTemplate template =
                compiler.compileBody(
                        UUID.randomUUID(),
                        "<p>{{customer.displayName}} {{invoice.invoiceNumber}}</p>",
                        null);

        assertThatThrownBy(() -> renderer.render(template, payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invoice.invoiceNumber");
    }

    @Test
    void rejectsMalformedAndNestedCollectionGrammar() {
        assertThatThrownBy(
                        () ->
                                compiler.compileBody(
                                        UUID.randomUUID(), "{{customer.displayName", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unclosed placeholder");

        assertThatThrownBy(
                        () ->
                                compiler.compileBody(
                                        UUID.randomUUID(),
                                        "{{#each items}}{{#each items}}{{item.name}}{{/each}}{{/each}}",
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nested each blocks are not supported");

        assertThatThrownBy(() -> compiler.compileBody(UUID.randomUUID(), "{{/each}}", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unexpected {{/each}}");
    }

    @Test
    void rejectsUnsupportedCollectionBlocks() {
        assertThatThrownBy(
                        () ->
                                compiler.compileBody(
                                        UUID.randomUUID(),
                                        "{{#each invoices}}{{item.name}}{{/each}}",
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only {{#each items}} collection block is supported");
    }

    @Test
    void rejectsExternalHtmlAndCssResourcesButAllowsManagedAssets() {
        assertThatThrownBy(
                        () ->
                                htmlPolicy.sanitize(
                                        "<img src=\"https://cdn.example.test/logo.png\">"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("External image URLs are forbidden");

        String managedAsset = htmlPolicy.sanitize("<img src=\"{{asset.logo}}\">");
        assertThat(managedAsset).contains("{{asset.logo}}");

        assertThatThrownBy(
                        () -> htmlPolicy.validateStylesheet("body { background: url(https://x); }"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stylesheet cannot load external resources");

        assertThatThrownBy(() -> htmlPolicy.validateStylesheet("@import 'https://x/style.css';"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stylesheet cannot load external resources");
    }
}
