package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class CustomerFrontendApiIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void customerDetailResolvesTenantScopedManagerAndSortedSegmentSummaries() throws Exception {
        String token = register("customer-detail");
        String foreignToken = register("customer-detail-foreign");
        JsonNode currentUser =
                read(get("/api/v1/identity/me").header("Authorization", bearer(token)), 200);
        JsonNode foreignCustomer =
                createCustomer(foreignToken, "EXT-FOREIGN-DETAIL", "Foreign detail");
        JsonNode customer =
                read(
                        post("/api/v1/customers")
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"externalId\":\"EXT-DETAIL\","
                                                + "\"customerType\":\"COMPANY\","
                                                + "\"displayName\":\"Detail Company\","
                                                + "\"companyName\":\"Detail Company\","
                                                + "\"managerUserId\":\""
                                                + currentUser.get("id").asText()
                                                + "\"}"),
                        201);
        JsonNode zeta = createSegment(token, "ZETA", "zeta");
        JsonNode alpha = createSegment(token, "ALPHA", "Alpha");
        for (JsonNode segment : java.util.List.of(zeta, alpha)) {
            mockMvc.perform(
                            post(
                                            "/api/v1/customers/{customerId}/segments/{segmentId}",
                                            customer.get("id").asText(),
                                            segment.get("id").asText())
                                    .header("Authorization", bearer(token)))
                    .andExpect(status().isNoContent());
        }

        String managerLabel =
                currentUser.hasNonNull("displayName")
                        ? currentUser.get("displayName").asText()
                        : currentUser.get("email").asText();
        mockMvc.perform(
                        get("/api/v1/customers/{id}", customer.get("id").asText())
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerDisplayName").value(managerLabel))
                .andExpect(jsonPath("$.segmentIds.length()").value(2))
                .andExpect(jsonPath("$.segments.length()").value(2))
                .andExpect(jsonPath("$.segments[0].name").value("Alpha"))
                .andExpect(jsonPath("$.segments[1].name").value("zeta"));

        mockMvc.perform(
                        get("/api/v1/customers/{id}", foreignCustomer.get("id").asText())
                                .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void customerListIsPagedFilteredAndTenantScoped() throws Exception {
        String tenantAToken = register("customer-a");
        String tenantBToken = register("customer-b");

        JsonNode alpha = createCustomer(tenantAToken, "EXT-ALPHA", "Alpha Company");
        createCustomer(tenantAToken, "EXT-BETA", "Beta Company");
        createCustomer(tenantBToken, "EXT-FOREIGN", "Foreign Company");

        JsonNode segment = createSegment(tenantAToken, "VIP", "VIP customers");
        mockMvc.perform(
                        post(
                                        "/api/v1/customers/{customerId}/segments/{segmentId}",
                                        alpha.get("id").asText(),
                                        segment.get("id").asText())
                                .header("Authorization", bearer(tenantAToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        post("/api/v1/customers/{id}/emails", alpha.get("id").asText())
                                .header("Authorization", bearer(tenantAToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"Alpha@Example.Test\",\"type\":\"work\",\"primary\":true}"))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        get("/api/v1/customers")
                                .header("Authorization", bearer(tenantAToken))
                                .param("page", "0")
                                .param("size", "1")
                                .param("sort", "displayName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].displayName").value("Alpha Company"));

        mockMvc.perform(
                        get("/api/v1/customers")
                                .header("Authorization", bearer(tenantAToken))
                                .param("search", "ALPHA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].externalId").value("EXT-ALPHA"));

        mockMvc.perform(
                        get("/api/v1/customers")
                                .header("Authorization", bearer(tenantAToken))
                                .param("externalId", "EXT-ALPHA")
                                .param("email", "alpha@example.test")
                                .param("segmentId", segment.get("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].externalId").value("EXT-ALPHA"));

        mockMvc.perform(
                        get("/api/v1/customers")
                                .header("Authorization", bearer(tenantAToken))
                                .param("externalId", "EXT-FOREIGN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void customerManagerMustBeAnActiveMemberOfTheSameTenant() throws Exception {
        String token = register("customer-manager");
        String foreignToken = register("customer-manager-foreign");
        JsonNode currentUser =
                read(get("/api/v1/identity/me").header("Authorization", bearer(token)), 200);
        JsonNode foreignUser =
                read(get("/api/v1/identity/me").header("Authorization", bearer(foreignToken)), 200);

        mockMvc.perform(
                        post("/api/v1/customers")
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"externalId\":\"EXT-FOREIGN-MANAGER\","
                                                + "\"customerType\":\"COMPANY\","
                                                + "\"displayName\":\"Foreign manager\","
                                                + "\"managerUserId\":\""
                                                + foreignUser.get("id").asText()
                                                + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_MANAGER"));

        mockMvc.perform(
                        post("/api/v1/customers")
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"externalId\":\"EXT-LOCAL-MANAGER\","
                                                + "\"customerType\":\"COMPANY\","
                                                + "\"displayName\":\"Local manager\","
                                                + "\"managerUserId\":\""
                                                + currentUser.get("id").asText()
                                                + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.managerUserId").value(currentUser.get("id").asText()));
    }

    @Test
    void primaryContactsAreSwitchedAtomicallyAndCanBeDeactivated() throws Exception {
        String token = register("customer-primary");
        JsonNode customer = createCustomer(token, "EXT-CONTACT", "Contact Company");
        String customerId = customer.get("id").asText();

        JsonNode first =
                read(
                        post("/api/v1/customers/{id}/emails", customerId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"first@example.test\",\"type\":\"WORK\",\"primary\":true}"),
                        201);
        JsonNode second =
                read(
                        post("/api/v1/customers/{id}/emails", customerId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"second@example.test\",\"type\":\"WORK\",\"primary\":true}"),
                        201);

        JsonNode emails =
                read(
                        get("/api/v1/customers/{id}/emails", customerId)
                                .header("Authorization", bearer(token)),
                        200);
        assertThat(primary(emails, first.get("id").asText())).isFalse();
        assertThat(primary(emails, second.get("id").asText())).isTrue();

        mockMvc.perform(
                        patch(
                                        "/api/v1/customers/{id}/emails/{emailId}",
                                        customerId,
                                        second.get("id").asText())
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"status\":\"INACTIVE\",\"version\":"
                                                + second.get("version").asLong()
                                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.primary").value(false));
    }

    @Test
    void staleCustomerContactAndSegmentCommandsFailWithoutSideEffects() throws Exception {
        String token = register("customer-version");
        JsonNode customer = createCustomer(token, "EXT-VERSION", "Version Company");
        String customerId = customer.get("id").asText();

        JsonNode updated =
                read(
                        put("/api/v1/customers/{id}", customerId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"displayName\":\"Updated"
                                                + " Company\",\"companyName\":\"Updated"
                                                + " Company\",\"version\":0}"),
                        200);

        mockMvc.perform(
                        put("/api/v1/customers/{id}", customerId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"displayName\":\"Stale Company\",\"companyName\":\"Stale"
                                                + " Company\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        mockMvc.perform(
                        patch("/api/v1/customers/{id}/status", customerId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.version").exists());

        read(
                patch("/api/v1/customers/{id}/status", customerId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"status\":\"BLOCKED\",\"version\":"
                                        + updated.get("version").asLong()
                                        + "}"),
                200);

        JsonNode first =
                read(
                        post("/api/v1/customers/{id}/emails", customerId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"version-first@example.test\",\"primary\":true}"),
                        201);
        JsonNode second =
                read(
                        post("/api/v1/customers/{id}/emails", customerId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"version-second@example.test\",\"primary\":true}"),
                        201);

        mockMvc.perform(
                        patch(
                                        "/api/v1/customers/{id}/emails/{emailId}",
                                        customerId,
                                        first.get("id").asText())
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"primary\":true,\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        JsonNode emails =
                read(
                        get("/api/v1/customers/{id}/emails", customerId)
                                .header("Authorization", bearer(token)),
                        200);
        assertThat(primary(emails, first.get("id").asText())).isFalse();
        assertThat(primary(emails, second.get("id").asText())).isTrue();

        JsonNode phone =
                read(
                        post("/api/v1/customers/{id}/phones", customerId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"phone\":\"+77010000001\",\"primary\":true}"),
                        201);
        read(
                patch(
                                "/api/v1/customers/{id}/phones/{phoneId}",
                                customerId,
                                phone.get("id").asText())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\",\"version\":0}"),
                200);
        mockMvc.perform(
                        patch(
                                        "/api/v1/customers/{id}/phones/{phoneId}",
                                        customerId,
                                        phone.get("id").asText())
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"ACTIVE\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        JsonNode segment = createSegment(token, "VERSIONED", "Versioned Segment");
        String segmentId = segment.get("id").asText();
        read(
                patch("/api/v1/customer-segments/{id}", segmentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Segment\",\"version\":0}"),
                200);
        mockMvc.perform(
                        patch("/api/v1/customer-segments/{id}", segmentId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Stale Segment\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void segmentMembershipCommandsAreIdempotentAndForeignSegmentIsHidden() throws Exception {
        String token = register("customer-segment");
        String foreignToken = register("customer-segment-foreign");
        JsonNode customer = createCustomer(token, "EXT-SEGMENT", "Segment Company");
        JsonNode segment = createSegment(token, "PRIORITY", "Priority");
        JsonNode foreignSegment = createSegment(foreignToken, "FOREIGN", "Foreign");

        String customerId = customer.get("id").asText();
        String segmentId = segment.get("id").asText();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(
                            post(
                                            "/api/v1/customers/{customerId}/segments/{segmentId}",
                                            customerId,
                                            segmentId)
                                    .header("Authorization", bearer(token)))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(
                        get("/api/v1/customers")
                                .header("Authorization", bearer(token))
                                .param("segmentId", segmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(
                        get("/api/v1/customer-segments")
                                .header("Authorization", bearer(token))
                                .param("search", "priority"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(segmentId));

        JsonNode inactiveSegment =
                read(
                        patch("/api/v1/customer-segments/{id}", segmentId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Priority\",\"active\":false,\"version\":"
                                                + segment.get("version").asLong()
                                                + "}"),
                        200);
        assertThat(inactiveSegment.get("active").asBoolean()).isFalse();

        // Repeating an already satisfied assignment remains idempotent after deactivation.
        mockMvc.perform(
                        post(
                                        "/api/v1/customers/{customerId}/segments/{segmentId}",
                                        customerId,
                                        segmentId)
                                .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        JsonNode anotherCustomer = createCustomer(token, "EXT-SEGMENT-NEW", "New Segment Company");
        mockMvc.perform(
                        post(
                                        "/api/v1/customers/{customerId}/segments/{segmentId}",
                                        anotherCustomer.get("id").asText(),
                                        segmentId)
                                .header("Authorization", bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INACTIVE_SEGMENT"));

        mockMvc.perform(
                        get("/api/v1/customers/{id}", customerId)
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segmentIds[0]").value(segmentId))
                .andExpect(jsonPath("$.segments[0].active").value(false));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(
                            delete(
                                            "/api/v1/customers/{customerId}/segments/{segmentId}",
                                            customerId,
                                            segmentId)
                                    .header("Authorization", bearer(token)))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(
                        delete(
                                        "/api/v1/customers/{customerId}/segments/{segmentId}",
                                        customerId,
                                        foreignSegment.get("id").asText())
                                .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void invalidPagingAndSortingReturnMachineReadableBadRequest() throws Exception {
        String token = register("customer-invalid");

        mockMvc.perform(
                        get("/api/v1/customers")
                                .header("Authorization", bearer(token))
                                .param("size", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(
                        get("/api/v1/customers")
                                .header("Authorization", bearer(token))
                                .param("sort", "passwordHash,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
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

    private JsonNode createSegment(String token, String code, String name) throws Exception {
        return read(
                post("/api/v1/customer-segments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"code\":\""
                                        + code
                                        + "-"
                                        + UUID.randomUUID().toString().substring(0, 8)
                                        + "\",\"name\":\""
                                        + name
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
                                                + "\",\"companyName\":\"Customer API Test\","
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

    private boolean primary(JsonNode array, String id) {
        for (JsonNode value : array) {
            if (id.equals(value.get("id").asText())) {
                return value.get("primary").asBoolean();
            }
        }
        throw new AssertionError("Contact not found: " + id);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
