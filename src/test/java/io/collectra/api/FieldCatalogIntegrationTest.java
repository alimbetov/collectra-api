package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class FieldCatalogIntegrationTest extends AbstractIntegrationTest {
    private static final UUID SYSTEM_FIELD =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void combinesSystemAndCurrentTenantFieldsForTemplateEditor() throws Exception {
        String first = register("field-first");
        String second = register("field-second");
        JsonNode created = create(first, "custom.invoice.purchase_order", "Purchase order");

        JsonNode firstCatalog = read(get("/api/v1/templates/fields")
                .header("Authorization", bearer(first)));
        assertThat(firstCatalog.findValuesAsText("key"))
                .contains("document.number", "customer.name", "custom.invoice.purchase_order");
        assertThat(firstCatalog.findValuesAsText("placeholder"))
                .contains("{{custom.invoice.purchase_order}}");
        assertThat(created.get("system").asBoolean()).isFalse();

        JsonNode secondCatalog = read(get("/api/v1/templates/fields")
                .header("Authorization", bearer(second)));
        assertThat(secondCatalog.findValuesAsText("key"))
                .contains("document.number", "customer.name")
                .doesNotContain("custom.invoice.purchase_order");

        mockMvc.perform(put("/api/v1/templates/fields/{id}", created.get("id").asText())
                        .header("Authorization", bearer(second))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Foreign update")))
                .andExpect(status().isNotFound());
    }

    @Test
    void customFieldCanBeUpdatedAndArchivedButSystemFieldIsImmutable() throws Exception {
        String token = register("field-lifecycle");
        JsonNode created = create(token, "custom.invoice.region", "Region");
        JsonNode updated = read(put("/api/v1/templates/fields/{id}", created.get("id").asText())
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Sales region")));
        assertThat(updated.get("label").asText()).isEqualTo("Sales region");

        mockMvc.perform(put("/api/v1/templates/fields/{id}", SYSTEM_FIELD)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Changed")))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/templates/fields/{id}", SYSTEM_FIELD)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/templates/fields/{id}", created.get("id").asText())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        JsonNode catalog = read(get("/api/v1/templates/fields")
                .header("Authorization", bearer(token)));
        assertThat(catalog.findValuesAsText("key")).doesNotContain("custom.invoice.region");
    }

    private JsonNode create(String token, String key, String label) throws Exception {
        return read(post("/api/v1/templates/fields")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"" + key + "\",\"label\":\"" + label
                        + "\",\"dataType\":\"STRING\",\"category\":\"invoice\","
                        + "\"description\":\"Imported value\",\"validationRules\":{}}"));
    }

    private String updateBody(String label) {
        return "{\"label\":\"" + label
                + "\",\"dataType\":\"STRING\",\"category\":\"invoice\","
                + "\"description\":\"Updated value\",\"validationRules\":{}}";
    }

    private String register(String prefix) throws Exception {
        JsonNode response = read(post("/api/v1/auth/tenants/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"" + prefix + "-" + UUID.randomUUID()
                        + "\",\"companyName\":\"Company\",\"email\":\"" + UUID.randomUUID()
                        + "@example.test\",\"password\":\"StrongPassword123!\"}"));
        return response.get("accessToken").asText();
    }

    private JsonNode read(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String body = mockMvc.perform(request).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private String bearer(String token) { return "Bearer " + token; }
}
