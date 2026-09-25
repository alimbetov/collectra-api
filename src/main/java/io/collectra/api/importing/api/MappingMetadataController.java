package io.collectra.api.importing.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backend-owned metadata used by Mapping Studio.
 *
 * Keep this contract aligned with MappingExecutionService. The frontend must not
 * hardcode transformation names or parameter shapes.
 */
@RestController
@RequestMapping("/api/v1/mapping-metadata")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class MappingMetadataController {

    @GetMapping("/transformations")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('MAPPING_PROFILE_READ')")
    List<TransformationResponse> transformations() {
        return List.of(
                transformation("NONE", Set.of(), object()),
                transformation("TRIM", Set.of("STRING"), object()),
                transformation("UPPERCASE", Set.of("STRING"), object()),
                transformation("LOWERCASE", Set.of("STRING"), object()),
                transformation("DATE_PARSE", Set.of("DATE"), object(
                        property("pattern", "string", "dd.MM.yyyy"))),
                transformation("DECIMAL_PARSE", Set.of("DECIMAL"), object(
                        property("decimalSeparator", "string", ","),
                        property("groupingSeparator", "string", " "))),
                transformation("BOOLEAN_PARSE", Set.of("BOOLEAN"), object(
                        property("trueValue", "string", "true"),
                        property("falseValue", "string", "false"))),
                transformation("NORMALIZE_PHONE", Set.of("STRING"), object()),
                transformation("VALIDATE_EMAIL", Set.of("STRING"), object()),
                transformation("SPLIT", Set.of(), object(
                        property("delimiterRegex", "string", "[,;]"))),
                transformation("CHANNELS_PARSE", Set.of("STRING"), object(
                        property("delimiterRegex", "string", "[,;]"))));
    }

    private TransformationResponse transformation(
            String code, Set<String> recommendedTargetTypes, JsonNode parameterSchema) {
        return new TransformationResponse(code, recommendedTargetTypes, parameterSchema);
    }

    private JsonNode object(Property... properties) {
        var root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        var values = root.putObject("properties");
        for (Property property : properties) {
            var node = values.putObject(property.name());
            node.put("type", property.type());
            node.put("default", property.defaultValue());
        }
        root.put("additionalProperties", false);
        return root;
    }

    private Property property(String name, String type, String defaultValue) {
        return new Property(name, type, defaultValue);
    }

    record TransformationResponse(
            String code, Set<String> recommendedTargetTypes, JsonNode parameterSchema) {}

    private record Property(String name, String type, String defaultValue) {}
}
