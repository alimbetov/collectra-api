package io.collectra.api.template.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.collectra.api.file.application.FileMetadata;
import io.collectra.api.file.application.FileService;
import io.collectra.api.file.domain.FileCategory;
import io.collectra.api.file.domain.FileStatus;
import io.collectra.api.template.domain.TemplateAsset;
import io.collectra.api.template.infrastructure.TemplateAssetRepository;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateAssetService {
    private static final long MAX_INLINE_ASSET_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/gif");

    private final TemplateAssetRepository assets;
    private final FileService files;
    private final FieldKeyValidator fieldKeyValidator;
    private final ObjectMapper json;

    public TemplateAssetService(
            TemplateAssetRepository assets,
            FileService files,
            FieldKeyValidator fieldKeyValidator,
            ObjectMapper json) {
        this.assets = assets;
        this.files = files;
        this.fieldKeyValidator = fieldKeyValidator;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<TemplateAsset> list(UUID tenantId) {
        return assets.findAllByTenantIdAndStatusOrderByAssetKeyAsc(tenantId, "ACTIVE");
    }

    @Transactional
    public TemplateAsset register(UUID tenantId, String key, UUID fileId, String altText) {
        String canonicalKey = validateKey(key);
        validateFile(tenantId, fileId);
        TemplateAsset asset =
                assets.findByTenantIdAndAssetKeyAndStatus(tenantId, canonicalKey, "ACTIVE")
                        .orElse(null);
        if (asset == null) {
            return assets.save(new TemplateAsset(tenantId, canonicalKey, fileId, altText));
        }
        asset.replaceFile(fileId, altText);
        return asset;
    }

    @Transactional
    public void archive(UUID tenantId, UUID assetId) {
        TemplateAsset asset =
                assets.findByIdAndTenantId(assetId, tenantId)
                        .orElseThrow(() -> new NoSuchElementException("Template asset not found"));
        asset.archive();
    }

    @Transactional(readOnly = true)
    public boolean exists(UUID tenantId, String key) {
        return assets.findByTenantIdAndAssetKeyAndStatus(tenantId, key, "ACTIVE").isPresent();
    }

    @Transactional(readOnly = true)
    public JsonNode enrichPayload(UUID tenantId, JsonNode payload) {
        ObjectNode root;
        if (payload == null || payload.isNull()) root = json.createObjectNode();
        else if (!payload.isObject())
            throw new IllegalArgumentException("Preview payload must be a JSON object");
        else root = ((ObjectNode) payload).deepCopy();

        root.remove("asset");
        ObjectNode assetNode = root.putObject("asset");
        for (TemplateAsset asset : list(tenantId)) {
            FileMetadata metadata = validateFile(tenantId, asset.getFileId());
            try (var download = files.openContent(tenantId, asset.getFileId()).content()) {
                byte[] bytes = download.readNBytes((int) MAX_INLINE_ASSET_BYTES + 1);
                if (bytes.length > MAX_INLINE_ASSET_BYTES) {
                    throw new IllegalArgumentException("Template asset exceeds 2 MB inline limit");
                }
                String dataUri =
                        "data:"
                                + metadata.contentType().toLowerCase(Locale.ROOT)
                                + ";base64,"
                                + Base64.getEncoder().encodeToString(bytes);
                assetNode.put(asset.getAssetKey(), dataUri);
            } catch (IOException ex) {
                throw new IllegalStateException("Cannot read template asset", ex);
            }
        }
        return root;
    }

    private FileMetadata validateFile(UUID tenantId, UUID fileId) {
        FileMetadata metadata = files.get(tenantId, fileId);
        if (metadata.category() != FileCategory.ASSET) {
            throw new IllegalArgumentException("Template asset must reference an ASSET file");
        }
        if (metadata.status() != FileStatus.READY) {
            throw new IllegalArgumentException("Template asset file must be READY");
        }
        String contentType =
                metadata.contentType() == null
                        ? ""
                        : metadata.contentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Template asset must be PNG, JPEG or GIF");
        }
        if (metadata.sizeBytes() > MAX_INLINE_ASSET_BYTES) {
            throw new IllegalArgumentException("Template asset exceeds 2 MB inline limit");
        }
        return metadata;
    }

    private String validateKey(String key) {
        if (key == null || key.isBlank())
            throw new IllegalArgumentException("Asset key is required");
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        FieldPath path = fieldKeyValidator.validatePlaceholderKey("asset." + normalized);
        if (path.segments().size() != 2) {
            throw new IllegalArgumentException("Asset key must be a single canonical segment");
        }
        return path.segments().get(1);
    }
}
