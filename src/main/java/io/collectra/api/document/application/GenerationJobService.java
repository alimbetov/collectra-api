package io.collectra.api.document.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.document.domain.GeneratedDocument;
import io.collectra.api.document.domain.GenerationJob;
import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.document.infrastructure.GeneratedDocumentRepository;
import io.collectra.api.document.infrastructure.GenerationJobRepository;
import io.collectra.api.importing.application.MappingExecutionService;
import io.collectra.api.template.application.TemplateRenderer;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.domain.TemplateVersionStatus;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GenerationJobService {
    public static final String REQUESTED_EVENT_TYPE = DocumentGenerationRequestPublisher.EVENT_TYPE;

    private final MappingExecutionService mappings;
    private final TemplateVersionRepository versions;
    private final DocumentTemplateRepository templates;
    private final GenerationJobRepository jobs;
    private final GeneratedDocumentRepository documents;
    private final TemplateRenderer renderer;
    private final DocumentGenerationRequestPublisher requests;

    public GenerationJobService(
            MappingExecutionService mappings,
            TemplateVersionRepository versions,
            DocumentTemplateRepository templates,
            GenerationJobRepository jobs,
            GeneratedDocumentRepository documents,
            TemplateRenderer renderer,
            DocumentGenerationRequestPublisher requests) {
        this.mappings = mappings;
        this.versions = versions;
        this.templates = templates;
        this.jobs = jobs;
        this.documents = documents;
        this.renderer = renderer;
        this.requests = requests;
    }

    @Transactional
    public GenerationJob create(
            UUID tenantId,
            UUID mappingProfileId,
            UUID templateVersionId,
            byte[] input,
            Set<OutputFormat> formats) {
        requireFormats(formats);
        var mapped = mappings.execute(tenantId, mappingProfileId, input);
        return createMapped(tenantId, mappingProfileId, templateVersionId, mapped, formats);
    }

    @Transactional
    public GenerationJob createMapped(
            UUID tenantId,
            UUID mappingProfileId,
            UUID templateVersionId,
            MappingExecutionService.MappingResult mapped,
            Set<OutputFormat> formats) {
        requireFormats(formats);
        TemplateVersion version = requirePublishedVersion(tenantId, templateVersionId);
        var template = requireTemplate(tenantId, version);
        if (!template.getDocumentType().equals(mapped.documentType())) {
            throw new IllegalArgumentException(
                    "Mapping profile and template document types differ");
        }
        renderer.render(version, mapped.normalizedPayload());
        GenerationJob job =
                jobs.save(
                        new GenerationJob(
                                tenantId,
                                mapped.documentType(),
                                mapped.sourceSchemaVersionId(),
                                mappingProfileId,
                                templateVersionId,
                                null,
                                mapped.normalizedPayload(),
                                formats,
                                mapped.mappingConfigSha256(),
                                templateSha(version)));
        request(job);
        return job;
    }

    /**
     * Creates a durable generation job without publishing the generation request yet. This is
     * intentionally used by message materialization so the PENDING MessageAttachment can be
     * persisted before the broker-visible request is appended to the Outbox.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public GenerationJob prepareFromNormalizedPayload(
            UUID tenantId,
            UUID templateVersionId,
            JsonNode normalizedPayload,
            Set<OutputFormat> formats) {
        requireFormats(formats);
        if (normalizedPayload == null || normalizedPayload.isNull()) {
            throw new IllegalArgumentException("normalizedPayload is required");
        }
        TemplateVersion version = requirePublishedVersion(tenantId, templateVersionId);
        var template = requireTemplate(tenantId, version);
        renderer.render(version, normalizedPayload);
        return jobs.save(
                new GenerationJob(
                        tenantId,
                        template.getDocumentType(),
                        null,
                        null,
                        templateVersionId,
                        null,
                        normalizedPayload.deepCopy(),
                        formats,
                        null,
                        templateSha(version)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void request(GenerationJob job) {
        if (job == null) {
            throw new IllegalArgumentException("job is required");
        }
        requests.requested(job.getTenantId(), job.getId());
    }

    @Transactional(readOnly = true)
    public GenerationJob get(UUID tenantId, UUID jobId) {
        return jobs.findByIdAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Generation job not found"));
    }

    @Transactional(readOnly = true)
    public List<GeneratedDocument> outputs(UUID tenantId, UUID jobId) {
        get(tenantId, jobId);
        return documents.findAllByGenerationJobIdOrderByFormat(jobId).stream()
                .filter(document -> tenantId.equals(document.getTenantId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public GeneratedDocument output(UUID tenantId, UUID jobId, OutputFormat format) {
        get(tenantId, jobId);
        return documents
                .findByTenantIdAndGenerationJobIdAndFormat(tenantId, jobId, format)
                .orElseThrow(() -> new NoSuchElementException("Generated document not found"));
    }

    private TemplateVersion requirePublishedVersion(UUID tenantId, UUID templateVersionId) {
        TemplateVersion version =
                versions.findByIdAndTenantId(templateVersionId, tenantId)
                        .orElseThrow(
                                () -> new NoSuchElementException("Template version not found"));
        if (version.getStatus() != TemplateVersionStatus.PUBLISHED) {
            throw new IllegalArgumentException("Only published template versions can be generated");
        }
        return version;
    }

    private io.collectra.api.template.domain.DocumentTemplate requireTemplate(
            UUID tenantId, TemplateVersion version) {
        return templates
                .findByIdAndTenantId(version.getTemplateId(), tenantId)
                .orElseThrow(() -> new NoSuchElementException("Template not found"));
    }

    private void requireFormats(Set<OutputFormat> formats) {
        if (formats == null || formats.isEmpty()) {
            throw new IllegalArgumentException("At least one output format is required");
        }
    }

    private String templateSha(TemplateVersion version) {
        return sha256(
                version.getContentHtml()
                        + "\n"
                        + (version.getStylesheet() == null ? "" : version.getStylesheet()));
    }

    private String sha256(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
