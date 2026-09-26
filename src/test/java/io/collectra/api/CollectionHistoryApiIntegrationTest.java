package io.collectra.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class CollectionHistoryApiIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void childHistoriesArePagedBoundedAndTenantScoped() throws Exception {
        String token = register("collection-history");
        String foreignToken = register("collection-history-foreign");
        JsonNode customer = createCustomer(token);
        JsonNode invoice = createInvoice(token, customer.get("id").asText());
        JsonNode collectionCase =
                createCase(token, customer.get("id").asText(), invoice.get("id").asText());
        String caseId = collectionCase.get("id").asText();

        createPromise(token, caseId, "100.0000");
        createPromise(token, caseId, "200.0000");
        createDispute(token, caseId);
        createAction(token, caseId);

        assertPage(token, caseId, "promises", 2);
        assertPage(token, caseId, "disputes", 1);
        assertPage(token, caseId, "actions", 1);
        assertPage(token, caseId, "timeline", 5);

        for (String child : new String[] {"promises", "disputes", "actions", "timeline"}) {
            mockMvc.perform(
                            get("/api/v1/collection-cases/{id}/" + child, caseId)
                                    .header("Authorization", bearer(token))
                                    .param("size", "100"))
                    .andExpect(status().isOk());
            mockMvc.perform(
                            get("/api/v1/collection-cases/{id}/" + child, caseId)
                                    .header("Authorization", bearer(token))
                                    .param("size", "101"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(
                            get("/api/v1/collection-cases/{id}/" + child, caseId)
                                    .header("Authorization", bearer(foreignToken)))
                    .andExpect(status().isNotFound());
        }
    }

    private void assertPage(String token, String caseId, String child, int total) throws Exception {
        mockMvc.perform(
                        get("/api/v1/collection-cases/{id}/" + child, caseId)
                                .header("Authorization", bearer(token))
                                .param("page", "0")
                                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(total))
                .andExpect(jsonPath("$.hasNext").value(total > 1));
    }

    private JsonNode createCase(String token, String customerId, String invoiceId)
            throws Exception {
        return read(
                post("/api/v1/collection-cases")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"customerId\":\""
                                        + customerId
                                        + "\",\"invoiceId\":\""
                                        + invoiceId
                                        + "\",\"priority\":\"NORMAL\"}"),
                201);
    }

    private void createPromise(String token, String caseId, String amount) throws Exception {
        read(
                post("/api/v1/collection-cases/{id}/promises", caseId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"amount":"%s","currency":"KZT","promisedDate":"2026-10-15"}
                                """
                                        .formatted(amount)),
                201);
    }

    private void createDispute(String token, String caseId) throws Exception {
        read(
                post("/api/v1/collection-cases/{id}/disputes", caseId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"AMOUNT_CONTESTED\"}"),
                201);
    }

    private void createAction(String token, String caseId) throws Exception {
        read(
                post("/api/v1/collection-cases/{id}/actions", caseId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"actionType":"CALL","dueAt":"2026-10-15T10:00:00Z","priority":"NORMAL"}
                                """),
                201);
    }

    private JsonNode createCustomer(String token) throws Exception {
        String suffix = UUID.randomUUID().toString();
        return read(
                post("/api/v1/customers")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"externalId":"C-%s","customerType":"COMPANY","displayName":"Collection Customer","companyName":"Collection Customer"}
                                """
                                        .formatted(suffix)),
                201);
    }

    private JsonNode createInvoice(String token, String customerId) throws Exception {
        String suffix = UUID.randomUUID().toString();
        return read(
                post("/api/v1/invoices")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"customerId":"%s","externalId":"INV-%s","invoiceNumber":"N-%s","invoiceDate":"2026-09-01","dueDate":"2026-09-10","originalAmount":"1000.0000","currency":"KZT"}
                                """
                                        .formatted(customerId, suffix, suffix)),
                201);
    }

    private String register(String prefix) throws Exception {
        JsonNode response =
                read(
                        post("/api/v1/auth/tenants/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"slug":"%s-%s","companyName":"Collection API Test","email":"%s@example.test","password":"StrongPassword123!"}
                                        """
                                                .formatted(
                                                        prefix,
                                                        UUID.randomUUID(),
                                                        UUID.randomUUID())),
                        201);
        return response.get("accessToken").asText();
    }

    private JsonNode read(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            int expected)
            throws Exception {
        String body =
                mockMvc.perform(request)
                        .andExpect(status().is(expected))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
