package io.collectra.api.template.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.importing.application.SourceSchemaManagementService;
import io.collectra.api.template.domain.DocumentTemplate;
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
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateManagementService {
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "\\{\\{\\s*([a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+)\\s*}}", Pattern.CASE_INSENSITIVE);
    private final DocumentTemplateRepository templates;
    private final TemplateVersionRepository versions;
    private final FieldDefinitionRepository fields;
    private final HtmlTemplatePolicy policy;
    private final TemplateRenderer renderer;

    public TemplateManagementService(DocumentTemplateRepository templates,
            TemplateVersionRepository versions, FieldDefinitionRepository fields,
            HtmlTemplatePolicy policy, TemplateRenderer renderer) {
        this.templates = templates;
        this.versions = versions;
        this.fields = fields;
        this.policy = policy;
        this.renderer = renderer;
    }

    @Transactional
    public DocumentTemplate create(UUID tenantId, String code, String name, String documentType) {
        String normalized = normalizeCode(code);
        if (templates.findAllByTenantIdOrderByNameAsc(tenantId).stream()
                .anyMatch(t -> t.getCode().equalsIgnoreCase(normalized)))
            throw new IllegalArgumentException("Template code already exists");
        return templates.save(new DocumentTemplate(tenantId, normalized, name.trim(), normalizeCode(documentType)));
    }

    @Transactional(readOnly = true)
    public List<DocumentTemplate> list(UUID tenantId) { return templates.findAllByTenantIdOrderByNameAsc(tenantId); }

    @Transactional
    public DocumentTemplate rename(UUID tenantId, UUID id, String name) {
        var template = requireTemplate(tenantId, id); template.rename(name.trim()); return template;
    }

    @Transactional
    public void archiveTemplate(UUID tenantId, UUID id) { requireTemplate(tenantId, id).archive(); }

    @Transactional
    public TemplateVersion createVersion(UUID tenantId, UUID templateId, String locale,
            String contentHtml, String stylesheet) {
        requireActiveTemplate(tenantId, templateId);
        String normalizedLocale = locale.trim().toLowerCase(Locale.ROOT);
        var existing = versions.findAllByTemplateIdAndLocaleOrderByTemplateVersionDesc(templateId, normalizedLocale);
        int number = existing.isEmpty() ? 1 : existing.get(0).getTemplateVersion() + 1;
        if ((contentHtml == null || contentHtml.isBlank()) && !existing.isEmpty()) {
            contentHtml = existing.get(0).getContentHtml();
            stylesheet = existing.get(0).getStylesheet();
        }
        if (contentHtml == null || contentHtml.isBlank())
            throw new IllegalArgumentException("Template HTML is required");
        return versions.save(new TemplateVersion(templateId, number, normalizedLocale, contentHtml, stylesheet));
    }

    @Transactional(readOnly = true)
    public List<TemplateVersion> versions(UUID tenantId, UUID templateId) {
        requireTemplate(tenantId, templateId);
        return versions.findAllByTemplateIdOrderByTemplateVersionDesc(templateId);
    }

    @Transactional
    public TemplateVersion update(UUID tenantId, UUID versionId, String html, String css) {
        var version = requireVersion(tenantId, versionId); version.update(html, css); return version;
    }

    @Transactional
    public SourceSchemaManagementService.ValidationResult validate(UUID tenantId, UUID versionId) {
        var version = requireVersion(tenantId, versionId);
        if (version.getStatus() != TemplateVersionStatus.DRAFT)
            throw new IllegalArgumentException("Only draft template can be validated");
        List<SourceSchemaManagementService.ValidationIssue> errors = new ArrayList<>();
        try { policy.sanitize(version.getContentHtml()); policy.validateStylesheet(version.getStylesheet()); }
        catch (IllegalArgumentException ex) { errors.add(issue("UNSAFE_TEMPLATE", "content", ex.getMessage())); }
        Set<String> available = fields.findAvailable(tenantId).stream()
                .map(f -> f.getKey().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        var matcher = PLACEHOLDER.matcher(version.getContentHtml());
        while (matcher.find()) if (!available.contains(matcher.group(1).toLowerCase(Locale.ROOT)))
            errors.add(issue("UNKNOWN_PLACEHOLDER", "contentHtml", "Unknown placeholder: " + matcher.group(1)));
        String unmatched = PLACEHOLDER.matcher(version.getContentHtml()).replaceAll("");
        if (unmatched.contains("{{")) errors.add(issue("UNSUPPORTED_EXPRESSION", "contentHtml",
                "Unsupported template expression"));
        if (errors.isEmpty()) version.validated();
        return new SourceSchemaManagementService.ValidationResult(errors.isEmpty(), errors, List.of());
    }

    @Transactional(readOnly = true)
    public TemplateRenderer.RenderResult preview(UUID tenantId, UUID versionId, JsonNode payload) {
        return renderer.render(requireVersion(tenantId, versionId), payload);
    }

    @Transactional
    public TemplateVersion publish(UUID tenantId, UUID id) { var v=requireVersion(tenantId,id); v.publish(); return v; }
    @Transactional
    public TemplateVersion reopen(UUID tenantId, UUID id) { var v=requireVersion(tenantId,id); v.reopen(); return v; }
    @Transactional
    public TemplateVersion archive(UUID tenantId, UUID id) { var v=requireVersion(tenantId,id); v.archive(); return v; }

    private DocumentTemplate requireTemplate(UUID tenantId, UUID id) { return templates.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new NoSuchElementException("Template not found")); }
    private DocumentTemplate requireActiveTemplate(UUID tenantId, UUID id) { var t=requireTemplate(tenantId,id);
        if (!"ACTIVE".equals(t.getStatus())) throw new IllegalArgumentException("Template is archived"); return t; }
    private TemplateVersion requireVersion(UUID tenantId, UUID id) { return versions.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new NoSuchElementException("Template version not found")); }
    private String normalizeCode(String code) { String v=code.trim().toUpperCase(Locale.ROOT);
        if (!v.matches("[A-Z][A-Z0-9_]{1,99}")) throw new IllegalArgumentException("Invalid code"); return v; }
    private SourceSchemaManagementService.ValidationIssue issue(String code,String path,String message) {
        return new SourceSchemaManagementService.ValidationIssue(code,path,message); }
}
