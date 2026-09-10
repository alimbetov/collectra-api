package io.collectra.api.template.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.shared.tenant.TenantContext;
import io.collectra.api.template.application.TemplateAssetService;
import io.collectra.api.template.application.TemplateBuilderService;
import io.collectra.api.template.application.TemplateManagementService;
import io.collectra.api.template.domain.FieldDefinition;
import io.collectra.api.template.domain.TemplateAsset;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/template-builder")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class TemplateBuilderController {
    private final TemplateBuilderService builder;
    private final TemplateAssetService assets;
    private final TemplateManagementService templates;

    public TemplateBuilderController(
            TemplateBuilderService builder,
            TemplateAssetService assets,
            TemplateManagementService templates) {
        this.builder = builder;
        this.assets = assets;
        this.templates = templates;
    }

    @GetMapping("/catalog")
    @PreAuthorize("hasAuthority('TEMPLATE_READ') and hasAuthority('FIELD_READ')")
    BuilderCatalogResponse catalog() {
        var catalog = builder.catalog(tenant());
        return new BuilderCatalogResponse(
                catalog.fields().stream().map(FieldResponse::from).toList(),
                catalog.assets().stream().map(AssetResponse::from).toList(),
                catalog.channels(),
                catalog.eachSyntax(),
                catalog.assetSyntax());
    }

    @PostMapping("/validate")
    @PreAuthorize("hasAuthority('TEMPLATE_MANAGE')")
    TemplateBuilderService.ValidationResult validate(@Valid @RequestBody DraftRequest request) {
        return builder.validate(tenant(), request.toDraft());
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('TEMPLATE_MANAGE')")
    TemplateBuilderService.PreviewResult preview(@Valid @RequestBody PreviewRequest request) {
        return builder.preview(tenant(), request.draft().toDraft(), request.payload());
    }

    @PostMapping("/templates/{templateId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TEMPLATE_MANAGE')")
    VersionResponse saveDraft(
            @PathVariable UUID templateId, @Valid @RequestBody DraftRequest request) {
        return VersionResponse.from(
                templates.createVersion(
                        tenant(),
                        templateId,
                        request.locale(),
                        request.channel(),
                        request.subject(),
                        request.content(),
                        request.stylesheet()));
    }

    @PutMapping("/versions/{versionId}")
    @PreAuthorize("hasAuthority('TEMPLATE_MANAGE')")
    VersionResponse updateDraft(
            @PathVariable UUID versionId, @Valid @RequestBody DraftRequest request) {
        TemplateVersion existing = templates.getVersion(tenant(), versionId);
        if (request.channel() != null && request.channel() != existing.getChannel()) {
            throw new IllegalArgumentException("Template channel cannot be changed inside an existing version");
        }
        return VersionResponse.from(
                templates.update(
                        tenant(),
                        versionId,
                        request.subject(),
                        request.content(),
                        request.stylesheet()));
    }

    @PostMapping("/versions/{versionId}/validate")
    @PreAuthorize("hasAuthority('TEMPLATE_MANAGE')")
    Object validateSavedDraft(@PathVariable UUID versionId) {
        return templates.validate(tenant(), versionId);
    }

    @PostMapping("/versions/{versionId}/publish")
    @PreAuthorize("hasAuthority('TEMPLATE_PUBLISH')")
    VersionResponse publish(@PathVariable UUID versionId) {
        return VersionResponse.from(templates.publish(tenant(), versionId));
    }

    @GetMapping("/assets")
    @PreAuthorize("hasAuthority('TEMPLATE_READ')")
    List<AssetResponse> listAssets() {
        return assets.list(tenant()).stream().map(AssetResponse::from).toList();
    }

    @PostMapping("/assets")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TEMPLATE_MANAGE')")
    AssetResponse registerAsset(@Valid @RequestBody AssetRequest request) {
        return AssetResponse.from(
                assets.register(tenant(), request.key(), request.fileId(), request.altText()));
    }

    @DeleteMapping("/assets/{assetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('TEMPLATE_MANAGE')")
    void archiveAsset(@PathVariable UUID assetId) {
        assets.archive(tenant(), assetId);
    }

    private UUID tenant() {
        return TenantContext.requireTenantId();
    }

    record DraftRequest(
            TemplateChannel channel,
            @NotBlank @Size(max = 10) String locale,
            @Size(max = 300) String subject,
            @NotBlank String content,
            String stylesheet) {
        TemplateBuilderService.BuilderDraft toDraft() {
            return new TemplateBuilderService.BuilderDraft(
                    channel, locale, subject, content, stylesheet);
        }
    }

    record PreviewRequest(@Valid @NotNull DraftRequest draft, @NotNull JsonNode payload) {}

    record AssetRequest(
            @NotBlank @Size(max = 64) String key,
            @NotNull UUID fileId,
            @Size(max = 300) String altText) {}

    record BuilderCatalogResponse(
            List<FieldResponse> fields,
            List<AssetResponse> assets,
            List<TemplateChannel> channels,
            String eachSyntax,
            String assetSyntax) {}

    record FieldResponse(
            UUID id,
            String key,
            String label,
            String dataType,
            String category,
            boolean collection,
            boolean required,
            boolean system) {
        static FieldResponse from(FieldDefinition field) {
            return new FieldResponse(
                    field.getId(),
                    field.getKey(),
                    field.getLabel(),
                    field.getDataType().name(),
                    field.getCategory(),
                    field.isCollection(),
                    field.isRequired(),
                    field.isSystem());
        }
    }

    record AssetResponse(UUID id, String key, UUID fileId, String altText, String placeholder) {
        static AssetResponse from(TemplateAsset asset) {
            return new AssetResponse(
                    asset.getId(),
                    asset.getAssetKey(),
                    asset.getFileId(),
                    asset.getAltText(),
                    "{{asset." + asset.getAssetKey() + "}}");
        }
    }

    record VersionResponse(
            UUID id,
            UUID templateId,
            int version,
            String locale,
            TemplateChannel channel,
            String subject,
            String content,
            String stylesheet,
            String status) {
        static VersionResponse from(TemplateVersion version) {
            return new VersionResponse(
                    version.getId(),
                    version.getTemplateId(),
                    version.getTemplateVersion(),
                    version.getLocale(),
                    version.getChannel(),
                    version.getSubject(),
                    version.getContentHtml(),
                    version.getStylesheet(),
                    version.getStatus().name());
        }
    }
}
