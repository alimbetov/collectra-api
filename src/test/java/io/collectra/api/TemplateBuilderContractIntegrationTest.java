package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class TemplateBuilderContractIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void exposesPagedFieldCatalogAndBinaryPdfPreview() throws Exception {
        String token = register("builder-contract");

        JsonNode page =
                read(
                        get("/api/v1/template-builder/catalog/fields")
                                .header("Authorization", bearer(token))
                                .queryParam("page", "0")
                                .queryParam("size", "2"));

        assertThat(page.get("items").size()).isLessThanOrEqualTo(2);
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isEqualTo(2);
        assertThat(page.get("totalElements").asLong()).isGreaterThan(2);

        byte[] pdf =
                mockMvc.perform(
                                post("/api/v1/template-builder/documents/preview-pdf")
                                        .header("Authorization", bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "draft": {
                                                    "channel": "PDF",
                                                    "locale": "ru",
                                                    "builderJson": {
                                                      "version": "1.0",
                                                      "blocks": [
                                                        {
                                                          "type": "richText",
                                                          "props": {
                                                            "content": [
                                                              {"type":"text","value":"Клиент: "},
                                                              {"type":"placeholder","key":"customer.name"}
                                                            ]
                                                          }
                                                        }
                                                      ]
                                                    }
                                                  },
                                                  "payload": {
                                                    "customer": {"name":"ACME"}
                                                  }
                                                }
                                                """))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();

        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
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
                                                + "\",\"companyName\":\"Company\",\"email\":\""
                                                + UUID.randomUUID()
                                                + "@example.test\",\"password\":\"StrongPassword123!\"}"));
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
