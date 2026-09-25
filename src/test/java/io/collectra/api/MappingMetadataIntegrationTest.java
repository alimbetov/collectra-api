package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class MappingMetadataIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void exposesBackendOwnedTransformationCatalogue() throws Exception {
        String accessToken = register();
        String body =
                mockMvc.perform(
                                get("/api/v1/mapping-metadata/transformations")
                                        .header("Authorization", "Bearer " + accessToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode transformations = json.readTree(body);
        assertThat(transformations).hasSize(11);
        assertThat(body)
                .contains("NONE")
                .contains("DATE_PARSE")
                .contains("DECIMAL_PARSE")
                .contains("BOOLEAN_PARSE")
                .contains("NORMALIZE_PHONE")
                .contains("VALIDATE_EMAIL")
                .contains("SPLIT")
                .contains("CHANNELS_PARSE")
                .contains("delimiterRegex");
    }

    private String register() throws Exception {
        String body =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/auth/tenants/register")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"slug\":\"mapping-metadata-"
                                                        + UUID.randomUUID()
                                                        + "\",\"companyName\":\"Company\","
                                                        + "\"email\":\""
                                                        + UUID.randomUUID()
                                                        + "@example.test\","
                                                        + "\"password\":\"StrongPassword123!\"}"))
                        .andExpect(status().is2xxSuccessful())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }
}
