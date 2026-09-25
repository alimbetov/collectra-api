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
        String oldSecret;
        String newSecret;

        JsonNode client =
                read(
                        post("/api/v1/integration/service-clients")
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"clientId\":\""
                                                + clientId
                                                + "\",\"name\":\"ERP\",\"scopes\":[\"integration:imports:read\"]}"));

        oldSecret = client.get("clientSecret").asText();
        assertThat(oldSecret).hasSizeGreaterThanOrEqualTo(32);

        JsonNode rotating =
                read(
                        post(
                                        "/api/v1/integration/service-clients/{id}/rotate-secret",
                                        client.has("client")
                                                ? client.get("client").get("id").asText()
                                                : client.get("id").asText())
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"));
        newSecret = rotating.get("clientSecret").asText();

        token(clientId, oldSecret).andExpect(status().isOk());
        token(clientId, newSecret).andExpect(status().isOk());

        mockMvc.perform(
                        post(
                                        "/api/v1/integration/service-clients/{id}/credentials/{credentialId}/activate",
                                        client.has("client")
                                                ? client.get("client").get("id").asText()
                                                : client.get("id").asText(),
                                        rotating.get("client").get("credentialId").asText())
                                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        token(clientId, oldSecret).andExpect(status().isUnauthorized());
        token(clientId, newSecret).andExpect(status().isOk());
    }

    @Test
    void clientAndSecretArePermanentByDefaultAndCanBeBlocked() throws Exception {
        String accessToken = register();
        String clientId = "erp-" + UUID.randomUUID().toString().substring(0, 8);
        String secret;
        JsonNode client =
                read(
                        post("/api/v1/integration/service-clients")
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"clientId\":\""
                                                + clientId
                                                + "\",\"name\":\"ERP\",\"scopes\":[\"integration:imports:read\"]}"));

        secret = client.get("clientSecret").asText();
        JsonNode safeClient = client.get("client");
        assertThat(safeClient.get("expiresAt").isNull()).isTrue();
        assertThat(safeClient.get("secretExpiresAt").isNull()).isTrue();
        assertThat(secret).hasSizeGreaterThanOrEqualTo(32);

        mockMvc.perform(
                        post(
                                        "/api/v1/integration/service-clients/{id}/block",
                                        client.has("client")
                                                ? client.get("client").get("id").asText()
                                                : client.get("id").asText())
                                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        token(clientId, secret).andExpect(status().isUnauthorized());

        mockMvc.perform(
                        post(
                                        "/api/v1/integration/service-clients/{id}/unblock",
                                        client.has("client")
                                                ? client.get("client").get("id").asText()
                                                : client.get("id").asText())
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

        String detail =
                mockMvc.perform(
                                get(
                                                "/api/v1/integration/service-clients/{id}",
                                                client.has("client")
                                                        ? client.get("client").get("id").asText()
                                                        : client.get("id").asText())
                                        .header("Authorization", "Bearer " + accessToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(detail).contains(clientId).doesNotContain(secret);

        String scopes =
                mockMvc.perform(
                                get("/api/v1/integration/service-client-scopes")
                                        .header("Authorization", "Bearer " + accessToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(scopes)
                .contains("integration:imports:create")
                .contains("integration:imports:read")
                .doesNotContain(secret);
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
