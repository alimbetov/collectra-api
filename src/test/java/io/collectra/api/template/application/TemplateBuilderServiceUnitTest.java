package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final TemplateBuilderDocumentCompiler documentCompiler =
            new TemplateBuilderDocumentCompiler();
    private final ObjectMapper json = new ObjectMapper();
    private TemplateBuilderService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        service =
                new TemplateBuilderService(
                        fields, assets, compiler, renderer, documentCompiler);
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
    void validatesStructuredBuilderDocumentThroughSamePlaceholderCatalog() throws Exception {
        when(fields.catalog(tenantId))
                .thenReturn(
                        List.of(
                                field("recipient.name"),
                                field("items.line_no"),
                                field("items.name"),
                                field("items.amount")));

        var builderJson =
                json.readTree(
                        """
                        {
                          "version":"1.0",
                          "blocks":[
                            {
                              "id":"greeting",
                              "type":"richText",
                              "props":{"content":[
                                {"type":"text","value":"Здравствуйте, "},
                                {"type":"placeholder","key":"recipient.name"}
                              ]}
                            },
                            {
                              "id":"items",
                              "type":"itemsTable",
                              "props":{"dataSource":"items","columns":[
                                {"key":"line_no","label":"#"},
                                {"key":"name","label":"Наименование"},
                                {"key":"amount","label":"Сумма"}
                              ]}
                            }
                          ]
                        }
                        """);

        var draft =
                new TemplateBuilderService.BuilderDocumentDraft(
                        TemplateChannel.PDF, "ru", null, builderJson, null);
        var result = service.validateDocument(tenantId, draft);
        var compiled = service.compileDocument(tenantId, draft);

        assertThat(result.valid()).isTrue();
        assertThat(compiled.contentHtml())
                .contains("{{recipient.name}}")
                .contains("{{#each items}}")
                .contains("{{item.line_no}}")
                .contains("{{item.name}}")
                .contains("{{item.amount}}")
                .contains("{{/each}}");
    }

    @Test
    void rejectsUnknownStructuredItemColumn() throws Exception {
        when(fields.catalog(tenantId)).thenReturn(List.of(field("items.name")));
        var builderJson =
                json.readTree(
                        """
                        {"version":"1.0","blocks":[
                          {"type":"itemsTable","props":{"dataSource":"items","columns":[
                            {"key":"unknown","label":"Unknown"}
                          ]}}
                        ]}
                        """);

        var result =
                service.validateDocument(
                        tenantId,
                        new TemplateBuilderService.BuilderDocumentDraft(
                                TemplateChannel.PDF, "ru", null, builderJson, null));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
                .anyMatch(issue -> "UNKNOWN_PLACEHOLDER".equals(issue.code()));
    }

    @Test
    void rejectsUnsupportedBuilderSchemaVersion() throws Exception {
        var builderJson = json.readTree("{\"version\":\"99.0\",\"blocks\":[]}");

        var result =
                service.validateDocument(
                        tenantId,
                        new TemplateBuilderService.BuilderDocumentDraft(
                                TemplateChannel.PDF, "ru", null, builderJson, null));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
                .anyMatch(issue -> "INVALID_BUILDER_JSON".equals(issue.code()));
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
        assertThat(result.errors()).anyMatch(issue -> "UNKNOWN_ASSET".equals(issue.code()));
    }

    private FieldDefinition field(String key) {
        FieldDefinition field = mock(FieldDefinition.class);
        when(field.getKey()).thenReturn(key);
        return field;
    }
}
