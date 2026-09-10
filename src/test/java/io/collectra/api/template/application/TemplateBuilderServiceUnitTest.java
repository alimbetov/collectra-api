package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.collectra.api.template.domain.FieldDefinition;
import io.collectra.api.template.domain.TemplateChannel;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TemplateBuilderServiceUnitTest {
    private final FieldCatalogService fields = mock(FieldCatalogService.class);
    private final TemplateAssetService assets = mock(TemplateAssetService.class);
    private final HtmlTemplatePolicy policy = new HtmlTemplatePolicy();
    private final TemplateCompiler compiler = new TemplateCompiler(policy, new PlaceholderScanner());
    private final TemplateRenderer renderer = new TemplateRenderer(compiler);
    private TemplateBuilderService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        service = new TemplateBuilderService(fields, assets, compiler, renderer);
        tenantId = UUID.randomUUID();
    }

    @Test
    void acceptsEmailTemplateWithRecipientAndOrderedItemFields() {
        when(fields.catalog(tenantId))
                .thenReturn(
                        List.of(
                                field("recipient.name"),
                                field("invoice.number"),
                                field("items.line_no"),
                                field("items.name"),
                                field("items.amount")));

        var result =
                service.validate(
                        tenantId,
                        new TemplateBuilderService.BuilderDraft(
                                TemplateChannel.EMAIL,
                                "ru",
                                "Счет {{invoice.number}}",
                                "<p>{{recipient.name}}</p>{{#each items}}<p>{{item.line_no}} {{item.name}} {{item.amount}}</p>{{/each}}",
                                null));

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void rejectsUnknownItemFieldAndItemPlaceholderOutsideBlock() {
        when(fields.catalog(tenantId)).thenReturn(List.of(field("items.name")));

        var result =
                service.validate(
                        tenantId,
                        new TemplateBuilderService.BuilderDraft(
                                TemplateChannel.PDF,
                                "ru",
                                null,
                                "{{item.name}}{{#each items}}{{item.unknown}}{{/each}}",
                                null));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
                .extracting(TemplateBuilderService.ValidationIssue::code)
                .contains("ITEM_PLACEHOLDER_OUTSIDE_BLOCK", "UNKNOWN_PLACEHOLDER");
    }

    @Test
    void rejectsHtmlAndStylesheetForTextChannels() {
        when(fields.catalog(tenantId)).thenReturn(List.of(field("recipient.name")));

        var result =
                service.validate(
                        tenantId,
                        new TemplateBuilderService.BuilderDraft(
                                TemplateChannel.WHATSAPP,
                                "ru",
                                null,
                                "<b>{{recipient.name}}</b>",
                                "b { font-weight: bold; }"));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
                .extracting(TemplateBuilderService.ValidationIssue::code)
                .contains("HTML_NOT_SUPPORTED", "STYLESHEET_NOT_SUPPORTED");
    }

    @Test
    void validatesManagedAssetExists() {
        when(fields.catalog(tenantId)).thenReturn(List.of());
        when(assets.exists(tenantId, "company_logo")).thenReturn(false);

        var result =
                service.validate(
                        tenantId,
                        new TemplateBuilderService.BuilderDraft(
                                TemplateChannel.PDF,
                                "ru",
                                null,
                                "<img src=\"{{asset.company_logo}}\">",
                                null));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
                .anyMatch(issue -> "UNKNOWN_ASSET".equals(issue.code()));
    }

    private FieldDefinition field(String key) {
        FieldDefinition field = mock(FieldDefinition.class);
        when(field.getKey()).thenReturn(key);
        return field;
    }
}
