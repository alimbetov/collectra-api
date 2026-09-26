package io.collectra.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class IngestionServiceAuthorizationIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void ingestionRequiresCreateScopeAndBlockedClientCannotMintAnotherToken() throws Exception {
        String adminToken = registerTenant();
        String clientId = "ingest-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode issued = createClient(adminToken, clientId);
        String secret = issued.get("clientSecret").asText();

        String readOnlyJwt = issueToken(clientId, secret, "integration:imports:read");
        mockMvc.perform(
                        post(
                                        "/api/v1/integration/sources/{sourceCode}/ingestions",
                                        "missing-source")
                                .header("Authorization", "Bearer " + readOnlyJwt)
                                .header("Idempotency-Key", "scope-denied")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isForbidden());

        String createJwt = issueToken(clientId, secret, "integration:imports:create");
        mockMvc.perform(
                        post(
                                        "/api/v1/integration/sources/{sourceCode}/ingestions",
                                        "missing-source")
                                .header("Authorization", "Bearer " + createJwt)
                                .header("Idempotency-Key", "scope-allowed")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        post(
                                        "/api/v1/integration/service-clients/{id}/block",
                                        issued.get("client").get("id").asText())
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        serviceToken(clientId, secret, "integration:imports:create")
                .andExpect(status().isUnauthorized());
    }

    private JsonNode createClient(String adminToken, String clientId) throws Exception {
        String body =
                json.writeValueAsString(
                        Map.of(
                                "clientId",
                                clientId,
                                "name",
                                "Ingestion client",
                                "scopes",
                                Set.of("integration:imports:create", "integration:imports:read")));
        String response =
                mockMvc.perform(
                                post("/api/v1/integration/service-clients")
                                        .header("Authorization", "Bearer " + adminToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(response);
    }

    private String issueToken(String clientId, String secret, String scope) throws Exception {
        String response =
                serviceToken(clientId, secret, scope)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(response).get("accessToken").asText();
    }

    private org.springframework.test.web.servlet.ResultActions serviceToken(
            String clientId, String secret, String scope) throws Exception {
        return mockMvc.perform(
                post("/api/v1/integration/service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                json.writeValueAsString(
                                        Map.of(
                                                "clientId",
                                                clientId,
                                                "clientSecret",
                                                secret,
                                                "scopes",
                                                Set.of(scope)))));
    }

    private String registerTenant() throws Exception {
        String response =
                mockMvc.perform(
                                post("/api/v1/auth/tenants/register")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "slug",
                                                                "ingestion-auth-" + UUID.randomUUID(),
                                                                "companyName",
                                                                "Ingestion Authorization",
                                                                "email",
                                                                UUID.randomUUID() + "@example.test",
                                                                "password",
                                                                "StrongPassword123!"))))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(response).get("accessToken").asText();
    }
}
