package io.collectra.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class FrontendBlockerClosureIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void frontendCanLoginByTenantSlugWithoutTenantUuid() throws Exception {
        TenantSession session = register("slug-login");

        mockMvc.perform(
                        post("/api/v1/auth/login/by-slug")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                java.util.Map.of(
                                                        "tenantSlug", session.slug(),
                                                        "email", session.email(),
                                                        "password", session.password()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        mockMvc.perform(
                        post("/api/v1/auth/login/by-slug")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                java.util.Map.of(
                                                        "tenantSlug", "missing-tenant",
                                                        "email", session.email(),
                                                        "password", session.password()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void customerListContainsScreenProjectionWithoutPerRowFrontendLookups() throws Exception {
        TenantSession session = register("customer-projection");
        String token = session.accessToken();
        JsonNode customer = createCustomer(token, "EXT-PROJECTION", "Projection Customer");
        String customerId = customer.get("id").asText();

        JsonNode segment =
                read(
                        post("/api/v1/customer-segments")
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                java.util.Map.of(
                                                        "code", "VIP-" + UUID.randomUUID().toString().substring(0, 8),
                                                        "name", "VIP Customers"))),
                        201);

        mockMvc.perform(
                        post(
                                        "/api/v1/customers/{customerId}/segments/{segmentId}",
                                        customerId,
                                        segment.get("id").asText())
                                .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        post("/api/v1/customers/{id}/emails", customerId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"projection@example.test\",\"type\":\"WORK\",\"primary\":true}"))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/customers/{id}/phones", customerId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"phone\":\"+77001234567\",\"type\":\"MOBILE\",\"primary\":true}"))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        get("/api/v1/customers")
                                .header("Authorization", bearer(token))
                                .param("externalId", "EXT-PROJECTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].displayName").value("Projection Customer"))
                .andExpect(jsonPath("$.items[0].primaryEmail").value("projection@example.test"))
                .andExpect(jsonPath("$.items[0].primaryPhone").value("+77001234567"))
                .andExpect(jsonPath("$.items[0].segments[0].id").value(segment.get("id").asText()))
                .andExpect(jsonPath("$.items[0].segments[0].name").value("VIP Customers"));
    }

    @Test
    void collectionListContainsWorkQueueProjection() throws Exception {
        TenantSession session = register("collection-projection");
        String token = session.accessToken();
        JsonNode customer = createCustomer(token, "EXT-COLLECTION", "Collection Customer");
        String customerId = customer.get("id").asText();

        JsonNode invoice =
                read(
                        post("/api/v1/invoices")
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                java.util.Map.of(
                                                        "customerId", customerId,
                                                        "externalId", "INV-EXT-" + UUID.randomUUID(),
                                                        "invoiceNumber", "INV-001",
                                                        "invoiceDate", LocalDate.now().minusDays(40).toString(),
                                                        "dueDate", LocalDate.now().minusDays(10).toString(),
                                                        "originalAmount", 150000,
                                                        "currency", "KZT"))),
                        201);

        JsonNode collectionCase =
                read(
                        post("/api/v1/collection-cases")
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                java.util.Map.of(
                                                        "customerId", customerId,
                                                        "invoiceId", invoice.get("id").asText(),
                                                        "priority", "HIGH"))),
                        201);

        mockMvc.perform(
                        post(
                                        "/api/v1/collection-cases/{caseId}/actions",
                                        collectionCase.get("id").asText())
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                java.util.Map.of(
                                                        "actionType", "CALL_CUSTOMER",
                                                        "description", "Call customer",
                                                        "dueAt", Instant.parse("2020-01-01T00:00:00Z").toString(),
                                                        "priority", "HIGH"))))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        get("/api/v1/collection-cases")
                                .header("Authorization", bearer(token))
                                .param("invoiceId", invoice.get("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].customerDisplayName").value("Collection Customer"))
                .andExpect(jsonPath("$.items[0].invoiceNumber").value("INV-001"))
                .andExpect(jsonPath("$.items[0].currency").value("KZT"))
                .andExpect(jsonPath("$.items[0].outstandingAmount").value(150000))
                .andExpect(jsonPath("$.items[0].nextActionType").value("CALL_CUSTOMER"))
                .andExpect(jsonPath("$.items[0].nextActionOverdue").value(true));
    }

    @Test
    void tenantAdministrationCanReadMemberIdentityAndCurrentRoles() throws Exception {
        TenantSession session = register("admin-projection");
        String token = session.accessToken();

        JsonNode users =
                read(
                        get("/api/v1/identity/users")
                                .header("Authorization", bearer(token)),
                        200);

        String membershipId = users.get(0).get("id").asText();
        mockMvc.perform(get("/api/v1/identity/users").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value(session.email()))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        mockMvc.perform(
                        get("/api/v1/identity/memberships/{id}/roles", membershipId)
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("00000000-0000-0000-0000-000000000002"));
    }

    private JsonNode createCustomer(String token, String externalId, String displayName)
            throws Exception {
        return read(
                post("/api/v1/customers")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                json.writeValueAsString(
                                        java.util.Map.of(
                                                "externalId", externalId,
                                                "customerType", "COMPANY",
                                                "displayName", displayName,
                                                "companyName", displayName))),
                201);
    }

    private TenantSession register(String prefix) throws Exception {
        String slug = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        String email = UUID.randomUUID() + "@example.test";
        String password = "StrongPassword123!";
        JsonNode response =
                read(
                        post("/api/v1/auth/tenants/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                java.util.Map.of(
                                                        "slug", slug,
                                                        "companyName", "Frontend Blocker Test",
                                                        "email", email,
                                                        "password", password))),
                        201);
        return new TenantSession(slug, email, password, response.get("accessToken").asText());
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

    private record TenantSession(String slug, String email, String password, String accessToken) {}
}
