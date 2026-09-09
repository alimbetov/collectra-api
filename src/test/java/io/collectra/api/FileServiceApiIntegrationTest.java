package io.collectra.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.file.infrastructure.storage.FileStorageException;
import io.collectra.api.file.infrastructure.storage.ObjectStorage;
import io.collectra.api.file.infrastructure.storage.StoredObject;
import io.collectra.api.file.infrastructure.storage.UploadObject;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
class FileServiceApiIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @MockBean ObjectStorage storage;

    @BeforeEach
    void storageDefaults() throws Exception {
        when(storage.upload(any(UploadObject.class)))
                .thenAnswer(
                        invocation -> {
                            UploadObject command = invocation.getArgument(0);
                            long transferred = command.content().transferTo(java.io.OutputStream.nullOutputStream());
                            return new StoredObject(transferred, "etag");
                        });
        when(storage.download(any()))
                .thenReturn(new ByteArrayInputStream("stored-content".getBytes(StandardCharsets.UTF_8)));
        when(storage.generatePresignedGetUrl(any(), any(Duration.class)))
                .thenReturn(URI.create("https://storage.example.test/download?signature=redacted"));
    }

    @Test
    void tenantAdminCanUploadReadDownloadPresignAndDelete() throws Exception {
        Auth admin = register("file-api-" + UUID.randomUUID(), "file-api@example.test");
        MockMultipartFile multipartFile =
                new MockMultipartFile(
                        "file",
                        "source.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "hello-file".getBytes(StandardCharsets.UTF_8));

        String response =
                mockMvc.perform(
                                multipart("/api/v1/files")
                                        .file(multipartFile)
                                        .param("category", "IMPORT_SOURCE")
                                        .header("Authorization", "Bearer " + admin.accessToken))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("READY"))
                        .andExpect(jsonPath("$.originalFilename").value("source.xlsx"))
                        .andExpect(jsonPath("$.checksumSha256").isString())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        UUID fileId = UUID.fromString(json.readTree(response).get("fileId").asText());

        mockMvc.perform(
                        get("/api/v1/files/{fileId}", fileId)
                                .header("Authorization", "Bearer " + admin.accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(fileId.toString()));

        mockMvc.perform(
                        get("/api/v1/files/{fileId}/content", fileId)
                                .header("Authorization", "Bearer " + admin.accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("source.xlsx")))
                .andExpect(content().bytes("stored-content".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(
                        get("/api/v1/files/{fileId}/download-url", fileId)
                                .header("Authorization", "Bearer " + admin.accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.expiresInSeconds").value(600));

        mockMvc.perform(
                        delete("/api/v1/files/{fileId}", fileId)
                                .header("Authorization", "Bearer " + admin.accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        get("/api/v1/files/{fileId}/content", fileId)
                                .header("Authorization", "Bearer " + admin.accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FILE_STATE_CONFLICT"));
    }

    @Test
    void fileIdFromAnotherTenantIsNotEnumerable() throws Exception {
        Auth first = register("file-owner-" + UUID.randomUUID(), "file-owner@example.test");
        Auth second = register("file-other-" + UUID.randomUUID(), "file-other@example.test");
        UUID fileId = upload(first, "private.txt", "private");

        mockMvc.perform(
                        get("/api/v1/files/{fileId}", fileId)
                                .header("Authorization", "Bearer " + second.accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));
    }

    @Test
    void storageFailureIsMappedToSanitized503ProblemDetail() throws Exception {
        Auth admin = register("file-failure-" + UUID.randomUUID(), "file-failure@example.test");
        doThrow(new FileStorageException("internal endpoint leaked", new RuntimeException("boom")))
                .when(storage)
                .delete(any());
        UUID fileId = upload(admin, "delete-me.txt", "data");

        mockMvc.perform(
                        delete("/api/v1/files/{fileId}", fileId)
                                .header("Authorization", "Bearer " + admin.accessToken))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FILE_STORAGE_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("Object storage operation failed"));
    }

    @Test
    void unauthenticatedFileAccessIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/files/{fileId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private UUID upload(Auth auth, String filename, String body) throws Exception {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", filename, MediaType.TEXT_PLAIN_VALUE, body.getBytes(StandardCharsets.UTF_8));
        String response =
                mockMvc.perform(
                                multipart("/api/v1/files")
                                        .file(file)
                                        .param("category", "TEMP")
                                        .header("Authorization", "Bearer " + auth.accessToken))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return UUID.fromString(json.readTree(response).get("fileId").asText());
    }

    private Auth register(String slug, String email) throws Exception {
        JsonNode response =
                read(
                        post("/api/v1/auth/tenants/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"slug\":\""
                                                + slug
                                                + "\",\"companyName\":\"File Test\",\"email\":\""
                                                + email
                                                + "\",\"password\":\"StrongPassword123!\"}"));
        return new Auth(response.get("accessToken").asText());
    }

    private JsonNode read(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String body =
                mockMvc.perform(request)
                        .andExpect(status().is2xxSuccessful())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body);
    }

    private record Auth(String accessToken) {}
}
