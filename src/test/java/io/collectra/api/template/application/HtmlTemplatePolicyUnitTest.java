package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HtmlTemplatePolicyUnitTest {

    private final HtmlTemplatePolicy policy = new HtmlTemplatePolicy();

    @ParameterizedTest
    @ValueSource(strings = {
        "<script>alert(1)</script><p>safe</p>",
        "<iframe src='https://evil.example'></iframe><p>safe</p>",
        "<object data='https://evil.example'></object><p>safe</p>",
        "<embed src='https://evil.example'><p>safe</p>"
    })
    void removesDangerousElements(String html) {
        String sanitized = policy.sanitize(html);

        assertThat(sanitized).contains("safe");
        assertThat(sanitized)
                .doesNotContainIgnoringCase("<script")
                .doesNotContainIgnoringCase("<iframe")
                .doesNotContainIgnoringCase("<object")
                .doesNotContainIgnoringCase("<embed");
    }

    @Test
    void removesInlineEventHandlersAndJavascriptLinks() {
        String sanitized = policy.sanitize(
                "<div onclick='alert(1)'>safe</div><a href='javascript:alert(1)'>link</a>");

        assertThat(sanitized)
                .contains("safe", "link")
                .doesNotContainIgnoringCase("onclick")
                .doesNotContainIgnoringCase("javascript:");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "<img src='http://evil.example/logo.png'>",
        "<img src='https://evil.example/logo.png'>",
        "<img src='javascript:alert(1)'>",
        "<img src='file:///etc/passwd'>",
        "<img src='data:image/svg+xml;base64,PHN2Zz48L3N2Zz4='>",
        "<img src='data:text/html;base64,PGgxPmJvb208L2gxPg=='>"
    })
    void rejectsImagesThatAreNotManagedOrApprovedInlineTypes(String html) {
        assertThatThrownBy(() -> policy.sanitize(html))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("External image URLs are forbidden");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "data:image/png;base64,iVBORw0KGgo=",
        "data:image/jpeg;base64,/9j/4AAQSkZJRg=",
        "data:image/gif;base64,R0lGODlhAQABAAAAACw="
    })
    void allowsApprovedInlineImageTypes(String source) {
        String sanitized = policy.sanitize("<img src='" + source + "'>");

        assertThat(sanitized).contains(source);
    }

    @Test
    void preservesManagedAssetPlaceholder() {
        String sanitized = policy.sanitize("<img src='{{ asset.logo }}' alt='Logo'>");

        assertThat(sanitized).contains("{{ asset.logo }}");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "body { background: url(https://evil.example/x.png); }",
        "body { background: URL(https://evil.example/x.png); }",
        "@import 'https://evil.example/x.css';",
        "@IMPORT 'https://evil.example/x.css';"
    })
    void rejectsStylesheetsThatLoadExternalResources(String stylesheet) {
        assertThatThrownBy(() -> policy.validateStylesheet(stylesheet))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot load external resources");
    }

    @Test
    void allowsStylesheetWithoutExternalResources() {
        policy.validateStylesheet("body { font-family: sans-serif; margin: 1rem; }");
    }
}
