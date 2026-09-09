package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class PlaceholderScannerUnitTest {
    private final PlaceholderScanner scanner = new PlaceholderScanner();

    @Test
    void tokenizesTextAndPlaceholdersInOriginalOrder() {
        List<TemplateToken> tokens =
                scanner.scan("Hello {{customer.name}}, invoice {{document.number}}.");

        assertThat(tokens)
                .containsExactly(
                        new TemplateToken.Text("Hello "),
                        new TemplateToken.Placeholder(
                                new FieldPath(List.of("customer", "name"))),
                        new TemplateToken.Text(", invoice "),
                        new TemplateToken.Placeholder(
                                new FieldPath(List.of("document", "number"))),
                        new TemplateToken.Text("."));
    }

    @Test
    void keepsSingleBracesAsPlainText() {
        assertThat(scanner.scan("body { color: red; }"))
                .containsExactly(new TemplateToken.Text("body { color: red; }"));
    }

    @Test
    void acceptsWhitespaceAroundCanonicalKey() {
        assertThat(scanner.scan("{{   customer.name   }}"))
                .containsExactly(
                        new TemplateToken.Placeholder(
                                new FieldPath(List.of("customer", "name"))));
    }

    @Test
    void rejectsNestedTripleAndUnclosedExpressionsWithPosition() {
        assertThatThrownBy(() -> scanner.scan("x {{{customer.name}}} y"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nested or unescaped", "position");

        assertThatThrownBy(() -> scanner.scan("x {{customer.name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unclosed placeholder", "position");
    }

    @Test
    void rejectsEmptyDoubleDotAndUppercaseExpressions() {
        assertThatThrownBy(() -> scanner.scan("{{ }}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");

        assertThatThrownBy(() -> scanner.scan("{{customer..name}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid placeholder");

        assertThatThrownBy(() -> scanner.scan("{{Customer.Name}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical lowercase");
    }
}
