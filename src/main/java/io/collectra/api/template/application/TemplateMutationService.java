package io.collectra.api.template.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.importing.application.SourceSchemaManagementService;
import io.collectra.api.shared.error.BusinessConflictException;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateMutationService {
    private final DocumentTemplateRepository templates;
    private final TemplateVersionRepository versions;
    private final TemplateManagementService management;

    public TemplateMutationService(
            DocumentTemplateRepository templates,
            TemplateVersionRepository versions,
            TemplateManagementService management) {
        this.templates = templates;
        this.versions = versions;
        this.management = management;
    }

    @Transactional
    public DocumentTemplate rename(
            UUID tenantId, UUID templateId, String name, long expectedVersion) {
        requireTemplateRevision(tenantId, templateId, expectedVersion);
        return management.rename(tenantId, templateId, name);
    }

    @Transactional
    public void archiveTemplate(UUID tenantId, UUID templateId, long expectedVersion) {
        requireTemplateRevision(tenantId, templateId, expectedVersion);
        management.archiveTemplate(tenantId, templateId);
    }

    @Transactional
    public TemplateVersion update(
            UUID tenantId,
            UUID versionId,
            String subject,
            String content,
            String stylesheet,
            long expectedVersion) {
        requireVersionRevision(tenantId, versionId, expectedVersion);
        return management.update(tenantId, versionId, subject, content, stylesheet);
    }

    @Transactional
    public TemplateVersion updateBuilder(
            UUID tenantId,
            UUID versionId,
            String subject,
            JsonNode builderJson,
            String renderedHtml,
            String stylesheet,
            long expectedVersion) {
        requireVersionRevision(tenantId, versionId, expectedVersion);
        return management.updateBuilder(
                tenantId, versionId, subject, builderJson, renderedHtml, stylesheet);
    }

    @Transactional
    public SourceSchemaManagementService.ValidationResult validate(
            UUID tenantId, UUID versionId, long expectedVersion) {
        requireVersionRevision(tenantId, versionId, expectedVersion);
        return management.validate(tenantId, versionId);
    }

    @Transactional
    public TemplateVersion publish(UUID tenantId, UUID versionId, long expectedVersion) {
        requireVersionRevision(tenantId, versionId, expectedVersion);
        return management.publish(tenantId, versionId);
    }

    @Transactional
    public TemplateVersion reopen(UUID tenantId, UUID versionId, long expectedVersion) {
        requireVersionRevision(tenantId, versionId, expectedVersion);
        return management.reopen(tenantId, versionId);
    }

    @Transactional
    public TemplateVersion archive(UUID tenantId, UUID versionId, long expectedVersion) {
        requireVersionRevision(tenantId, versionId, expectedVersion);
        return management.archive(tenantId, versionId);
    }

    private void requireTemplateRevision(UUID tenantId, UUID id, long expectedVersion) {
        long actual =
                templates
                        .findByIdAndTenantId(id, tenantId)
                        .orElseThrow(() -> new NoSuchElementException("Template not found"))
                        .getVersion();
        requireRevision(actual, expectedVersion);
    }

    private void requireVersionRevision(UUID tenantId, UUID id, long expectedVersion) {
        long actual =
                versions.findByIdAndTenantId(id, tenantId)
                        .orElseThrow(() -> new NoSuchElementException("Template version not found"))
                        .getVersion();
        requireRevision(actual, expectedVersion);
    }

    private void requireRevision(long actualVersion, long expectedVersion) {
        if (actualVersion != expectedVersion) {
            throw new BusinessConflictException(
                    "VERSION_CONFLICT",
                    "Resource version conflict: expected "
                            + expectedVersion
                            + " but was "
                            + actualVersion);
        }
    }
}
