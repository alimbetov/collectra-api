package io.collectra.api.file.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.AbstractIntegrationTest;
import io.collectra.api.file.infrastructure.storage.ObjectStorage;
import io.collectra.api.file.infrastructure.storage.StoredObject;
import io.collectra.api.file.infrastructure.storage.UploadObject;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class FileControllerIntegrationTest extends AbstractIntegrationTest {
    private static final byte[] CONTENT = "collectra-file-content".getBytes(StandardCharsets.UTF_8);

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @MockBean ObjectStorage storage;

    @BeforeEach
    void configureStorage() {
        reset(storage);
        when(storage.upload(any(UploadObject.class)))
                .thenAnswer(
                        invocation -> {
                            UploadObject upload = invocation.getArgument(0);
                            byte[] bytes = upload.content().readAllBytes();
                            return new StoredObject(bytes.length, "test-etag");
                        });
        when(storage.download(any())).thenAnswer(invocation -> new ByteArrayInputStream(CONTENT));
        when(storage.generatePresignedGetUrl(any(), any()))
                .thenReturn(URI.create("https://storage.example.test/download?signature=test"));
        doNothing().when(storage).delete(any());
    }

    @Test
    void tenantAdminCanUploadReadDownloadAndDeleteFile() throws Exception {
        Auth admin = register("files-" + UUID.randomUUID(), "files@example.test");
        JsonNode uploaded = upload(admin.accessToken());
        UUID fileId = UUID.fromString(uploaded.get("fileId").asText());

        assertThat(uploaded.get("status").asText()).isEqualTo("READY");
        assertThat(uploaded.get("category").asText()).isEqualTo("IMPORT_SOURCE");
        assertThat(uploaded.get("sizeBytes").asLong()).isEqualTo(CONTENT.length);
        assertThat(uploaded.has("tenantId")).isFalse();
        assertThat(uploaded.has("checksumSha256")).isFalse();

        mockMvc.perform(
                        get("/api/v1/files/{fileId}", fileId)
                                .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.status").value("READY"));

        mockMvc.perform(
                        get("/api/v1/files/{fileId}/content", fileId)
                                .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.TEXT_PLAIN_VALUE))
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getContentAsByteArray())
                                        .isEqualTo(CONTENT));

        mockMvc.perform(
                        get("/api/v1/files/{fileId}/download-url", fileId)
                                .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(fileId.toString()))
                .andExpect(
                        jsonPath("$.url")
                                .value("https://storage.example.test/download?signature=test"))
                .andExpect(jsonPath("$.expiresInSeconds").value(600));

        mockMvc.perform(
                        delete("/api/v1/files/{fileId}", fileId)
                                .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isNoContent());

        verify(storage).delete(any());

        mockMvc.perform(
                        get("/api/v1/files/{fileId}/content", fileId)
                                .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FILE_STATE_CONFLICT"));
    }

    @Test
    void registryIsTenantScopedPagedFilteredAndDoesNotLeakStorageInternals() throws Exception {
        Auth owner = register("registry-owner-" + UUID.randomUUID(), "registry-owner@example.test");
        Auth outsider =
                register(
                        "registry-outsider-" + UUID.randomUUID(), "registry-outsider@example.test");

        upload(owner.accessToken());
        upload(owner.accessToken());
        upload(outsider.accessToken());

        String body =
                mockMvc.perform(
                                get("/api/v1/files")
                                        .param("category", "IMPORT_SOURCE")
                                        .param("filename", "source")
                                        .param("page", "0")
                                        .param("size", "1")
                                        .param("sort", "createdAt,desc")
                                        .header("Authorization", "Bearer " + owner.accessToken()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.items.length()").value(1))
                        .andExpect(jsonPath("$.page").value(0))
                        .andExpect(jsonPath("$.size").value(1))
                        .andExpect(jsonPath("$.totalElements").value(2))
                        .andExpect(jsonPath("$.totalPages").value(2))
                        .andExpect(jsonPath("$.hasNext").value(true))
                        .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
                        .andExpect(jsonPath("$.items[0].storageProvider").doesNotExist())
                        .andExpect(jsonPath("$.items[0].bucket").doesNotExist())
                        .andExpect(jsonPath("$.items[0].objectKey").doesNotExist())
                        .andExpect(jsonPath("$.items[0].checksumSha256").doesNotExist())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        UUID visibleFileId =
                UUID.fromString(json.readTree(body).get("items").get(0).get("fileId").asText());
        mockMvc.perform(
                        get("/api/v1/files/{fileId}", visibleFileId)
                                .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").doesNotExist())
                .andExpect(jsonPath("$.storageProvider").doesNotExist())
                .andExpect(jsonPath("$.bucket").doesNotExist())
                .andExpect(jsonPath("$.objectKey").doesNotExist())
                .andExpect(jsonPath("$.checksumSha256").doesNotExist());

        mockMvc.perform(
                        get("/api/v1/files")
                                .param("page", "0")
                                .param("size", "201")
                                .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        get("/api/v1/files")
                                .param("sort", "bucket,asc")
                                .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fileIdFromAnotherTenantIsReportedAsNotFound() throws Exception {
        Auth owner = register("file-owner-" + UUID.randomUUID(), "owner@example.test");
        Auth outsider = register("file-outsider-" + UUID.randomUUID(), "outsider@example.test");
        UUID fileId = UUID.fromString(upload(owner.accessToken()).get("fileId").asText());

        mockMvc.perform(
                        get("/api/v1/files/{fileId}", fileId)
                                .header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));
    }

    @Test
    void unauthenticatedFileRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/files/{fileId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode upload(String accessToken) throws Exception {
        MockMultipartFile multipart =
                new MockMultipartFile("file", "source.txt", MediaType.TEXT_PLAIN_VALUE, CONTENT);
        String body =
                mockMvc.perform(
                                multipart("/api/v1/files")
                                        .file(multipart)
                                        .param("category", "IMPORT_SOURCE")
                                        .header("Authorization", "Bearer " + accessToken))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.fileId").isString())
                        .andExpect(jsonPath("$.status").value("READY"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body);
    }

    private Auth register(String slug, String email) throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/v1/auth/tenants/register")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"slug\":\""
                                                        + slug
                                                        + "\",\"companyName\":\"File Test Company\",\"email\":\""
                                                        + email
                                                        + "\",\"password\":\"StrongPassword123!\"}"))
                        .andExpect(status().is2xxSuccessful())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode response = json.readTree(body);
        return new Auth(response.get("accessToken").asText());
    }

    private record Auth(String accessToken) {}
}
