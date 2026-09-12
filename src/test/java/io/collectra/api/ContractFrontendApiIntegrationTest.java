package io.collectra.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class ContractFrontendApiIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void contractCrudListAndTenantIsolation() throws Exception {
        String token = register("contract-main");
        String foreignToken = register("contract-foreign");
        JsonNode customer = createCustomer(token, "CUST-CONTRACT", "Contract Customer");
        JsonNode foreignCustomer =
                createCustomer(foreignToken, "CUST-FOREIGN", "Foreign Contract Customer");

        JsonNode contract =
                createContract(
                        token,
                        customer.get("id").asText(),
                        "EXT-CONTRACT-1",
                        "CN-2026-001",
                        "2026-01-01");
        createContract(
                foreignToken,
                foreignCustomer.get("id").asText(),
                "EXT-FOREIGN",
                "CN-FOREIGN",
                "2026-01-01");

        mockMvc.perform(
                        get("/api/v1/contracts")
                                .header("Authorization", bearer(token))
                                .param("search", "cn-2026")
                                .param("customerId", customer.get("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].externalId").value("EXT-CONTRACT-1"));

        mockMvc.perform(
                        get("/api/v1/contracts/{id}", contract.get("id").asText())
                                .header("Authorization", bearer(foreignToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(
                        post("/api/v1/contracts")
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"customerId\":\""
                                                + foreignCustomer.get("id").asText()
                                                + "\",\"externalId\":\"EXT-CROSS\",\"contractNumber\":\"CROSS\",\"validFrom\":\"2026-01-01\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void lifecycleAndOptimisticVersionAreEnforced() throws Exception {
        String token = register("contract-lifecycle");
        JsonNode customer = createCustomer(token, "CUST-LIFE", "Lifecycle Customer");
        JsonNode contract =
                createContract(
                        token, customer.get("id").asText(), "EXT-LIFE", "LIFE-001", "2026-01-01");
        String id = contract.get("id").asText();

        JsonNode suspended =
                read(
                        post("/api/v1/contracts/{id}/suspend", id)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":0}"),
                        200);

        mockMvc.perform(
                        post("/api/v1/contracts/{id}/suspend", id)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":" + suspended.get("version").asLong() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        mockMvc.perform(
                        put("/api/v1/contracts/{id}", id)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"version\":0,\"contractNumber\":\"LIFE-002\",\"validFrom\":\"2026-01-01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        JsonNode active =
                read(
                        post("/api/v1/contracts/{id}/activate", id)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":" + suspended.get("version").asLong() + "}"),
                        200);

        JsonNode closed =
                read(
                        post("/api/v1/contracts/{id}/close", id)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":" + active.get("version").asLong() + "}"),
                        200);

        mockMvc.perform(
                        post("/api/v1/contracts/{id}/activate", id)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":" + closed.get("version").asLong() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void duplicateExternalIdAndInvalidDatesReturnStableCodes() throws Exception {
        String token = register("contract-validation");
        JsonNode customer = createCustomer(token, "CUST-VALID", "Validation Customer");
        String customerId = customer.get("id").asText();

        createContract(token, customerId, "EXT-DUP", "DUP-001", "2026-01-01");
        mockMvc.perform(
                        post("/api/v1/contracts")
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        contractBody(
                                                customerId,
                                                "EXT-DUP",
                                                "DUP-002",
                                                "2026-01-01",
                                                null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EXTERNAL_ID"));

        mockMvc.perform(
                        post("/api/v1/contracts")
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        contractBody(
                                                customerId,
                                                "EXT-RANGE",
                                                "RANGE-001",
                                                "2026-05-01",
                                                "2026-04-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
    }

    @Test
    void duplicateContractNumberIsAllowed() throws Exception {
        String token = register("contract-number");
        JsonNode customer = createCustomer(token, "CUST-NUMBER", "Number Customer");
        String customerId = customer.get("id").asText();

        createContract(token, customerId, "EXT-NUM-1", "SAME-NUMBER", "2026-01-01");
        createContract(token, customerId, "EXT-NUM-2", "SAME-NUMBER", "2026-02-01");

        mockMvc.perform(
                        get("/api/v1/contracts")
                                .header("Authorization", bearer(token))
                                .param("search", "same-number"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    private JsonNode createContract(
            String token,
            String customerId,
            String externalId,
            String contractNumber,
            String validFrom)
            throws Exception {
        return read(
                post("/api/v1/contracts")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                contractBody(
                                        customerId, externalId, contractNumber, validFrom, null)),
                201);
    }

    private String contractBody(
            String customerId,
            String externalId,
            String contractNumber,
            String validFrom,
            String validTo) {
        return "{\"customerId\":\""
                + customerId
                + "\",\"externalId\":\""
                + externalId
                + "\",\"contractNumber\":\""
                + contractNumber
                + "\",\"validFrom\":\""
                + validFrom
                + "\""
                + (validTo == null ? "" : ",\"validTo\":\"" + validTo + "\"")
                + "}";
    }

    private JsonNode createCustomer(String token, String externalId, String displayName)
            throws Exception {
        return read(
                post("/api/v1/customers")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"externalId\":\""
                                        + externalId
                                        + "\",\"customerType\":\"COMPANY\",\"displayName\":\""
                                        + displayName
                                        + "\",\"companyName\":\""
                                        + displayName
                                        + "\"}"),
                201);
    }

    private String register(String prefix) throws Exception {
        JsonNode response =
                read(
                        post("/api/v1/auth/tenants/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"slug\":\""
                                                + prefix
                                                + "-"
                                                + UUID.randomUUID()
                                                + "\",\"companyName\":\"Contract API Test\","
                                                + "\"email\":\""
                                                + UUID.randomUUID()
                                                + "@example.test\","
                                                + "\"password\":\"StrongPassword123!\"}"),
                        201);
        return response.get("accessToken").asText();
    }

    private JsonNode read(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            int expectedStatus)
            throws Exception {
        String body =
                mockMvc.perform(request)
                        .andExpect(status().is(expectedStatus))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
