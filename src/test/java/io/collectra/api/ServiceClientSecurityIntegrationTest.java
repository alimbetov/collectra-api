package io.collectra.api;

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
class ServiceClientSecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void completedSecretRotationInvalidatesPreviouslyIssuedServiceJwt() throws Exception {
        String adminToken = registerTenant();
        String clientId = "svc-" + UUID.randomUUID().toString().substring(0, 8);
        String oldSecret = "service-old-secret-12345678901234567890";
        String newSecret = "service-new-secret-12345678901234567890";

        JsonNode client = createClient(adminToken, clientId, oldSecret, "document:read");
        String oldServiceJwt = issueToken(clientId, oldSecret, "document:read");

        mockMvc.perform(get("/api/v1/import-batches/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + oldServiceJwt))
                .andExpect(status().isNotFound());

        JsonNode rotating = read(post(
                        "/api/v1/integration/service-clients/{id}/rotate-secret",
                        client.get("id").asText())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientSecret\":\"" + newSecret + "\"}"));

        mockMvc.perform(post(
                        "/api/v1/integration/service-clients/{id}/credentials/{credentialId}/activate",
                        client.get("id").asText(),
                        rotating.get("credentialId").asText())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/import-batches/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + oldServiceJwt))
                .andExpect(status().isUnauthorized());

        token(clientId, oldSecret, "document:read").andExpect(status().isUnauthorized());
        String newServiceJwt = issueToken(clientId, newSecret, "document:read");
        mockMvc.perform(get("/api/v1/import-batches/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + newServiceJwt))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantCannotRotateOrBlockAnotherTenantsServiceClient() throws Exception {
        String tenantA = registerTenant();
        String tenantB = registerTenant();
        JsonNode foreignClient = createClient(
                tenantB,
                "foreign-" + UUID.randomUUID().toString().substring(0, 8),
                "foreign-service-secret-12345678901234567890",
                "document:read");

        mockMvc.perform(post(
                        "/api/v1/integration/service-clients/{id}/rotate-secret",
                        foreignClient.get("id").asText())
                .header("Authorization", "Bearer " + tenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientSecret\":\"replacement-secret-12345678901234567890\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(
                        "/api/v1/integration/service-clients/{id}/block",
                        foreignClient.get("id").asText())
                .header("Authorization", "Bearer " + tenantA))
                .andExpect(status().isNotFound());
    }

    private JsonNode createClient(String adminToken, String clientId, String secret, String scope)
            throws Exception {
        return read(post("/api/v1/integration/service-clients")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + clientId
                        + "\",\"name\":\"Service Client\",\"clientSecret\":\"" + secret
                        + "\",\"scopes\":[\"" + scope + "\"]}"));
    }

    private String issueToken(String clientId, String secret, String scope) throws Exception {
        return read(token(clientId, secret, scope)).get("accessToken").asText();
    }

    private org.springframework.test.web.servlet.ResultActions token(
            String clientId, String secret, String scope) throws Exception {
        return mockMvc.perform(post("/api/v1/integration/service-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + clientId + "\",\"clientSecret\":\"" + secret
                        + "\",\"scopes\":[\"" + scope + "\"]}"));
    }

    private String registerTenant() throws Exception {
        JsonNode response = read(post("/api/v1/auth/tenants/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"service-security-" + UUID.randomUUID()
                        + "\",\"companyName\":\"Security Company\",\"email\":\""
                        + UUID.randomUUID()
                        + "@example.test\",\"password\":\"StrongPassword123!\"}"));
        return response.get("accessToken").asText();
    }

    private JsonNode read(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String body = mockMvc.perform(request)
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(body);
    }
}
