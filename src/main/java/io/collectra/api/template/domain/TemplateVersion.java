package io.collectra.api.template.domain;

import io.collectra.api.shared.persistence.AuditableEntity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "template_versions")
public class TemplateVersion extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "template_version", nullable = false)
    private int templateVersion;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(name = "content_html", nullable = false, columnDefinition = "text")
    private String contentHtml;

    @Column(columnDefinition = "text")
    private String stylesheet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TemplateVersionStatus status;

    protected TemplateVersion() {}

    public TemplateVersion(
            UUID templateId,
            int templateVersion,
            String locale,
            String contentHtml,
            String stylesheet) {
        this.id = UUID.randomUUID();
        this.templateId = templateId;
        this.templateVersion = templateVersion;
        this.locale = locale;
        this.contentHtml = contentHtml;
        this.stylesheet = stylesheet;
        this.status = TemplateVersionStatus.DRAFT;
    }

    public UUID getId() {
        return id;
    }

    public TemplateVersionStatus getStatus() {
        return status;
    }

    public String getContentHtml() {
        return contentHtml;
    }

    public String getStylesheet() {
        return stylesheet;
    }

    public void publish() {
        if (status != TemplateVersionStatus.DRAFT)
            throw new IllegalStateException("Only draft template can be published");
        status = TemplateVersionStatus.PUBLISHED;
    }
}
