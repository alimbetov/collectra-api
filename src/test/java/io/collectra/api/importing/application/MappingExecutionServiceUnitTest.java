package io.collectra.api.importing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.collectra.api.importing.domain.MappingProfile;
import io.collectra.api.importing.domain.MappingRule;
import io.collectra.api.importing.domain.SourceField;
import io.collectra.api.importing.infrastructure.MappingProfileRepository;
import io.collectra.api.importing.infrastructure.MappingRuleRepository;
import io.collectra.api.importing.infrastructure.SourceFieldRepository;
import io.collectra.api.importing.infrastructure.SourceSchemaRepository;
import io.collectra.api.template.domain.FieldDataType;
import io.collectra.api.template.domain.FieldDefinition;
import io.collectra.api.template.infrastructure.FieldDefinitionRepository;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class MappingExecutionServiceUnitTest {
    private final ObjectMapper json = new ObjectMapper();
    private final FieldDefinitionRepository targets = mock(FieldDefinitionRepository.class);
    private final MappingExecutionService service =
            new MappingExecutionService(
                    mock(MappingProfileRepository.class),
                    mock(MappingRuleRepository.class),
                    mock(SourceSchemaRepository.class),
                    mock(SourceFieldRepository.class),
                    targets,
                    mock(DocumentInputParser.class),
                    json);

    @Test
    void transformsValuesAndBuildsNestedPayload() throws Exception {
        UUID tenantId = UUID.randomUUID();
        MappingProfile profile =
                new MappingProfile(tenantId, UUID.randomUUID(), "MAP", "Map", "INVOICE", 1);
        SourceField dateSource =
                new SourceField(profile.getSourceSchemaId(), "Date", "STRING", null, true, 1);
        SourceField totalSource =
                new SourceField(profile.getSourceSchemaId(), "Total", "STRING", null, true, 2);
        SourceField tagSource =
                new SourceField(profile.getSourceSchemaId(), "Tags", "STRING", null, false, 3);
        FieldDefinition date = field(null, "document.date", FieldDataType.DATE, false, true);
        FieldDefinition total = field(null, "invoice.total", FieldDataType.DECIMAL, false, true);
        FieldDefinition tags =
                field(tenantId, "custom.invoice.tags", FieldDataType.STRING, true, false);
        List<MappingRule> rules =
                List.of(
                        rule(
                                profile,
                                dateSource,
                                date,
                                "{\"type\":\"DATE_PARSE\",\"pattern\":\"dd.MM.yyyy\"}"),
                        rule(
                                profile,
                                totalSource,
                                total,
                                "{\"type\":\"DECIMAL_PARSE\",\"decimalSeparator\":\",\"}"),
                        rule(profile, tagSource, tags, "{\"type\":\"TRIM\"}"));
        when(targets.findAllById(anyCollection())).thenReturn(List.of(date, total, tags));
        ParsedInput input =
                new ParsedInput(
                        Map.of(
                                "Date", List.of(text("08.09.2026")),
                                "Total", List.of(text("1 250,75")),
                                "Tags", List.of(text(" priority "), text("retail"))));

        var result =
                service.apply(
                        tenantId,
                        profile,
                        input,
                        List.of(dateSource, totalSource, tagSource),
                        rules);

        assertThat(result.normalizedPayload().at("/document/date").asText())
                .isEqualTo("2026-09-08");
        assertThat(result.normalizedPayload().at("/invoice/total").decimalValue())
                .isEqualByComparingTo("1250.75");
        assertThat(result.normalizedPayload().at("/custom/invoice/tags"))
                .extracting(JsonNode::toString)
                .isEqualTo("[\"priority\",\"retail\"]");
    }

    @Test
    void reportsEveryMissingRequiredMapping() {
        UUID tenantId = UUID.randomUUID();
        MappingProfile profile =
                new MappingProfile(tenantId, UUID.randomUUID(), "MAP", "Map", "INVOICE", 1);
        SourceField source =
                new SourceField(profile.getSourceSchemaId(), "Number", "STRING", null, true, 1);
        FieldDefinition target = field(null, "document.number", FieldDataType.STRING, false, true);
        MappingRule rule =
                new MappingRule(
                        profile.getId(),
                        source.getId(),
                        target.getId(),
                        json.createObjectNode(),
                        null,
                        true);
        when(targets.findAllById(anyCollection())).thenReturn(List.of(target));

        assertThatThrownBy(
                        () ->
                                service.apply(
                                        tenantId,
                                        profile,
                                        new ParsedInput(Map.of("Number", List.of())),
                                        List.of(source),
                                        List.of(rule)))
                .isInstanceOfSatisfying(
                        MappingValidationException.class,
                        error ->
                                assertThat(error.getViolations())
                                        .singleElement()
                                        .extracting(MappingValidationException.Violation::targetKey)
                                        .isEqualTo("document.number"));
    }

    private FieldDefinition field(
            UUID tenantId, String key, FieldDataType type, boolean collection, boolean required) {
        return new FieldDefinition(
                tenantId, key, key, type, "TEST", collection, required, json.createObjectNode());
    }

    private MappingRule rule(
            MappingProfile profile,
            SourceField source,
            FieldDefinition target,
            String transformation)
            throws Exception {
        return new MappingRule(
                profile.getId(),
                source.getId(),
                target.getId(),
                json.readTree(transformation),
                null,
                target.isRequired());
    }

    private JsonNode text(String value) {
        return json.getNodeFactory().textNode(value);
    }
}
