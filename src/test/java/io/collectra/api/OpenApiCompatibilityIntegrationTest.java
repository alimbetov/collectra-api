package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.openapitools.openapidiff.core.OpenApiCompare;
import org.openapitools.openapidiff.core.model.ChangedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class OpenApiCompatibilityIntegrationTest extends AbstractIntegrationTest {

    private static final Set<String> MONEY_PROPERTIES =
            Set.of(
                    "PaymentRequest.amount",
                    "PaymentResponse.amount",
                    "AllocationResponse.amount",
                    "AllocationRequest.amount",
                    "InvoiceRequest.originalAmount",
                    "InvoiceResponse.originalAmount",
                    "InvoiceResponse.paidAmount",
                    "InvoiceResponse.outstandingAmount",
                    "PromiseCreateRequest.amount",
                    "PromiseResponse.amount",
                    "CampaignSelection.amountFrom",
                    "CampaignSelection.amountTo",
                    "PaymentItem.amount",
                    "InvoiceItem.originalAmount",
                    "InvoiceItem.paidAmount",
                    "InvoiceItem.outstandingAmount",
                    "CurrencyTotal.amount",
                    "Aging.current",
                    "Aging.days1To30",
                    "Aging.days31To60",
                    "Aging.days61To90",
                    "Aging.days90Plus",
                    "CurrencyReceivables.outstanding",
                    "CaseItem.outstandingAmount");

    private static final Path BASELINE =
            Path.of("src/test/resources/openapi/collectra-api-v1-baseline.json");
    private static final Path CURRENT = Path.of("target/openapi/collectra-api-v1-current.json");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void publicApiRemainsBackwardCompatibleWithReviewedBaseline() throws Exception {
        String current = currentPublicApi();
        Files.createDirectories(CURRENT.getParent());
        Files.writeString(CURRENT, current, StandardCharsets.UTF_8);

        if (Boolean.getBoolean("collectra.openapi.update-baseline")) {
            Files.createDirectories(BASELINE.getParent());
            Files.writeString(BASELINE, current, StandardCharsets.UTF_8);
            return;
        }

        assertThat(BASELINE)
                .as("OpenAPI baseline is required; use scripts/update-openapi-baseline.sh")
                .exists();
        String baseline = Files.readString(BASELINE, StandardCharsets.UTF_8);
        ChangedOpenApi changes = OpenApiCompare.fromContents(baseline, current);

        assertThat(changes.isCompatible())
                .as("Breaking /api/v1 change detected:%n%s", changes)
                .isTrue();
    }

    @Test
    void everyPublicMoneyPropertyAndFilterUsesDecimalStringSchema() throws Exception {
        JsonNode document = objectMapper.readTree(currentPublicApi());
        JsonNode schemas = document.path("components").path("schemas");

        for (String field : MONEY_PROPERTIES) {
            String[] parts = field.split("\\.", 2);
            JsonNode schema = schemas.path(parts[0]).path("properties").path(parts[1]);
            assertThat(schema.path("type").asText()).as(field).isEqualTo("string");
            assertThat(schema.path("pattern").asText()).as(field).isNotBlank();
        }

        assertDecimalQueryParameters(document, "/api/v1/invoices", 4);
        assertDecimalQueryParameters(document, "/api/v1/payments", 2);
    }

    @Test
    void mutableCustomerContractsRequireExplicitVersion() throws Exception {
        JsonNode document = objectMapper.readTree(currentPublicApi());
        JsonNode schemas = document.path("components").path("schemas");

        assertRequiredVersion(schemas, "CustomerUpdateRequest");
        assertRequiredVersion(schemas, "CustomerStatusRequest");
        assertRequiredVersion(schemas, "ContactPatchRequest");
        assertRequiredVersion(schemas, "CustomerSegmentUpdateRequest");

        assertThat(
                        document.path("paths")
                                .path("/api/v1/customers/{id}/status")
                                .path("patch")
                                .path("requestBody")
                                .path("content")
                                .path("application/json")
                                .path("schema")
                                .path("$ref")
                                .asText())
                .endsWith("/CustomerStatusRequest");
        assertThat(
                        document.path("paths")
                                .path("/api/v1/customer-segments/{segmentId}")
                                .path("patch")
                                .path("requestBody")
                                .path("content")
                                .path("application/json")
                                .path("schema")
                                .path("$ref")
                                .asText())
                .endsWith("/CustomerSegmentUpdateRequest");
    }

    private void assertRequiredVersion(JsonNode schemas, String schemaName) {
        JsonNode schema = schemas.path(schemaName);
        assertThat(schema.path("properties").path("version").path("type").asText())
                .as(schemaName)
                .isEqualTo("integer");
        List<String> required = new ArrayList<>();
        schema.path("required").forEach(value -> required.add(value.asText()));
        assertThat(required).as(schemaName).contains("version");
    }

    private void assertDecimalQueryParameters(JsonNode document, String path, int expected) {
        long count =
                document
                        .path("paths")
                        .path(path)
                        .path("get")
                        .path("parameters")
                        .findValues("name")
                        .stream()
                        .filter(JsonNode::isTextual)
                        .map(JsonNode::asText)
                        .filter(
                                name ->
                                        name.equals("amountMin")
                                                || name.equals("amountMax")
                                                || name.equals("outstandingMin")
                                                || name.equals("outstandingMax"))
                        .count();
        assertThat(count).isEqualTo(expected);

        document.path("paths")
                .path(path)
                .path("get")
                .path("parameters")
                .forEach(
                        parameter -> {
                            String name = parameter.path("name").asText();
                            if (name.equals("amountMin")
                                    || name.equals("amountMax")
                                    || name.equals("outstandingMin")
                                    || name.equals("outstandingMax")) {
                                assertThat(parameter.path("schema").path("type").asText())
                                        .as(path + " " + name)
                                        .isEqualTo("string");
                                assertThat(parameter.path("schema").path("pattern").asText())
                                        .as(path + " " + name)
                                        .isNotBlank();
                            }
                        });
    }

    private String currentPublicApi() throws Exception {
        String raw =
                mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        ObjectNode document = (ObjectNode) objectMapper.readTree(raw);
        ObjectNode paths = document.withObject("/paths");
        List<String> nonPublicPaths = new ArrayList<>();
        paths.fieldNames()
                .forEachRemaining(
                        path -> {
                            if (!path.startsWith("/api/v1")) {
                                nonPublicPaths.add(path);
                            }
                        });
        nonPublicPaths.forEach(paths::remove);
        document.remove("servers");
        sortRecursively(document);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(document) + "\n";
    }

    private void sortRecursively(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            names.forEach(name -> sortRecursively(object.get(name)));
            object.retain(names.stream().sorted().toList());
        } else if (node.isArray()) {
            node.forEach(this::sortRecursively);
        }
    }
}
