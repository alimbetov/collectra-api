package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.collectra.api.file.application.FileDownload;
import io.collectra.api.file.application.FileMetadata;
import io.collectra.api.file.application.FileService;
import io.collectra.api.file.domain.FileCategory;
import io.collectra.api.file.domain.FileStatus;
import io.collectra.api.template.domain.TemplateAsset;
import io.collectra.api.template.infrastructure.TemplateAssetRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TemplateAssetServiceUnitTest {

    private TemplateAssetRepository assets;
    private FileService files;
    private TemplateAssetService service;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        assets = mock(TemplateAssetRepository.class);
        files = mock(FileService.class);
        service = new TemplateAssetService(assets, files, new FieldKeyValidator(), json);
    }

    @Test
    void rejectsNonAssetFile() {
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(files.get(tenantId, fileId))
                .thenReturn(
                        metadata(
                                tenantId,
                                fileId,
                                FileCategory.IMPORT_SOURCE,
                                FileStatus.READY,
                                "image/png",
                                10L));

        assertThatThrownBy(() -> service.register(tenantId, "logo", fileId, "Logo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ASSET");
    }

    @Test
    void rejectsAssetThatIsNotReady() {
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(files.get(tenantId, fileId))
                .thenReturn(
                        metadata(
                                tenantId,
                                fileId,
                                FileCategory.ASSET,
                                FileStatus.DELETE_FAILED,
                                "image/png",
                                10L));

        assertThatThrownBy(() -> service.register(tenantId, "logo", fileId, "Logo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("READY");
    }

    @Test
    void rejectsUnsupportedMimeType() {
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(files.get(tenantId, fileId))
                .thenReturn(
                        metadata(
                                tenantId,
                                fileId,
                                FileCategory.ASSET,
                                FileStatus.READY,
                                "image/svg+xml",
                                10L));

        assertThatThrownBy(() -> service.register(tenantId, "logo", fileId, "Logo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PNG, JPEG or GIF");
    }

    @Test
    void rejectsAssetAboveInlineLimit() {
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(files.get(tenantId, fileId))
                .thenReturn(
                        metadata(
                                tenantId,
                                fileId,
                                FileCategory.ASSET,
                                FileStatus.READY,
                                "image/png",
                                2L * 1024 * 1024 + 1));

        assertThatThrownBy(() -> service.register(tenantId, "logo", fileId, "Logo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 MB");
    }

    @Test
    void enrichesPayloadWithServerManagedDataUriAndOverwritesSameManagedKey() {
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        byte[] content = "image-content".getBytes(StandardCharsets.UTF_8);
        TemplateAsset asset = mock(TemplateAsset.class);
        when(asset.getAssetKey()).thenReturn("logo");
        when(asset.getFileId()).thenReturn(fileId);
        when(assets.findAllByTenantIdAndStatusOrderByAssetKeyAsc(tenantId, "ACTIVE"))
                .thenReturn(List.of(asset));
        FileMetadata metadata =
                metadata(
                        tenantId,
                        fileId,
                        FileCategory.ASSET,
                        FileStatus.READY,
                        "image/png",
                        (long) content.length);
        when(files.get(tenantId, fileId)).thenReturn(metadata);
        when(files.openContent(tenantId, fileId))
                .thenReturn(new FileDownload(metadata, new ByteArrayInputStream(content)));
        ObjectNode payload = json.createObjectNode();
        payload.putObject("asset").put("logo", "caller-controlled-value");

        var enriched = service.enrichPayload(tenantId, payload);

        assertThat(enriched.at("/asset/logo").asText())
                .isEqualTo("data:image/png;base64,aW1hZ2UtY29udGVudA==");
        assertThat(payload.at("/asset/logo").asText()).isEqualTo("caller-controlled-value");
    }

    @Test
    void rejectsScalarReservedAssetNamespace() {
        UUID tenantId = UUID.randomUUID();
        ObjectNode payload = json.createObjectNode().put("asset", "caller-controlled-value");

        assertThatThrownBy(() -> service.enrichPayload(tenantId, payload))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private FileMetadata metadata(
            UUID tenantId,
            UUID fileId,
            FileCategory category,
            FileStatus status,
            String contentType,
            long size) {
        return new FileMetadata(
                fileId,
                tenantId,
                null,
                category,
                "asset.bin",
                contentType,
                size,
                null,
                status,
                Instant.now(),
                null,
                null);
    }
}
