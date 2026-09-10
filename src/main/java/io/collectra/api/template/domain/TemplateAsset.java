package io.collectra.api.template.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
        name = "template_assets",
        uniqueConstraints = @UniqueConstraint(name = "uk_template_asset_key", columnNames = {"tenant_id", "asset_key"}))
public class TemplateAsset extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "asset_key", nullable = false, length = 80)
    private String assetKey;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "alt_text", length = 300)
    private String altText;

    @Column(nullable = false, length = 20)
    private String status;

    protected TemplateAsset() {}

    public TemplateAsset(UUID tenantId, String assetKey, UUID fileId, String altText) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.assetKey = assetKey;
        this.fileId = fileId;
        this.altText = altText == null || altText.isBlank() ? null : altText.trim();
        this.status = "ACTIVE";
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getAssetKey() { return assetKey; }
    public UUID getFileId() { return fileId; }
    public String getAltText() { return altText; }
    public String getStatus() { return status; }

    public void replaceFile(UUID fileId, String altText) {
        if (!"ACTIVE".equals(status)) throw new IllegalStateException("Archived asset cannot be changed");
        this.fileId = fileId;
        this.altText = altText == null || altText.isBlank() ? null : altText.trim();
    }

    public void archive() {
        this.status = "ARCHIVED";
    }
}
