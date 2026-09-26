package io.collectra.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Wave-B adversarial smoke. Uses public auth flows and two independent tenants so tenant context,
 * HTTP security and repository predicates are exercised together.
 */
@AutoConfigureMockMvc
class FunctionalHardeningSecuritySmokeIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void alphaCannotReadBetaCustomerContractInvoicePaymentOrCollectionCase() throws Exception {
        Auth alpha = register("fh-alpha");
        Auth beta = register("fh-beta");

        JsonNode betaCustomer = createCustomer(beta.token());
        JsonNode betaContract = createContract(beta.token(), betaCustomer.get("id").asText());
        JsonNode betaInvoice = createInvoice(beta.token(), betaCustomer.get("id").asText());
        JsonNode betaPayment = createPayment(beta.token(), betaCustomer.get("id").asText());
        JsonNode betaCase = createCollectionCase(beta.token(), betaCustomer.get("id").asText(), betaInvoice.get("id").asText());

        foreignGet(alpha.token(), "/api/v1/customers/" + betaCustomer.get("id").asText());
        foreignGet(alpha.token(), "/api/v1/contracts/" + betaContract.get("id").asText());
        foreignGet(alpha.token(), "/api/v1/invoices/" + betaInvoice.get("id").asText());
        foreignGet(alpha.token(), "/api/v1/payments/" + betaPayment.get("id").asText());
        foreignGet(alpha.token(), "/api/v1/collection-cases/" + betaCase.get("id").asText());
    }

    @Test
    void roleHumanOnlyActorCurrentlyReachesCoreBusinessReadsAndMutations() throws Exception {
        Auth admin = register("fh-vertical");
        JsonNode role = read(post("/api/v1/identity/roles")
                .header("Authorization", bearer(admin.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"MINIMAL_" + code() + "\",\"permissions\":[\"USER_READ\"]}"));
        String email = code().toLowerCase() + "@example.test";
        JsonNode invitation = read(post("/api/v1/identity/invitations")
                .header("Authorization", bearer(admin.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"roleIds\":[\"" + role.get("id").asText() + "\"]}"));
        JsonNode accepted = read(post("/api/v1/auth/invitations/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + invitation.get("developmentToken").asText() + "\",\"password\":\"WaveBPassword123!\",\"displayName\":\"Restricted Smoke\"}"));
        String restricted = accepted.get("accessToken").asText();

        // Characterization assertions: Wave B deliberately records the current broad ROLE_HUMAN
        // boundary. If product policy requires capability separation these 2xx responses become
        // the reproducible evidence for FH-005 and the follow-up fix changes expectations to 403.
        mockMvc.perform(get("/api/v1/customers").header("Authorization", bearer(restricted)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", bearer(restricted))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"restricted-" + code() + "\",\"customerType\":\"PERSON\",\"displayName\":\"Restricted Created\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/contracts").header("Authorization", bearer(restricted)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/invoices").header("Authorization", bearer(restricted)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/collection-cases").header("Authorization", bearer(restricted)))
                .andExpect(status().isOk());
    }

    private void foreignGet(String token, String path) throws Exception {
        mockMvc.perform(get(path).header("Authorization", bearer(token))).andExpect(status().isNotFound());
    }

    private JsonNode createCustomer(String token) throws Exception {
        return read(post("/api/v1/customers").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"externalId\":\"customer-" + code() + "\",\"customerType\":\"PERSON\",\"displayName\":\"Beta Customer\"}"));
    }

    private JsonNode createContract(String token, String customerId) throws Exception {
        return read(post("/api/v1/contracts").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"" + customerId + "\",\"externalId\":\"contract-" + code() + "\",\"contractNumber\":\"CN-" + code() + "\",\"validFrom\":\"2026-01-01\"}"));
    }

    private JsonNode createInvoice(String token, String customerId) throws Exception {
        return read(post("/api/v1/invoices").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"" + customerId + "\",\"externalId\":\"invoice-" + code() + "\",\"invoiceNumber\":\"INV-" + code() + "\",\"invoiceDate\":\"2026-01-01\",\"dueDate\":\"2026-01-31\",\"originalAmount\":\"100.00\",\"currency\":\"USD\"}"));
    }

    private JsonNode createPayment(String token, String customerId) throws Exception {
        return read(post("/api/v1/payments").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"" + customerId + "\",\"externalId\":\"payment-" + code() + "\",\"paymentDate\":\"2026-02-01\",\"amount\":\"25.00\",\"currency\":\"USD\"}"));
    }

    private JsonNode createCollectionCase(String token, String customerId, String invoiceId) throws Exception {
        return read(post("/api/v1/collection-cases").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"" + customerId + "\",\"invoiceId\":\"" + invoiceId + "\",\"title\":\"Wave B Case\",\"priority\":\"NORMAL\"}"));
    }

    private Auth register(String prefix) throws Exception {
        JsonNode value = read(post("/api/v1/auth/tenants/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"" + prefix + "-" + UUID.randomUUID() + "\",\"companyName\":\"Wave B Smoke\",\"email\":\"" + code().toLowerCase() + "@example.test\",\"password\":\"WaveBAdminPassword123!\"}"));
        return new Auth(value.get("accessToken").asText());
    }

    private JsonNode read(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private String bearer(String token) { return "Bearer " + token; }
    private String code() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    private record Auth(String token) {}
}
