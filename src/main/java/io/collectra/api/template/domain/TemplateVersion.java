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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TemplateChannel channel;

    @Column(length = 300)
    private String subject;

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
        this(templateId, templateVersion, locale, TemplateChannel.PDF, null, contentHtml, stylesheet);
    }

    public TemplateVersion(
            UUID templateId,
            int templateVersion,
            String locale,
            TemplateChannel channel,
            String subject,
            String contentHtml,
            String stylesheet) {
        this.id = UUID.randomUUID();
        this.templateId = templateId;
        this.templateVersion = templateVersion;
        this.locale = locale;
        this.channel = channel == null ? TemplateChannel.PDF : channel;
        this.subject = normalizeSubject(this.channel, subject);
        this.contentHtml = requireContent(contentHtml);
        this.stylesheet = stylesheet;
        this.status = TemplateVersionStatus.DRAFT;
    }

    public UUID getId() { return id; }
    public UUID getTemplateId() { return templateId; }
    public int getTemplateVersion() { return templateVersion; }
    public String getLocale() { return locale; }
    public TemplateChannel getChannel() { return channel; }
    public String getSubject() { return subject; }
    public TemplateVersionStatus getStatus() { return status; }
    public String getContentHtml() { return contentHtml; }
    public String getStylesheet() { return stylesheet; }

    public void publish() {
        if (status != TemplateVersionStatus.VALIDATED)
            throw new IllegalStateException("Only validated template can be published");
        status = TemplateVersionStatus.PUBLISHED;
    }

    public void update(String contentHtml, String stylesheet) {
        update(subject, contentHtml, stylesheet);
    }

    public void update(String subject, String contentHtml, String stylesheet) {
        if (status != TemplateVersionStatus.DRAFT)
            throw new IllegalStateException("Only draft template can be changed");
        this.subject = normalizeSubject(channel, subject);
        this.contentHtml = requireContent(contentHtml);
        this.stylesheet = stylesheet;
    }

    public void validated() {
        if (status != TemplateVersionStatus.DRAFT)
            throw new IllegalStateException("Only draft template can be validated");
        status = TemplateVersionStatus.VALIDATED;
    }

    public void reopen() {
        if (status != TemplateVersionStatus.VALIDATED)
            throw new IllegalStateException("Only validated template can return to draft");
        status = TemplateVersionStatus.DRAFT;
    }

    public void archive() {
        if (status != TemplateVersionStatus.PUBLISHED)
            throw new IllegalStateException("Only published template can be archived");
        status = TemplateVersionStatus.ARCHIVED;
    }

    private static String requireContent(String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Template content is required");
        return content;
    }

    private static String normalizeSubject(TemplateChannel channel, String subject) {
        if (channel == TemplateChannel.EMAIL) {
            if (subject == null || subject.isBlank()) throw new IllegalArgumentException("Email template subject is required");
            return subject.trim();
        }
        return subject == null || subject.isBlank() ? null : subject.trim();
    }
}
