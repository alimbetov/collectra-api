package io.collectra.api.document.application;

import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.template.application.TemplateRenderer;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;

import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class DocumentGenerationWorker {
    private final GenerationJobStateService states;
    private final TemplateVersionRepository versions;
    private final TemplateRenderer templates;
    private final PdfRenderer pdf;
    private final GeneratedOutputService outputs;

    public DocumentGenerationWorker(
            GenerationJobStateService states,
            TemplateVersionRepository versions,
            TemplateRenderer templates,
            PdfRenderer pdf,
            GeneratedOutputService outputs) {
        this.states = states;
        this.versions = versions;
        this.templates = templates;
        this.pdf = pdf;
        this.outputs = outputs;
    }

    public void generate(UUID jobId) {
        var job = states.begin(jobId);
        if (job == null) return;
        var version =
                versions.findById(job.templateVersionId())
                        .orElseThrow(
                                () -> new NoSuchElementException("Template version not found"));
        String html = templates.render(version, job.payload()).html();
        if (job.formats().contains(OutputFormat.HTML))
            outputs.storeHtml(job.tenantId(), job.jobId(), html);
        if (job.formats().contains(OutputFormat.PDF)) {
            states.step(jobId, "RENDER_PDF");
            outputs.storePdf(job.tenantId(), job.jobId(), pdf.render(html));
        }
        states.complete(jobId);
    }
}
