package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
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
class ReceivableFrontendApiIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void invoiceAndPaymentListsArePagedFilteredAndTenantScoped() throws Exception {
        String token = register("receivable-list");
        String foreignToken = register("receivable-list-foreign");
        JsonNode customer = createCustomer(token, "CUST-RCV-A", "Receivable A");
        JsonNode contract = createContract(token, customer.get("id").asText(), "CONT-A");
        createInvoice(
                token,
                customer.get("id").asText(),
                contract.get("id").asText(),
                "INV-EXT-A",
                "INV-2026-A",
                "1000.0000",
                "KZT");
        JsonNode foreignCustomer = createCustomer(foreignToken, "CUST-RCV-B", "Receivable B");
        createInvoice(
                foreignToken,
                foreignCustomer.get("id").asText(),
                null,
                "INV-EXT-B",
                "INV-2026-B",
                "500.0000",
                "KZT");
        createPayment(
                token,
                customer.get("id").asText(),
                "PAY-EXT-A",
                "BANK-REF-ALPHA",
                "600.0000",
                "KZT");

        mockMvc.perform(
                        get("/api/v1/invoices")
                                .header("Authorization", bearer(token))
                                .param("search", "inv-2026")
                                .param("contractId", contract.get("id").asText())
                                .param("page", "0")
                                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].externalId").value("INV-EXT-A"));

        mockMvc.perform(
                        get("/api/v1/payments")
                                .header("Authorization", bearer(token))
                                .param("search", "alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].externalId").value("PAY-EXT-A"));

