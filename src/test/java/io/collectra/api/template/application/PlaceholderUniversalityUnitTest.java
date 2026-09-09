package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.collectra.api.template.domain.TemplateVersion;

import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class PlaceholderUniversalityUnitTest {
    private final ObjectMapper json = new ObjectMapper();
    private final TemplateCompiler compiler =
            new TemplateCompiler(new HtmlTemplatePolicy(), new PlaceholderScanner());
    private final TemplateRenderer renderer = new TemplateRenderer(compiler);

    @Test
    void everyGeneratedCanonicalNestedKeyRoundTripsThroughCompileAndRender() {
        IntStream.range(0, 500)
                .forEach(
                        index -> {
                            String namespace = "ns" + index;
                            String field = "field_" + index;
                            String key = "custom." + namespace + "." + field;
                            String value = "value<&>" + index;

                            ObjectNode payload = json.createObjectNode();
                            payload.with("custom").with(namespace).put(field, value);
                            TemplateVersion version =
                                    new TemplateVersion(
                                            UUID.randomUUID(),
                                            1,
                                            "ru-kz",
                                            "<span>{{" + key + "}}</span>",
                                            null);

                            CompiledTemplate compiled = compiler.compile(version);
                            String html = renderer.render(compiled, payload).html();

                            assertThat(compiled.tokens())
                                    .anyMatch(
                                            token ->
                                                    token instanceof TemplateToken.Placeholder p
                                                            && p.path().canonical().equals(key));
                            assertThat(html)
                                    .contains(
                                            "value&lt;&amp;&gt;" + index);
                        });
    }
}
