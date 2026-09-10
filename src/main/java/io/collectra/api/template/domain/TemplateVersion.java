package io.collectra.api.template.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "builder_json", columnDefinition = "jsonb")
    private JsonNode builderJson;

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
        this(templateId, templateVersion, locale, TemplateChannel.PDF, null, null, contentHtml, stylesheet);
    }

    public TemplateVersion(
            UUID templateId,
            int templateVersion,
            String locale,
            TemplateChannel channel,
            String subject,
            String contentHtml,
            String stylesheet) {
        this(templateId, templateVersion, locale, channel, subject, null, contentHtml, stylesheet);
    }

    public TemplateVersion(
            UUID templateId,
            int templateVersion,
            String locale,
            TemplateChannel channel,
            String subject,
            JsonNode builderJson,
            String contentHtml,
            String stylesheet) {
        this.id = UUID.randomUUID();
        this.templateId = templateId;
        this.templateVersion = templateVersion;
        this.locale = locale;
        this.channel = channel == null ? TemplateChannel.PDF : channel;
        this.subject = normalizeSubject(this.channel, subject);
        this.builderJson = builderJson == null ? null : builderJson.deepCopy();
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
    public JsonNode getBuilderJson() { return builderJson == null ? null : builderJson.deepCopy(); }
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
        ensureDraft();
        if (isBuilderManaged()) {
            throw new IllegalStateException(
                    "Builder-managed template must be updated through the builder API");
        }
        this.subject = normalizeSubject(channel, subject);
        this.contentHtml = requireContent(contentHtml);
        this.stylesheet = stylesheet;
    }

    public void updateBuilder(
            String subject, JsonNode builderJson, String renderedHtml, String stylesheet) {
        ensureDraft();
        if (builderJson == null || !builderJson.isObject()) {
            throw new IllegalArgumentException("builderJson is required");
        }
        this.subject = normalizeSubject(channel, subject);
        this.builderJson = builderJson.deepCopy();
        this.contentHtml = requireContent(renderedHtml);
        this.stylesheet = stylesheet;
    }

    public boolean isBuilderManaged() {
        return builderJson != null;
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

    private void ensureDraft() {
        if (status != TemplateVersionStatus.DRAFT)
            throw new IllegalStateException("Only draft template can be changed");
    }

    private static String requireContent(String content) {
        if (content == null || content.isBlank())
            throw new IllegalArgumentException("Template content is required");
        return content;
    }

    private static String normalizeSubject(TemplateChannel channel, String subject) {
        if (channel == TemplateChannel.EMAIL) {
            if (subject == null || subject.isBlank())
                throw new IllegalArgumentException("Email template subject is required");
            return subject.trim();
        }
        return subject == null || subject.isBlank() ? null : subject.trim();
    }
}