        mockMvc.perform(
                        get("/api/v1/invoices")
                                .header("Authorization", bearer(token))
                                .param("externalId", "INV-EXT-B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void invoiceContractMustBelongToSameCustomerAndTenant() throws Exception {
        String token = register("receivable-contract");
        String foreignToken = register("receivable-contract-foreign");
        JsonNode customerA = createCustomer(token, "CUST-A", "Customer A");
        JsonNode customerB = createCustomer(token, "CUST-B", "Customer B");
        JsonNode contractB = createContract(token, customerB.get("id").asText(), "CONT-B");
        JsonNode foreignCustomer = createCustomer(foreignToken, "CUST-F", "Customer F");
        JsonNode foreignContract =
                createContract(foreignToken, foreignCustomer.get("id").asText(), "CONT-F");

        mockMvc.perform(
                        post("/api/v1/invoices")
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        invoiceBody(
                                                customerA.get("id").asText(),
                                                contractB.get("id").asText(),
                                                "INV-MISMATCH",
                                                "INV-MISMATCH",
                                                "100.0000",
                                                "KZT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_MISMATCH"));

        mockMvc.perform(
                        post("/api/v1/invoices")
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        invoiceBody(
                                                customerA.get("id").asText(),
                                                foreignContract.get("id").asText(),
                                                "INV-FOREIGN",
                                                "INV-FOREIGN",
                                                "100.0000",
                                                "KZT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void allocationIsIdempotentAndReversalRestoresInvoiceState() throws Exception {
        String token = register("receivable-allocation");
        JsonNode customer = createCustomer(token, "CUST-ALLOC", "Allocation Customer");
        JsonNode invoice =
                createInvoice(
                        token,
                        customer.get("id").asText(),
                        null,
                        "INV-ALLOC",
                        "INV-ALLOC",
                        "1000.0000",
                        "KZT");
        JsonNode payment =
                createPayment(
                        token,
                        customer.get("id").asText(),
                        "PAY-ALLOC",
                        "ALLOC-REF",
                        "600.0000",
                        "KZT");
        String paymentId = payment.get("id").asText();
        String invoiceId = invoice.get("id").asText();
        String commandId = UUID.randomUUID().toString();

        JsonNode first = allocate(token, paymentId, commandId, invoiceId, "400.0000", 201);
        JsonNode replay = allocate(token, paymentId, commandId, invoiceId, "400.0000", 201);
        assertThat(replay.get("id").asText()).isEqualTo(first.get("id").asText());

        mockMvc.perform(
                        post("/api/v1/payments/{id}/allocations", paymentId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"commandId\":\""
                                                + commandId
                                                + "\",\"invoiceId\":\""
                                                + invoiceId
                                                + "\",\"amount\":\"300.0000\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        mockMvc.perform(
                        get("/api/v1/invoices/{id}", invoiceId)
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAmount").value(400.0))
                .andExpect(jsonPath("$.outstandingAmount").value(600.0))
                .andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"));

        JsonNode reversed =
                read(
                        post(
                                        "/api/v1/payments/{paymentId}/allocations/{allocationId}/reverse",
                                        paymentId,
                                        first.get("id").asText())
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"version\":"
                                                + first.get("version").asLong()
                                                + ",\"reason\":\"OPERATOR_CORRECTION\"}"),
                        200);
        assertThat(reversed.get("status").asText()).isEqualTo("REVERSED");

        mockMvc.perform(
                        get("/api/v1/invoices/{id}", invoiceId)
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAmount").value(0.0))
                .andExpect(jsonPath("$.outstandingAmount").value(1000.0))
                .andExpect(jsonPath("$.paymentStatus").value("OPEN"));

        mockMvc.perform(
                        post(
                                        "/api/v1/payments/{paymentId}/allocations/{allocationId}/reverse",
                                        paymentId,
                                        first.get("id").asText())
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"version\":"
                                                + reversed.get("version").asLong()
                                                + ",\"reason\":\"SECOND\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALLOCATION_ALREADY_REVERSED"));
    }

    @Test
    void allocationRejectsPaymentAndInvoiceOverAllocation() throws Exception {
        String token = register("receivable-overalloc");
        JsonNode customer = createCustomer(token, "CUST-OVER", "Over Allocation");
        JsonNode invoice =
                createInvoice(
                        token,
                        customer.get("id").asText(),
                        null,
                        "INV-OVER",
                        "INV-OVER",
                        "300.0000",
                        "KZT");
        JsonNode payment =
                createPayment(
                        token,
                        customer.get("id").asText(),
                        "PAY-OVER",
                        "OVER-REF",
                        "200.0000",
                        "KZT");

        mockMvc.perform(
                        post("/api/v1/payments/{id}/allocations", payment.get("id").asText())
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        allocationBody(
                                                UUID.randomUUID().toString(),
                                                invoice.get("id").asText(),
                                                "250.0000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALLOCATION_EXCEEDS_PAYMENT"));
    }

    private JsonNode allocate(
            String token,
            String paymentId,
            String commandId,
            String invoiceId,
            String amount,
            int statusCode)
            throws Exception {
        return read(
                post("/api/v1/payments/{id}/allocations", paymentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(allocationBody(commandId, invoiceId, amount)),
                statusCode);
    }

    private String allocationBody(String commandId, String invoiceId, String amount) {
        return "{\"commandId\":\""
                + commandId
                + "\",\"invoiceId\":\""
                + invoiceId
                + "\",\"amount\":\""
                + amount
                + "\"}";
    }

    private JsonNode createInvoice(
            String token,
            String customerId,
            String contractId,
            String externalId,
            String invoiceNumber,
            String amount,
            String currency)
            throws Exception {
        return read(
                post("/api/v1/invoices")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                invoiceBody(
                                        customerId,
                                        contractId,
                                        externalId,
                                        invoiceNumber,
                                        amount,
                                        currency)),
                201);
    }

    private String invoiceBody(
            String customerId,
            String contractId,
            String externalId,
            String invoiceNumber,
            String amount,
            String currency) {
        return "{\"customerId\":\""
                + customerId
                + "\""
                + (contractId == null ? "" : ",\"contractId\":\"" + contractId + "\"")
                + ",\"externalId\":\""
                + externalId
                + "\",\"invoiceNumber\":\""
                + invoiceNumber
                + "\",\"invoiceDate\":\"2026-09-01\",\"dueDate\":\"2026-09-10\","
                + "\"originalAmount\":\""
                + amount
                + "\",\"currency\":\""
                + currency
                + "\"}";
    }

    private JsonNode createPayment(
            String token,
            String customerId,
            String externalId,
            String reference,
            String amount,
            String currency)
            throws Exception {
        return read(
                post("/api/v1/payments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"customerId\":\""
                                        + customerId
                                        + "\",\"externalId\":\""
                                        + externalId
                                        + "\",\"paymentDate\":\"2026-09-11\",\"amount\":\""
                                        + amount
                                        + "\",\"currency\":\""
                                        + currency
                                        + "\",\"paymentReference\":\""
                                        + reference
                                        + "\"}"),
                201);
    }

    private JsonNode createContract(String token, String customerId, String externalId)
            throws Exception {
        return read(
                post("/api/v1/contracts")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"customerId\":\""
                                        + customerId
                                        + "\",\"externalId\":\""
                                        + externalId
                                        + "\",\"contractNumber\":\"CN-"
                                        + externalId
                                        + "\",\"validFrom\":\"2026-01-01\"}"),
                201);
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
                                                + "\",\"companyName\":\"Receivable API Test\","
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
