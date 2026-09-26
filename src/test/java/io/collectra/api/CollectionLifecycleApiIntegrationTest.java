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
class CollectionLifecycleApiIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void collectionOfficerLifecycleKeepsReceivableFinancialStateAuthoritative() throws Exception {
        String token = register();
        JsonNode customer = createCustomer(token);
        JsonNode invoice = createInvoice(token, customer.get("id").asText());
        String invoiceId = invoice.get("id").asText();
        JsonNode before = getInvoice(token, invoiceId);

        JsonNode collectionCase = createCase(token, customer.get("id").asText(), invoiceId);
        String caseId = collectionCase.get("id").asText();

        JsonNode started =
                command(
                        token,
                        "/api/v1/collection-cases/" + caseId + "/start",
                        collectionCase.get("version").asLong());
        assertThat(started.get("status").asText()).isEqualTo("IN_PROGRESS");

        JsonNode promise =
                read(
                        post("/api/v1/collection-cases/{id}/promises", caseId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"amount":"125.5000","currency":"KZT","promisedDate":"2026-10-15"}
                                        """),
                        201);
        JsonNode fulfilled =
                command(
                        token,
                        "/api/v1/collection-cases/"
                                + caseId
                                + "/promises/"
                                + promise.get("id").asText()
                                + "/fulfill",
                        promise.get("version").asLong());
        assertThat(fulfilled.get("status").asText()).isEqualTo("FULFILLED");

        JsonNode dispute =
                read(
                        post("/api/v1/collection-cases/{id}/disputes", caseId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"reason\":\"AMOUNT_CONTESTED\",\"description\":\"verify\"}"),
                        201);
        JsonNode resolved =
                read(
                        post(
                                        "/api/v1/collection-cases/{caseId}/disputes/{disputeId}/resolve",
                                        caseId,
                                        dispute.get("id").asText())
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"version":%d,"resolutionCode":"CONFIRMED","summary":"checked"}
                                        """
                                                .formatted(dispute.get("version").asLong())),
                        200);
        assertThat(resolved.get("status").asText()).isEqualTo("RESOLVED");

        JsonNode action =
                read(
                        post("/api/v1/collection-cases/{id}/actions", caseId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"actionType":"CALL","description":"follow up","dueAt":"2026-10-15T10:00:00Z","priority":"HIGH"}
                                        """),
                        201);
        JsonNode completed =
                command(
                        token,
                        "/api/v1/collection-cases/"
                                + caseId
                                + "/actions/"
                                + action.get("id").asText()
                                + "/complete",
                        action.get("version").asLong());
        assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");

        JsonNode closed =
                read(
                        post("/api/v1/collection-cases/{id}/close", caseId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"version":%d,"reason":"OTHER"}
                                        """
                                                .formatted(started.get("version").asLong())),
                        200);
        assertThat(closed.get("status").asText()).isEqualTo("CLOSED");

        JsonNode timeline =
                read(
                        get("/api/v1/collection-cases/{id}/timeline", caseId)
                                .header("Authorization", bearer(token))
                                .param("page", "0")
                                .param("size", "50"),
                        200);
        assertThat(timeline.get("totalElements").asInt()).isGreaterThanOrEqualTo(8);
        assertThat(timeline.get("items").toString())
                .contains(
                        "CASE_CREATED",
                        "CASE_STARTED",
                        "PROMISE_CREATED",
                        "PROMISE_FULFILLED",
                        "DISPUTE_CREATED",
                        "DISPUTE_RESOLVED",
                        "ACTION_CREATED",
                        "ACTION_COMPLETED",
                        "CASE_CLOSED");

        JsonNode after = getInvoice(token, invoiceId);
        assertThat(after.get("originalAmount")).isEqualTo(before.get("originalAmount"));
        assertThat(after.get("outstandingAmount")).isEqualTo(before.get("outstandingAmount"));
        assertThat(after.get("paymentStatus")).isEqualTo(before.get("paymentStatus"));
    }

    private JsonNode command(String token, String path, long version) throws Exception {
        return read(
                post(path)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + "}"),
                200);
    }

    private JsonNode createCase(String token, String customerId, String invoiceId)
            throws Exception {
        return read(
                post("/api/v1/collection-cases")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"customerId":"%s","invoiceId":"%s","priority":"HIGH"}
                                """
                                        .formatted(customerId, invoiceId)),
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
                                {"externalId":"C-%s","customerType":"COMPANY","displayName":"Collection Lifecycle","companyName":"Collection Lifecycle"}
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

    private JsonNode getInvoice(String token, String invoiceId) throws Exception {
        return read(
                get("/api/v1/invoices/{id}", invoiceId).header("Authorization", bearer(token)),
                200);
    }

    private String register() throws Exception {
        JsonNode response =
                read(
                        post("/api/v1/auth/tenants/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"slug":"collection-lifecycle-%s","companyName":"Collection Lifecycle","email":"%s@example.test","password":"StrongPassword123!"}
                                        """
                                                .formatted(UUID.randomUUID(), UUID.randomUUID())),
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
