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
import org.junit.jupiter.api.Test;
import org.openapitools.openapidiff.core.OpenApiCompare;
import org.openapitools.openapidiff.core.model.ChangedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class OpenApiCompatibilityIntegrationTest extends AbstractIntegrationTest {

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
