package io.collectra.api.localization.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.collectra.api.localization.domain.FontProfileCode;
import org.junit.jupiter.api.Test;

class FontProfileRegistryUnitTest {
    private final FontProfileRegistry registry = new FontProfileRegistry();

    @Test
    void allRequiredFontsAreBundledInClasspath() {
        registry.verifyBundledResources();
    }

    @Test
    void providesExpectedFallbackStacks() {
        assertThat(registry.cssStack(FontProfileCode.LATIN_CYRILLIC))
                .isEqualTo("\"Noto Sans\", sans-serif");
        assertThat(registry.cssStack(FontProfileCode.ARMENIAN))
                .contains("\"Noto Sans Armenian\"", "\"Noto Sans\"");
        assertThat(registry.cssStack(FontProfileCode.GEORGIAN))
                .contains("\"Noto Sans Georgian\"", "\"Noto Sans\"");
        assertThat(registry.cssStack(FontProfileCode.CJK_SC))
                .contains("\"Noto Sans SC\"", "\"Noto Sans\"");
    }
}
