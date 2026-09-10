package io.collectra.api.template.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.template.domain.FieldDefinition;
import io.collectra.api.template.domain.TemplateAsset;
import io.collectra.api.template.domain.TemplateChannel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TemplateBuilderService {
    private final FieldCatalogService fieldCatalog;
    private final TemplateAssetService assets;
    private final TemplateCompiler compiler;
    private final TemplateRenderer renderer;
    private final TemplateBuilderDocumentCompiler documentCompiler;

    public TemplateBuilderService(
            FieldCatalogService fieldCatalog,
            TemplateAssetService assets,
            TemplateCompiler compiler,
            TemplateRenderer renderer,
            TemplateBuilderDocumentCompiler documentCompiler) {
        this.fieldCatalog = fieldCatalog;
        this.assets = assets;
        this.compiler = compiler;
        this.renderer = renderer;
        this.documentCompiler = documentCompiler;
    }

    public BuilderCatalog catalog(UUID tenantId) {
        return new BuilderCatalog(
                fieldCatalog.catalog(tenantId),
                assets.list(tenantId),
                List.of(TemplateChannel.values()),
                "{{#each items}}...{{item.field}}...{{/each}}",
                "{{asset.company_logo}}",
                "1.0");
    }

    public ValidationResult validate(UUID tenantId, BuilderDraft draft) {
        List<ValidationIssue> errors = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>();
        if (draft == null) {
            return new ValidationResult(
                    false,
                    List.of(new ValidationIssue("DRAFT_REQUIRED", "draft", "Builder draft is required")),
                    List.of());
        }

        TemplateChannel channel = effectiveChannel(draft.channel());
        validateChannelRules(channel, draft.subject(), draft.content(), draft.stylesheet(), errors);

        Set<String> available = availableFields(tenantId);
        CompiledTemplate body = compileBody(channel, draft.content(), draft.stylesheet(), errors);
        if (body != null) validateTokens(tenantId, "content", body.tokens(), available, errors);

        validateSubject(tenantId, channel, draft.subject(), available, errors);

        if (body != null && body.tokens().stream().noneMatch(this::isDynamicToken)) {
            warnings.add(
                    new ValidationIssue(
                            "NO_DYNAMIC_FIELDS", "content", "Template does not contain dynamic placeholders"));
        }
        return new ValidationResult(errors.isEmpty(), List.copyOf(errors), List.copyOf(warnings));
    }

    public ValidationResult validateDocument(UUID tenantId, BuilderDocumentDraft draft) {
        if (draft == null) {
            return new ValidationResult(
                    false,
                    List.of(new ValidationIssue("DRAFT_REQUIRED", "draft", "Builder draft is required")),
                    List.of());
        }
        String content;
        try {
            content = documentCompiler.compile(draft.builderJson(), effectiveChannel(draft.channel()));
        } catch (IllegalArgumentException ex) {
            return new ValidationResult(
                    false,
                    List.of(new ValidationIssue("INVALID_BUILDER_JSON", "builderJson", ex.getMessage())),
                    List.of());
        }
        return validate(
                tenantId,
                new BuilderDraft(
                        draft.channel(), draft.locale(), draft.subject(), content, draft.stylesheet()));
    }

    public PreviewResult preview(UUID tenantId, BuilderDraft draft, JsonNode payload) {
        ValidationResult validation = validate(tenantId, draft);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Builder draft is invalid: " + validation.errors());
        }
        return renderPreview(
                tenantId,
                draft.channel(),
                draft.subject(),
                draft.content(),
                draft.stylesheet(),
                payload);
    }

    public PreviewResult previewDocument(
            UUID tenantId, BuilderDocumentDraft draft, JsonNode payload) {
        ValidationResult validation = validateDocument(tenantId, draft);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Builder document is invalid: " + validation.errors());
        }
        TemplateChannel channel = effectiveChannel(draft.channel());
        String content = documentCompiler.compile(draft.builderJson(), channel);
        return renderPreview(
                tenantId,
                channel,
                draft.subject(),
                content,
                draft.stylesheet(),
                payload);
    }

    public CompiledBuilderDocument compileDocument(UUID tenantId, BuilderDocumentDraft draft) {
        ValidationResult validation = validateDocument(tenantId, draft);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Builder document is invalid: " + validation.errors());
        }
        TemplateChannel channel = effectiveChannel(draft.channel());
        return new CompiledBuilderDocument(
                draft.builderJson().deepCopy(), documentCompiler.compile(draft.builderJson(), channel));
    }

    private PreviewResult renderPreview(
            UUID tenantId,
            TemplateChannel requestedChannel,
            String subjectTemplate,
            String contentTemplate,
            String stylesheet,
            JsonNode payload) {
        TemplateChannel channel = effectiveChannel(requestedChannel);
        JsonNode effectivePayload = assets.enrichPayload(tenantId, payload);
        UUID previewId = UUID.randomUUID();
        CompiledTemplate body =
                isTextChannel(channel)
                        ? compiler.compileText(previewId, contentTemplate)
                        : compiler.compileBody(previewId, contentTemplate, stylesheet);
        String content =
                isTextChannel(channel)
                        ? renderer.renderText(body, effectivePayload)
                        : renderer.render(body, effectivePayload).html();
        String subject =
                channel == TemplateChannel.EMAIL
                        ? renderer.renderText(
                                compiler.compileText(previewId, subjectTemplate), effectivePayload)
                        : null;
        return new PreviewResult(channel, subject, content);
    }

    private void validateChannelRules(
            TemplateChannel channel,
            String subject,
            String content,
            String stylesheet,
            List<ValidationIssue> errors) {
        if (channel == TemplateChannel.EMAIL && (subject == null || subject.isBlank())) {
            errors.add(new ValidationIssue("SUBJECT_REQUIRED", "subject", "Email subject is required"));
        }
        if (content == null || content.isBlank()) {
            errors.add(new ValidationIssue("CONTENT_REQUIRED", "content", "Template content is required"));
            return;
        }
        if (isTextChannel(channel)) {
            if (stylesheet != null && !stylesheet.isBlank()) {
                errors.add(
                        new ValidationIssue(
                                "STYLESHEET_NOT_SUPPORTED",
                                "stylesheet",
                                "Stylesheet is supported only for EMAIL and PDF templates"));
            }
            if (content.matches("(?s).*<[^>]+>.*")) {
                errors.add(
                        new ValidationIssue(
                                "HTML_NOT_SUPPORTED",
                                "content",
                                "HTML markup is not supported for SMS, WhatsApp or Telegram text variants"));
            }
        }
    }

    private CompiledTemplate compileBody(
            TemplateChannel channel,
            String content,
            String stylesheet,
            List<ValidationIssue> errors) {
        if (content == null || content.isBlank()) return null;
        try {
            return isTextChannel(channel)
                    ? compiler.compileText(UUID.randomUUID(), content)
                    : compiler.compileBody(UUID.randomUUID(), content, stylesheet);
        } catch (IllegalArgumentException ex) {
            errors.add(new ValidationIssue("INVALID_TEMPLATE", "content", ex.getMessage()));
            return null;
        }
    }

    private void validateSubject(
            UUID tenantId,
            TemplateChannel channel,
            String subject,
            Set<String> available,
            List<ValidationIssue> errors) {
        if (channel != TemplateChannel.EMAIL || subject == null || subject.isBlank()) return;
        try {
            CompiledTemplate compiled = compiler.compileText(UUID.randomUUID(), subject);
            validateTokens(tenantId, "subject", compiled.tokens(), available, errors);
        } catch (IllegalArgumentException ex) {
            errors.add(new ValidationIssue("INVALID_TEMPLATE", "subject", ex.getMessage()));
        }
    }

    private Set<String> availableFields(UUID tenantId) {
        return fieldCatalog.catalog(tenantId).stream()
                .map(FieldDefinition::getKey)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private void validateTokens(
            UUID tenantId,
            String path,
            List<TemplateToken> tokens,
            Set<String> available,
            List<ValidationIssue> errors) {
        boolean insideItems = false;
        for (TemplateToken token : tokens) {
            if (token instanceof TemplateToken.EachStart start) {
                insideItems = "items".equals(start.collectionKey());
                continue;
            }
            if (token instanceof TemplateToken.EachEnd) {
                insideItems = false;
                continue;
            }
            if (!(token instanceof TemplateToken.Placeholder placeholder)) continue;

            String key = placeholder.path().canonical();
            if (key.startsWith("item.")) {
                if (!insideItems) {
                    errors.add(
                            new ValidationIssue(
                                    "ITEM_PLACEHOLDER_OUTSIDE_BLOCK",
                                    path,
                                    "Item placeholder must be inside {{#each items}}: " + key));
                    continue;
                }
                String catalogKey = "items." + key.substring("item.".length());
                if (!available.contains(catalogKey)) {
                    errors.add(
                            new ValidationIssue(
                                    "UNKNOWN_PLACEHOLDER", path, "Unknown item placeholder: " + key));
                }
                continue;
            }
            if (key.startsWith("items.")) {
                errors.add(
                        new ValidationIssue(
                                "COLLECTION_PLACEHOLDER_REQUIRES_BLOCK",
                                path,
                                "Use {{item.*}} inside {{#each items}} instead of: " + key));
                continue;
            }
            if (key.startsWith("asset.")) {
                String assetKey = key.substring("asset.".length());
                if (!assets.exists(tenantId, assetKey)) {
                    errors.add(
                            new ValidationIssue(
                                    "UNKNOWN_ASSET", path, "Unknown template asset: " + assetKey));
                }
                continue;
            }
            if (!available.contains(key)) {
                errors.add(
                        new ValidationIssue("UNKNOWN_PLACEHOLDER", path, "Unknown placeholder: " + key));
            }
        }
    }

    private TemplateChannel effectiveChannel(TemplateChannel channel) {
        return channel == null ? TemplateChannel.PDF : channel;
    }

    private boolean isDynamicToken(TemplateToken token) {
        return token instanceof TemplateToken.Placeholder || token instanceof TemplateToken.EachStart;
    }

    private boolean isTextChannel(TemplateChannel channel) {
        return channel == TemplateChannel.SMS
                || channel == TemplateChannel.WHATSAPP
                || channel == TemplateChannel.TELEGRAM;
    }

    public record BuilderDraft(
            TemplateChannel channel, String locale, String subject, String content, String stylesheet) {}

    public record BuilderDocumentDraft(
            TemplateChannel channel,
            String locale,
            String subject,
            JsonNode builderJson,
            String stylesheet) {}

    public record CompiledBuilderDocument(JsonNode builderJson, String contentHtml) {}

    public record ValidationIssue(String code, String path, String message) {}

    public record ValidationResult(
            boolean valid, List<ValidationIssue> errors, List<ValidationIssue> warnings) {}

    public record PreviewResult(TemplateChannel channel, String subject, String content) {}

    public record BuilderCatalog(
            List<FieldDefinition> fields,
            List<TemplateAsset> assets,
            List<TemplateChannel> channels,
            String eachSyntax,
            String assetSyntax,
            String builderSchemaVersion) {}
}
