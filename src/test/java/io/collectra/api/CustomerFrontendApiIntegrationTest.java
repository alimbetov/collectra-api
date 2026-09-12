package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class CustomerFrontendApiIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

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
                                .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.primary").value(false));
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
