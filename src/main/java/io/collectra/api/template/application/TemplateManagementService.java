package io.collectra.api.template.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.importing.application.SourceSchemaManagementService;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.domain.TemplateVersionStatus;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.FieldDefinitionRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateManagementService {
    private final DocumentTemplateRepository templates;
    private final TemplateVersionRepository versions;
    private final FieldDefinitionRepository fields;
    private final HtmlTemplatePolicy policy;
    private final TemplateCompiler compiler;
    private final TemplateRenderer renderer;

    public TemplateManagementService(
            DocumentTemplateRepository templates,
            TemplateVersionRepository versions,
            FieldDefinitionRepository fields,
            HtmlTemplatePolicy policy,
            TemplateCompiler compiler,
            TemplateRenderer renderer) {
        this.templates = templates;
        this.versions = versions;
        this.fields = fields;
        this.policy = policy;
        this.compiler = compiler;
        this.renderer = renderer;
    }

    @Transactional
    public DocumentTemplate create(UUID tenantId, String code, String name, String documentType) {
        String normalized = normalizeCode(code);
        if (templates.findAllByTenantIdOrderByNameAsc(tenantId).stream()
                .anyMatch(t -> t.getCode().equalsIgnoreCase(normalized))) {
            throw new IllegalArgumentException("Template code already exists");
        }
        return templates.save(
                new DocumentTemplate(tenantId, normalized, name.trim(), normalizeCode(documentType)));
    }

    @Transactional(readOnly = true)
    public List<DocumentTemplate> list(UUID tenantId) {
        return templates.findAllByTenantIdOrderByNameAsc(tenantId);
    }

    @Transactional
    public DocumentTemplate rename(UUID tenantId, UUID id, String name) {
        var template = requireTemplate(tenantId, id);
        template.rename(name.trim());
        return template;
    }

    @Transactional
    public void archiveTemplate(UUID tenantId, UUID id) {
        requireTemplate(tenantId, id).archive();
    }

    @Transactional
    public TemplateVersion createVersion(
            UUID tenantId,
            UUID templateId,
            String locale,
            String contentHtml,
            String stylesheet) {
        return createVersion(
                tenantId,
                templateId,
                locale,
                TemplateChannel.PDF,
                null,
                contentHtml,
                stylesheet);
    }

    @Transactional
    public TemplateVersion createVersion(
            UUID tenantId,
            UUID templateId,
            String locale,
            TemplateChannel channel,
            String subject,
            String contentHtml,
            String stylesheet) {
        requireActiveTemplate(tenantId, templateId);
        String normalizedLocale = locale.trim().toLowerCase(Locale.ROOT);
        TemplateChannel normalizedChannel = channel == null ? TemplateChannel.PDF : channel;
        var existing =
                versions.findAllByTemplateIdAndLocaleAndChannelOrderByTemplateVersionDesc(
                        templateId, normalizedLocale, normalizedChannel);
        int number = existing.isEmpty() ? 1 : existing.get(0).getTemplateVersion() + 1;
        if ((contentHtml == null || contentHtml.isBlank()) && !existing.isEmpty()) {
            TemplateVersion latest = existing.get(0);
            contentHtml = latest.getContentHtml();
            stylesheet = latest.getStylesheet();
            if (subject == null) subject = latest.getSubject();
        }
        if (contentHtml == null || contentHtml.isBlank()) {
            throw new IllegalArgumentException("Template content is required");
        }
        return versions.save(
                new TemplateVersion(
                        templateId,
                        number,
                        normalizedLocale,
                        normalizedChannel,
                        subject,
                        contentHtml,
                        stylesheet));
    }

    @Transactional(readOnly = true)
    public List<TemplateVersion> versions(UUID tenantId, UUID templateId) {
        requireTemplate(tenantId, templateId);
        return versions.findAllByTemplateIdOrderByTemplateVersionDesc(templateId);
    }

    @Transactional
    public TemplateVersion update(UUID tenantId, UUID versionId, String html, String css) {
        var version = requireVersion(tenantId, versionId);
        version.update(html, css);
        return version;
    }

    @Transactional
    public TemplateVersion update(
            UUID tenantId, UUID versionId, String subject, String content, String css) {
        var version = requireVersion(tenantId, versionId);
        version.update(subject, content, css);
        return version;
    }

    @Transactional
    public SourceSchemaManagementService.ValidationResult validate(UUID tenantId, UUID versionId) {
        var version = requireVersion(tenantId, versionId);
        if (version.getStatus() != TemplateVersionStatus.DRAFT) {
            throw new IllegalArgumentException("Only draft template can be validated");
        }

        List<SourceSchemaManagementService.ValidationIssue> errors = new ArrayList<>();
        Set<String> available =
                fields.findAvailable(tenantId).stream()
                        .map(f -> f.getKey().toLowerCase(Locale.ROOT))
                        .collect(java.util.stream.Collectors.toSet());

        validateCompiled("contentHtml", compileBody(version, errors), available, errors);
        if (version.getChannel() == TemplateChannel.EMAIL) {
            validateCompiled(
                    "subject",
                    compileText(version.getId(), version.getSubject(), "subject", errors),
                    available,
                    errors);
        }

        if (errors.isEmpty()) version.validated();
        return new SourceSchemaManagementService.ValidationResult(
                errors.isEmpty(), errors, List.of());
    }

    private CompiledTemplate compileBody(
            TemplateVersion version, List<SourceSchemaManagementService.ValidationIssue> errors) {
        try {
            return compiler.compile(version);
        } catch (IllegalArgumentException ex) {
            errors.add(issue("INVALID_TEMPLATE", "contentHtml", ex.getMessage()));
            return null;
        }
    }

    private CompiledTemplate compileText(
            UUID versionId,
            String text,
            String path,
            List<SourceSchemaManagementService.ValidationIssue> errors) {
        try {
            return compiler.compileText(versionId, text);
        } catch (IllegalArgumentException ex) {
            errors.add(issue("INVALID_TEMPLATE", path, ex.getMessage()));
            return null;
        }
    }

    private void validateCompiled(
            String sourcePath,
            CompiledTemplate compiled,
            Set<String> available,
            List<SourceSchemaManagementService.ValidationIssue> errors) {
        if (compiled == null) return;
        boolean insideItems = false;
        for (TemplateToken token : compiled.tokens()) {
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
            String catalogKey = key;
            if (key.startsWith("item.")) {
                if (!insideItems) {
                    errors.add(
                            issue(
                                    "ITEM_PLACEHOLDER_OUTSIDE_BLOCK",
                                    sourcePath,
                                    "Item placeholder must be inside {{#each items}}: " + key));
                    continue;
                }
                catalogKey = "items." + key.substring("item.".length());
            } else if (key.startsWith("items.")) {
                errors.add(
                        issue(
                                "COLLECTION_PLACEHOLDER_REQUIRES_BLOCK",
                                sourcePath,
                                "Use {{item.*}} inside {{#each items}} instead of: " + key));
                continue;
            }

            if (!catalogKey.startsWith("asset.") && !available.contains(catalogKey)) {
                errors.add(
                        issue(
                                "UNKNOWN_PLACEHOLDER",
                                sourcePath,
                                "Unknown placeholder: " + key));
            }
        }
    }

    @Transactional(readOnly = true)
    public TemplateRenderer.RenderResult preview(UUID tenantId, UUID versionId, JsonNode payload) {
        return renderer.render(requireVersion(tenantId, versionId), payload);
    }

    @Transactional(readOnly = true)
    public TemplateVersion getVersion(UUID tenantId, UUID versionId) {
        return requireVersion(tenantId, versionId);
    }

    @Transactional
    public TemplateVersion publish(UUID tenantId, UUID id) {
        var version = requireVersion(tenantId, id);
        version.publish();
        return version;
    }

    @Transactional
    public TemplateVersion reopen(UUID tenantId, UUID id) {
        var version = requireVersion(tenantId, id);
        version.reopen();
        return version;
    }

    @Transactional
    public TemplateVersion archive(UUID tenantId, UUID id) {
        var version = requireVersion(tenantId, id);
        version.archive();
        return version;
    }

    private DocumentTemplate requireTemplate(UUID tenantId, UUID id) {
        return templates.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Template not found"));
    }

    private DocumentTemplate requireActiveTemplate(UUID tenantId, UUID id) {
        var template = requireTemplate(tenantId, id);
        if (!"ACTIVE".equals(template.getStatus())) {
            throw new IllegalArgumentException("Template is archived");
        }
        return template;
    }

    private TemplateVersion requireVersion(UUID tenantId, UUID id) {
        return versions.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Template version not found"));
    }

    private String normalizeCode(String code) {
        String value = code.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z][A-Z0-9_]{1,99}")) {
            throw new IllegalArgumentException("Invalid code");
        }
        return value;
    }

    private SourceSchemaManagementService.ValidationIssue issue(
            String code, String path, String message) {
        return new SourceSchemaManagementService.ValidationIssue(code, path, message);
    }
}
