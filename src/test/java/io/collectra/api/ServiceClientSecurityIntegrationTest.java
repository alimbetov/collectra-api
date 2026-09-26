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
        String oldSecret;
        String newSecret;

        JsonNode issued = createClient(adminToken, clientId, "document:read");
        JsonNode client = issued.get("client");
        oldSecret = issued.get("clientSecret").asText();
        String oldServiceJwt = issueToken(clientId, oldSecret, "document:read");

        mockMvc.perform(
                        get("/api/v1/import-batches/{id}", UUID.randomUUID())
                                .header("Authorization", "Bearer " + oldServiceJwt))
                .andExpect(status().isNotFound());

        JsonNode rotating =
                read(
                        post(
                                        "/api/v1/integration/service-clients/{id}/rotate-secret",
                                        client.get("id").asText())
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"));
        newSecret = rotating.get("clientSecret").asText();

        mockMvc.perform(
                        post(
                                        "/api/v1/integration/service-clients/{id}/credentials/{credentialId}/activate",
                                        client.get("id").asText(),
                                        rotating.get("client").get("credentialId").asText())
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(
                        get("/api/v1/import-batches/{id}", UUID.randomUUID())
                                .header("Authorization", "Bearer " + oldServiceJwt))
                .andExpect(status().isUnauthorized());

        token(clientId, oldSecret, "document:read").andExpect(status().isUnauthorized());
        String newServiceJwt = issueToken(clientId, newSecret, "document:read");
        mockMvc.perform(
                        get("/api/v1/import-batches/{id}", UUID.randomUUID())
                                .header("Authorization", "Bearer " + newServiceJwt))
                .andExpect(status().isNotFound());
    }

    @Test
    void ingestionRequiresCurrentCreateScopeAndBlockedClientCannotMintAnotherToken()\n            throws Exception {
        String adminToken = registerTenant();
        String clientId = "ingest-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode issued =
                createClient(
                        adminToken,
                        clientId,
                        "integration:imports:create",
                        "integration:imports:read");
        JsonNode client = issued.get("client");
        String secret = issued.get("clientSecret").asText();

        String readOnlyJwt = issueToken(clientId, secret, "integration:imports:read");
        mockMvc.perform(
                        post(\n                                        "/api/v1/integration/sources/{sourceCode}/ingestions",\n                                        "missing-source")
                                .header("Authorization", "Bearer " + readOnlyJwt)
                                .header("Idempotency-Key", "scope-denied")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isForbidden());

        String createJwt = issueToken(clientId, secret, "integration:imports:create");
        mockMvc.perform(
                        post("/api/v1/integration/sources/{sourceCode}/ingestions", "missing-source")
                                .header("Authorization", "Bearer " + createJwt)
                                .header("Idempotency-Key", "scope-allowed")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        post(
                                        "/api/v1/integration/service-clients/{id}/block",
                                        client.get("id").asText())
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        token(clientId, secret, "integration:imports:create")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tenantCannotRotateOrBlockAnotherTenantsServiceClient() throws Exception {
        String tenantA = registerTenant();
        String tenantB = registerTenant();
        JsonNode foreignClient =
                createClient(
                                tenantB,
                                "foreign-" + UUID.randomUUID().toString().substring(0, 8),
                                "document:read")
                        .get("client");

        mockMvc.perform(
                        post(
                                        "/api/v1/integration/service-clients/{id}/rotate-secret",
                                        foreignClient.get("id").asText())
                                .header("Authorization", "Bearer " + tenantA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        post(
                                        "/api/v1/integration/service-clients/{id}/block",
                                        foreignClient.get("id").asText())
                                .header("Authorization", "Bearer " + tenantA))
                .andExpect(status().isNotFound());
    }

    private JsonNode createClient(String adminToken, String clientId, String scope)
            throws Exception {
        return createClient(adminToken, clientId, new String[] {scope});
    }

    private JsonNode createClient(String adminToken, String clientId, String... scopes)
            throws Exception {
        String body =
                json.writeValueAsString(
                        java.util.Map.of(
                                "clientId", clientId,
                                "name", "Service Client",
                                "scopes", java.util.Set.of(scopes)));
        return read(
                post("/api/v1/integration/service-clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private String issueToken(String clientId, String secret, String scope) throws Exception {
        String body =
                token(clientId, secret, scope)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private org.springframework.test.web.servlet.ResultActions token(
            String clientId, String secret, String scope) throws Exception {
        return mockMvc.perform(
                post("/api/v1/integration/service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"clientId\":\""
                                        + clientId
                                        + "\",\"clientSecret\":\""
                                        + secret
                                        + "\",\"scopes\":[\""
                                        + scope
                                        + "\"]}"));
    }

    private String registerTenant() throws Exception {
        JsonNode response =
                read(
                        post("/api/v1/auth/tenants/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"slug\":\"service-security-"
                                                + UUID.randomUUID()
                                                + "\",\"companyName\":\"Security Company\",\"email\":\""
                                                + UUID.randomUUID()
                                                + "@example.test\",\"password\":\"StrongPassword123!\"}"));
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
