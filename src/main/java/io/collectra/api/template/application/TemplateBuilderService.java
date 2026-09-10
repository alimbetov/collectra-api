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

    public TemplateBuilderService(
            FieldCatalogService fieldCatalog,
            TemplateAssetService assets,
            TemplateCompiler compiler,
            TemplateRenderer renderer) {
        this.fieldCatalog = fieldCatalog;
        this.assets = assets;
        this.compiler = compiler;
        this.renderer = renderer;
    }

    public BuilderCatalog catalog(UUID tenantId) {
        return new BuilderCatalog(
                fieldCatalog.catalog(tenantId),
                assets.list(tenantId),
                List.of(TemplateChannel.values()),
                "{{#each items}}...{{item.field}}...{{/each}}",
                "{{asset.company_logo}}");
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

        TemplateChannel channel = draft.channel() == null ? TemplateChannel.PDF : draft.channel();
        if (channel == TemplateChannel.EMAIL && (draft.subject() == null || draft.subject().isBlank())) {
            errors.add(new ValidationIssue("SUBJECT_REQUIRED", "subject", "Email subject is required"));
        }
        if (isTextChannel(channel)) {
            if (draft.stylesheet() != null && !draft.stylesheet().isBlank()) {
                errors.add(
                        new ValidationIssue(
                                "STYLESHEET_NOT_SUPPORTED",
                                "stylesheet",
                                "Stylesheet is supported only for EMAIL and PDF templates"));
            }
            if (draft.content() != null && draft.content().matches("(?s).*<[^>]+>.*")) {
                errors.add(
                        new ValidationIssue(
                                "HTML_NOT_SUPPORTED",
                                "content",
                                "HTML markup is not supported for SMS, WhatsApp or Telegram text variants"));
            }
        }

        Set<String> available =
                fieldCatalog.catalog(tenantId).stream()
                        .map(FieldDefinition::getKey)
                        .map(value -> value.toLowerCase(Locale.ROOT))
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        CompiledTemplate body = null;
        try {
            body =
                    isTextChannel(channel)
                            ? compiler.compileText(UUID.randomUUID(), draft.content())
                            : compiler.compileBody(UUID.randomUUID(), draft.content(), draft.stylesheet());
        } catch (IllegalArgumentException ex) {
            errors.add(new ValidationIssue("INVALID_TEMPLATE", "content", ex.getMessage()));
        }
        if (body != null) validateTokens(tenantId, "content", body.tokens(), available, errors);

        if (channel == TemplateChannel.EMAIL && draft.subject() != null && !draft.subject().isBlank()) {
            try {
                CompiledTemplate subject = compiler.compileText(UUID.randomUUID(), draft.subject());
                validateTokens(tenantId, "subject", subject.tokens(), available, errors);
            } catch (IllegalArgumentException ex) {
                errors.add(new ValidationIssue("INVALID_TEMPLATE", "subject", ex.getMessage()));
            }
        }

        if (body != null && body.tokens().stream().noneMatch(t -> t instanceof TemplateToken.Placeholder)) {
            warnings.add(
                    new ValidationIssue(
                            "NO_DYNAMIC_FIELDS", "content", "Template does not contain dynamic placeholders"));
        }
        return new ValidationResult(errors.isEmpty(), List.copyOf(errors), List.copyOf(warnings));
    }

    public PreviewResult preview(UUID tenantId, BuilderDraft draft, JsonNode payload) {
        ValidationResult validation = validate(tenantId, draft);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Builder draft is invalid: " + validation.errors());
        }
        TemplateChannel channel = draft.channel() == null ? TemplateChannel.PDF : draft.channel();
        JsonNode effectivePayload = assets.enrichPayload(tenantId, payload);
        UUID previewId = UUID.randomUUID();
        CompiledTemplate body =
                isTextChannel(channel)
                        ? compiler.compileText(previewId, draft.content())
                        : compiler.compileBody(previewId, draft.content(), draft.stylesheet());
        String content =
                isTextChannel(channel)
                        ? renderer.renderText(body, effectivePayload)
                        : renderer.render(body, effectivePayload).html();
        String subject =
                channel == TemplateChannel.EMAIL
                        ? renderer.renderText(
                                compiler.compileText(previewId, draft.subject()), effectivePayload)
                        : null;
        return new PreviewResult(channel, subject, content);
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

    private boolean isTextChannel(TemplateChannel channel) {
        return channel == TemplateChannel.SMS
                || channel == TemplateChannel.WHATSAPP
                || channel == TemplateChannel.TELEGRAM;
    }

    public record BuilderDraft(
            TemplateChannel channel, String locale, String subject, String content, String stylesheet) {}

    public record ValidationIssue(String code, String path, String message) {}

    public record ValidationResult(
            boolean valid, List<ValidationIssue> errors, List<ValidationIssue> warnings) {}

    public record PreviewResult(TemplateChannel channel, String subject, String content) {}

    public record BuilderCatalog(
            List<FieldDefinition> fields,
            List<TemplateAsset> assets,
            List<TemplateChannel> channels,
            String eachSyntax,
            String assetSyntax) {}
}
