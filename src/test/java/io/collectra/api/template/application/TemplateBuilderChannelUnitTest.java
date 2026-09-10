package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.template.domain.FieldDataType;
import io.collectra.api.template.domain.FieldDefinition;
import io.collectra.api.template.domain.TemplateChannel;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TemplateBuilderChannelUnitTest {
    private final FieldCatalogService fields = mock(FieldCatalogService.class);
    private final TemplateAssetService assets = mock(TemplateAssetService.class);
    private final TemplateCompiler compiler =
            new TemplateCompiler(new HtmlTemplatePolicy(), new PlaceholderScanner());
    private final TemplateRenderer renderer = new TemplateRenderer(compiler);
    private final TemplateBuilderDocumentCompiler documentCompiler =
            new TemplateBuilderDocumentCompiler();
    private final ObjectMapper json = new ObjectMapper();
    private TemplateBuilderService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        service = new TemplateBuilderService(fields, assets, compiler, renderer, documentCompiler);
    }

    @Test
    void structuredWhatsAppRichTextCompilesAndPreviewsAsPlainText() throws Exception {
        when(fields.catalog(tenantId)).thenReturn(List.of(field("recipient.name")));
        var builderJson =
                json.readTree(
                        """
                        {"version":"1.0","blocks":[
                          {"type":"richText","props":{"content":[
                            {"type":"text","value":"Здравствуйте, "},
                            {"type":"placeholder","key":"recipient.name"},
                            {"type":"text","value":" & добро пожаловать"}
                          ]}}
                        ]}
                        """);
        var draft =
                new TemplateBuilderService.BuilderDocumentDraft(
                        TemplateChannel.WHATSAPP, "ru", null, builderJson, null);
        var payload = json.readTree("{\"recipient\":{\"name\":\"A&B\"}}");
        when(assets.enrichPayload(tenantId, payload)).thenReturn(payload);

        var validation = service.validateDocument(tenantId, draft);
        var preview = service.previewDocument(tenantId, draft, payload);

        assertThat(validation.valid()).isTrue();
        assertThat(preview.channel()).isEqualTo(TemplateChannel.WHATSAPP);
        assertThat(preview.content()).isEqualTo("Здравствуйте, A&B & добро пожаловать");
        assertThat(preview.content()).doesNotContain("<div", "&amp;");
    }

    @Test
    void structuredTextChannelRejectsVisualBlocks() throws Exception {
        var builderJson =
                json.readTree(
                        """
                        {"version":"1.0","blocks":[
                          {"type":"image","props":{"assetKey":"company_logo","alt":"Logo"}}
                        ]}
                        """);

        var result =
                service.validateDocument(
                        tenantId,
                        new TemplateBuilderService.BuilderDocumentDraft(
                                TemplateChannel.SMS, "ru", null, builderJson, null));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
                .anyMatch(
                        issue ->
                                "INVALID_BUILDER_JSON".equals(issue.code())
                                        && issue.message().contains("not supported for text channel SMS"));
    }

    private FieldDefinition field(String key) {
        return new FieldDefinition(
                null,
                key,
                key,
                FieldDataType.STRING,
                "TEST",
                false,
                false,
                json.createObjectNode());
    }
}
