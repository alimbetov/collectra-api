package io.collectra.api.template.preset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.template.application.PlaceholderScanner;
import io.collectra.api.template.domain.TemplateChannel;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplatePresetCatalogUnitTest {
    private final PlaceholderScanner scanner = new PlaceholderScanner();

    @Test
    void catalogContainsAllTwelvePresetsWithCompleteBaseLocales() {
        TemplatePresetCatalog catalog = new TemplatePresetCatalog(new ObjectMapper());
        catalog.load();

        assertThat(catalog.list()).hasSize(12);
        assertThat(catalog.list())
                .extracting(TemplatePreset::code)
                .containsExactly(TemplatePresetCode.values());

        for (TemplatePreset preset : catalog.list()) {
            assertThat(preset.translations()).containsKeys("en", "ru", "kk");
            assertThat(preset.supportedChannels()).isNotEmpty();
            for (String locale : List.of("en", "ru", "kk")) {
                TemplatePresetTranslation translation = preset.translations().get(locale);
                assertThat(translation.subject()).isNotBlank();
                assertThat(translation.text()).isNotBlank();
                assertThat(translation.html()).isNotBlank();
                assertThatCode(() -> scanner.scan(translation.subject())).doesNotThrowAnyException();
                assertThatCode(() -> scanner.scan(translation.text())).doesNotThrowAnyException();
                assertThatCode(() -> scanner.scan(translation.html())).doesNotThrowAnyException();
            }
        }
    }

    @Test
    void emailAndPdfUseHtmlWhileMessageChannelsUseText() {
        TemplatePresetCatalog catalog = new TemplatePresetCatalog(new ObjectMapper());
        catalog.load();
        TemplatePreset preset = catalog.require(TemplatePresetCode.PAYMENT_OVERDUE);

        assertThat(preset.supportedChannels())
                .contains(TemplateChannel.EMAIL, TemplateChannel.SMS, TemplateChannel.WHATSAPP,
                        TemplateChannel.TELEGRAM, TemplateChannel.PDF);
    }
}
