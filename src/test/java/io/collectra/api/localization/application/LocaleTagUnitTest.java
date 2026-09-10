package io.collectra.api.localization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LocaleTagUnitTest {
    @Test
    void canonicalizesLanguageTagsWithoutDestroyingRegionCase() {
        assertThat(LocaleTag.canonicalize("ru")).isEqualTo("ru");
        assertThat(LocaleTag.canonicalize("kk_KZ")).isEqualTo("kk-KZ");
        assertThat(LocaleTag.canonicalize("ZH-cn")).isEqualTo("zh-CN");
    }

    @Test
    void rejectsMissingOrUndefinedLocale() {
        assertThatThrownBy(() -> LocaleTag.canonicalize(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Locale is required");
        assertThatThrownBy(() -> LocaleTag.canonicalize("---"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid locale");
    }
}
