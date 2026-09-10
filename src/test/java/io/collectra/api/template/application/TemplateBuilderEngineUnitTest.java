package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class TemplateBuilderEngineUnitTest {
    private final HtmlTemplatePolicy policy = new HtmlTemplatePolicy();
    private final PlaceholderScanner scanner = new PlaceholderScanner();
    private final TemplateCompiler compiler = new TemplateCompiler(policy, scanner);
    private final TemplateRenderer renderer = new TemplateRenderer(compiler);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void rendersOrderedItemsAsFirstClassRows() throws Exception {
        var compiled =
                compiler.compileBody(
                        UUID.randomUUID(),
                        """
                        <table><tbody>{{#each items}}<tr><td>{{item.line_no}}</td><td>{{item.name}}</td><td>{{item.amount}}</td></tr>{{/each}}</tbody></table>
                        """,
                        null);
        var payload =
                json.readTree(
                        """
                        {"items":[
                          {"line_no":1,"name":"Laptop","amount":100000},
                          {"line_no":2,"name":"Mouse & Cable","amount":20000},
                          {"line_no":3,"name":"Keyboard","amount":5000}
                        ]}
                        """);

        String html = renderer.render(compiled, payload).html();
        var rows = Jsoup.parse(html).select("tbody > tr");

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).select("td")).extracting(element -> element.text())
                .containsExactly("1", "Laptop", "100000");
        assertThat(rows.get(1).select("td")).extracting(element -> element.text())
                .containsExactly("2", "Mouse & Cable", "20000");
        assertThat(rows.get(2).select("td")).extracting(element -> element.text())
                .containsExactly("3", "Keyboard", "5000");
    }

    @Test
    void rejectsItemPlaceholderOutsideItemsBlockAndUnsupportedCollectionsAtScannerLevel() {
        assertThat(scanner.scan("{{item.name}}"))
                .containsExactly(
                        new TemplateToken.Placeholder(
                                new FieldPath(java.util.List.of("item", "name"))));

        assertThatThrownBy(() -> scanner.scan("{{#each payments}}{{item.name}}{{/each}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only {{#each items}}");

        assertThatThrownBy(
                        () ->
                                scanner.scan(
                                        "{{#each items}}{{#each items}}{{item.name}}{{/each}}{{/each}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nested each blocks");
    }

    @Test
    void managedAssetPlaceholderIsAllowedButExternalImageUrlIsRejected() {
        String sanitized = policy.sanitize("<img src=\"{{asset.company_logo}}\" alt=\"logo\">");
        assertThat(sanitized).contains("{{asset.company_logo}}");

        assertThatThrownBy(() -> policy.sanitize("<img src=\"https://example.com/logo.png\">"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("External image URLs are forbidden");
    }

    @Test
    void missingItemsCollectionFailsExplicitly() throws Exception {
        var compiled =
                compiler.compileBody(
                        UUID.randomUUID(),
                        "{{#each items}}<p>{{item.name}}</p>{{/each}}",
                        null);

        assertThatThrownBy(() -> renderer.render(compiled, json.readTree("{}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("items[]");
    }
}
