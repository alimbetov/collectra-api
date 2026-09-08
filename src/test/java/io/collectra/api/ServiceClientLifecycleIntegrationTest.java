package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class ServiceClientLifecycleIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void rotatesSecretWithoutDowntimeAndRevokesOldSecretOnActivation() throws Exception {
        String accessToken = register();
        String clientId = "erp-" + UUID.randomUUID().toString().substring(0, 8);
        String oldSecret = "customer-owned-old-secret-1234567890";
        String newSecret = "customer-owned-new-secret-1234567890";

        JsonNode client =
                read(
                        post("/api/v1/integration/service-clients")
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"clientId\":\""
                                                + clientId
                                                + "\",\"name\":\"ERP\",\"clientSecret\":\""
                                                + oldSecret
                                                + "\",\"scopes\":[\"integration:imports:read\"]}"));

        JsonNode rotating =
                read(
                        post(
                                        "/api/v1/integration/service-clients/{id}/rotate-secret",
                                        client.get("id").asText())
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"clientSecret\":\"" + newSecret + "\"}"));

        token(clientId, oldSecret).andExpect(status().isOk());
        token(clientId, newSecret).andExpect(status().isOk());

        mockMvc.perform(
                        post(
                                        "/api/v1/integration/service-clients/{id}/credentials/{credentialId}/activate",
                                        client.get("id").asText(),
                                        rotating.get("credentialId").asText())
                                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        token(clientId, oldSecret).andExpect(status().isUnauthorized());
        token(clientId, newSecret).andExpect(status().isOk());
    }

    @Test
    void clientAndSecretArePermanentByDefaultAndCanBeBlocked() throws Exception {
        String accessToken = register();
        String clientId = "erp-" + UUID.randomUUID().toString().substring(0, 8);
        String secret = "customer-owned-service-secret-123456789";
        JsonNode client =
                read(
                        post("/api/v1/integration/service-clients")
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"clientId\":\""
                                                + clientId
                                                + "\",\"name\":\"ERP\",\"clientSecret\":\""
                                                + secret
                                                + "\",\"scopes\":[\"integration:imports:read\"]}"));

        assertThat(client.get("expiresAt").isNull()).isTrue();
        assertThat(client.get("secretExpiresAt").isNull()).isTrue();
        assertThat(client.has("clientSecret")).isFalse();

        mockMvc.perform(
                        post(
                                        "/api/v1/integration/service-clients/{id}/block",
                                        client.get("id").asText())
                                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        token(clientId, secret).andExpect(status().isUnauthorized());

        mockMvc.perform(
                        post(
                                        "/api/v1/integration/service-clients/{id}/unblock",
                                        client.get("id").asText())
                                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        token(clientId, secret).andExpect(status().isOk());

        String list =
                mockMvc.perform(
                                get("/api/v1/integration/service-clients")
                                        .header("Authorization", "Bearer " + accessToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(list).doesNotContain(secret);
    }

    private org.springframework.test.web.servlet.ResultActions token(String clientId, String secret)
            throws Exception {
        return mockMvc.perform(
                post("/api/v1/integration/service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"clientId\":\""
                                        + clientId
                                        + "\",\"clientSecret\":\""
                                        + secret
                                        + "\",\"scopes\":[\"integration:imports:read\"]}"));
    }

    private String register() throws Exception {
        JsonNode response =
                read(
                        post("/api/v1/auth/tenants/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"slug\":\"service-lifecycle-"
                                                + UUID.randomUUID()
                                                + "\",\"companyName\":\"Company\","
                                                + "\"email\":\""
                                                + UUID.randomUUID()
                                                + "@example.test\","
                                                + "\"password\":\"StrongPassword123!\"}"));
        return response.get("accessToken").asText();
    }

    private JsonNode read(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String body =
                mockMvc.perform(request)
                        .andExpect(status().is2xxSuccessful())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body);
    }
}
