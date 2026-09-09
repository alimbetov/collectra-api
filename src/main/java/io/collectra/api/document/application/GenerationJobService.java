package io.collectra.api.document.application;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.collectra.api.document.domain.GeneratedDocument;
import io.collectra.api.document.domain.GenerationJob;
import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.document.infrastructure.GeneratedDocumentRepository;
import io.collectra.api.document.infrastructure.GenerationJobRepository;
import io.collectra.api.importing.application.MappingExecutionService;
import io.collectra.api.shared.outbox.OutboxService;
import io.collectra.api.template.application.TemplateRenderer;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.domain.TemplateVersionStatus;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class GenerationJobService {
    private final MappingExecutionService mappings;
    private final TemplateVersionRepository versions;
    private final DocumentTemplateRepository templates;
    private final GenerationJobRepository jobs;
    private final GeneratedDocumentRepository documents;
    private final TemplateRenderer renderer;
    private final OutboxService outbox;
    private final ObjectMapper json;

    public GenerationJobService(
            MappingExecutionService mappings,
            TemplateVersionRepository versions,
            DocumentTemplateRepository templates,
            GenerationJobRepository jobs,
            GeneratedDocumentRepository documents,
            TemplateRenderer renderer,
            OutboxService outbox,
            ObjectMapper json) {
        this.mappings = mappings;
        this.versions = versions;
        this.templates = templates;
        this.jobs = jobs;
        this.documents = documents;
        this.renderer = renderer;
        this.outbox = outbox;
        this.json = json;
    }

    @Transactional
    public GenerationJob create(
            UUID tenantId,
            UUID mappingProfileId,
            UUID templateVersionId,
            byte[] input,
            Set<OutputFormat> formats) {
        if (formats == null || formats.isEmpty())
            throw new IllegalArgumentException("At least one output format is required");
        var mapped = mappings.execute(tenantId, mappingProfileId, input);
        TemplateVersion version =
                versions.findByIdAndTenantId(templateVersionId, tenantId)
                        .orElseThrow(
                                () -> new NoSuchElementException("Template version not found"));
        if (version.getStatus() != TemplateVersionStatus.PUBLISHED)
            throw new IllegalArgumentException("Only published template versions can be generated");
        var template =
                templates
                        .findByIdAndTenantId(version.getTemplateId(), tenantId)
                        .orElseThrow(() -> new NoSuchElementException("Template not found"));
        if (!template.getDocumentType().equals(mapped.documentType()))
            throw new IllegalArgumentException(
                    "Mapping profile and template document types differ");
        renderer.render(version, mapped.normalizedPayload());
        GenerationJob job =
                jobs.save(
                        new GenerationJob(
                                tenantId,
                                mapped.documentType(),
                                mappingProfileId,
                                templateVersionId,
                                null,
                                mapped.normalizedPayload(),
                                formats));
        try {
            outbox.append(
                    tenantId,
                    "GENERATION_JOB",
                    job.getId(),
                    "DOCUMENT_GENERATION_REQUESTED",
                    json.writeValueAsString(java.util.Map.of("jobId", job.getId())));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize generation event", ex);
        }
        return job;
    }

    @Transactional(readOnly = true)
    public GenerationJob get(UUID tenantId, UUID jobId) {
        return jobs.findByIdAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Generation job not found"));
    }

    @Transactional(readOnly = true)
    public List<GeneratedDocument> outputs(UUID tenantId, UUID jobId) {
        get(tenantId, jobId);
        return documents.findAllByGenerationJobIdOrderByFormat(jobId);
    }

    @Transactional(readOnly = true)
    public GeneratedDocument output(UUID tenantId, UUID jobId, OutputFormat format) {
        get(tenantId, jobId);
        return documents
                .findByGenerationJobIdAndFormat(jobId, format)
                .orElseThrow(() -> new NoSuchElementException("Generated document not found"));
    }
}
