package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
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
class TemplateStudioContractIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void exposesBoundedBuilderCapabilitiesAndFieldMetadata() throws Exception {
        String token = register("studio-contract");

        JsonNode capabilities =
                read(
                        get("/api/v1/template-builder/capabilities")
                                .header("Authorization", bearer(token)));

        assertThat(capabilities.get("builderSchemaVersion").asText()).isEqualTo("1.0");
        assertThat(capabilities.get("channels"))
                .extracting(JsonNode::asText)
                .contains("EMAIL", "PDF", "SMS", "WHATSAPP", "TELEGRAM");

        JsonNode fields =
                read(
                        get("/api/v1/template-builder/catalog/fields")
                                .queryParam("page", "0")
                                .queryParam("size", "2")
                                .header("Authorization", bearer(token)));

        assertThat(fields.get("items").size()).isLessThanOrEqualTo(2);
        assertThat(fields.get("totalElements").asLong()).isPositive();
        JsonNode first = fields.get("items").get(0);
        assertThat(first.has("description")).isTrue();
        assertThat(first.has("exampleValue")).isTrue();
        assertThat(first.has("validationRules")).isTrue();
    }

    @Test
    void logicalTemplateRenameRequiresRevisionAndRejectsStaleWrite() throws Exception {
        String token = register("studio-revision");
        JsonNode created =
                read(
                        post("/api/v1/templates")
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "code": "PAYMENT_REMINDER",
                                          "name": "Payment reminder",
                                          "documentType": "NOTIFICATION"
                                        }
                                        """));

        String id = created.get("id").asText();
        long revision = created.get("revision").asLong();

        mockMvc.perform(
                        put("/api/v1/templates/{id}", id)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"name":"Renamed"}
                                        """))
                .andExpect(status().isBadRequest());

        read(
                put("/api/v1/templates/{id}", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"name":"Renamed","revision":%d}
                                """
                                        .formatted(revision)));

        mockMvc.perform(
                        put("/api/v1/templates/{id}", id)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"name":"Stale","revision":%d}
                                        """
                                                .formatted(revision)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void savedBuilderUpdateRequiresRevision() throws Exception {
        String token = register("studio-builder");
        JsonNode template =
                read(
                        post("/api/v1/templates")
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "code": "EMAIL_NOTICE",
                                          "name": "Email notice",
                                          "documentType": "NOTIFICATION"
                                        }
                                        """));

        JsonNode version =
                read(
                        post(
                                        "/api/v1/template-builder/templates/{templateId}/versions/builder",
                                        template.get("id").asText())
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "channel": "EMAIL",
                                          "locale": "ru",
                                          "subject": "Hello",
                                          "builderJson": {
                                            "version": "1.0",
                                            "blocks": [
                                              {
                                                "type": "richText",
                                                "props": {
                                                  "content": [
                                                    {"type":"text","value":"Hello"}
                                                  ]
                                                }
                                              }
                                            ]
                                          }
                                        }
                                        """));

        mockMvc.perform(
                        put(
                                        "/api/v1/template-builder/versions/{versionId}/builder",
                                        version.get("id").asText())
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "channel": "EMAIL",
                                          "locale": "ru",
                                          "subject": "Updated",
                                          "builderJson": {
                                            "version": "1.0",
                                            "blocks": [
                                              {
                                                "type": "richText",
                                                "props": {
                                                  "content": [
                                                    {"type":"text","value":"Updated"}
                                                  ]
                                                }
                                              }
                                            ]
                                          }
                                        }
                                        """))
                .andExpect(status().isBadRequest());
    }

    private String register(String prefix) throws Exception {
        String slug = prefix + "-" + UUID.randomUUID();
        String email = UUID.randomUUID() + "@example.test";
        JsonNode response =
                read(
                        post("/api/v1/auth/tenants/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "slug": "%s",
                                          "companyName": "Company",
                                          "email": "%s",
                                          "password": "StrongPassword123!"
                                        }
                                        """
                                                .formatted(slug, email)));
        return response.get("accessToken").asText();
    }

    private JsonNode read(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String body =
                mockMvc.perform(request)
                        .andExpect(status().is2xxSuccessful())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
