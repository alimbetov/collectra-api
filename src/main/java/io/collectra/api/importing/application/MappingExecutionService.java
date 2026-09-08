package io.collectra.api.importing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.collectra.api.importing.domain.MappingProfile;
import io.collectra.api.importing.domain.MappingRule;
import io.collectra.api.importing.domain.SourceField;
import io.collectra.api.importing.domain.SourceSchema;
import io.collectra.api.importing.infrastructure.MappingProfileRepository;
import io.collectra.api.importing.infrastructure.MappingRuleRepository;
import io.collectra.api.importing.infrastructure.SourceFieldRepository;
import io.collectra.api.importing.infrastructure.SourceSchemaRepository;
import io.collectra.api.template.domain.FieldDataType;
import io.collectra.api.template.domain.FieldDefinition;
import io.collectra.api.template.infrastructure.FieldDefinitionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class MappingExecutionService {
    private final MappingProfileRepository profiles;
    private final MappingRuleRepository rules;
    private final SourceSchemaRepository schemas;
    private final SourceFieldRepository sourceFields;
    private final FieldDefinitionRepository targetFields;
    private final DocumentInputParser parser;
    private final ObjectMapper json;

    public MappingExecutionService(
            MappingProfileRepository profiles,
            MappingRuleRepository rules,
            SourceSchemaRepository schemas,
            SourceFieldRepository sourceFields,
            FieldDefinitionRepository targetFields,
            DocumentInputParser parser,
            ObjectMapper json) {
        this.profiles = profiles;
        this.rules = rules;
        this.schemas = schemas;
        this.sourceFields = sourceFields;
        this.targetFields = targetFields;
        this.parser = parser;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public MappingResult execute(UUID tenantId, UUID mappingProfileId, byte[] content) {
        MappingProfile profile =
                profiles.findByIdAndTenantId(mappingProfileId, tenantId)
                        .orElseThrow(() -> new NoSuchElementException("Mapping profile not found"));
        SourceSchema schema =
                schemas.findByIdAndTenantId(profile.getSourceSchemaId(), tenantId)
                        .orElseThrow(() -> new NoSuchElementException("Source schema not found"));
        List<SourceField> sources =
                sourceFields.findAllBySourceSchemaIdOrderByPositionAsc(schema.getId());
        ParsedInput input =
                parser.parse(
                        schema.getSourceFormat(),
                        content,
                        sources.stream().map(SourceField::getSourcePath).toList());
        return apply(
                tenantId,
                profile,
                input,
                sources,
                rules.findAllByMappingProfileId(mappingProfileId));
    }

    MappingResult apply(
            UUID tenantId,
            MappingProfile profile,
            ParsedInput input,
            List<SourceField> sources,
            List<MappingRule> configuredRules) {
        Map<UUID, SourceField> sourceById = new HashMap<>();
        sources.forEach(source -> sourceById.put(source.getId(), source));
        Map<UUID, FieldDefinition> targetById = new HashMap<>();
        targetFields
                .findAllById(configuredRules.stream().map(MappingRule::getTargetFieldId).toList())
                .forEach(field -> targetById.put(field.getId(), field));

        ObjectNode payload = json.createObjectNode();
        List<MappingValidationException.Violation> violations = new ArrayList<>();
        for (MappingRule rule : configuredRules) {
            SourceField source = sourceById.get(rule.getSourceFieldId());
            FieldDefinition target = targetById.get(rule.getTargetFieldId());
            validateDefinition(tenantId, source, target);
            List<JsonNode> values = new ArrayList<>(input.valuesAt(source.getSourcePath()));
            if (values.isEmpty() && rule.getDefaultValue() != null)
                values.add(json.getNodeFactory().textNode(rule.getDefaultValue()));
            values = transform(values, rule.getTransformation(), target, source.getSourcePath());
            values = values.stream().filter(value -> !isEmpty(value)).toList();
            if (values.isEmpty()
                    && (source.isRequired() || rule.isRequired() || target.isRequired())) {
                violations.add(
                        new MappingValidationException.Violation(
                                source.getSourcePath(),
                                target.getKey(),
                                "REQUIRED_VALUE_MISSING",
                                "Required value is missing"));
                continue;
            }
            if (!values.isEmpty()) put(payload, target, values);
        }
        if (!violations.isEmpty()) throw new MappingValidationException(violations);
        return new MappingResult(profile.getId(), profile.getDocumentType(), payload);
    }

    private void validateDefinition(UUID tenantId, SourceField source, FieldDefinition target) {
        if (source == null)
            throw new IllegalStateException("Mapping rule references unknown source field");
        if (target == null || !"ACTIVE".equals(target.getStatus()))
            throw new IllegalStateException("Mapping rule references unavailable target field");
        if (!target.isSystem() && !tenantId.equals(target.getTenantId()))
            throw new IllegalStateException("Mapping rule references field of another tenant");
    }

    private List<JsonNode> transform(
            List<JsonNode> values,
            JsonNode transformation,
            FieldDefinition target,
            String sourcePath) {
        String type =
                transformation == null
                        ? "NONE"
                        : transformation.path("type").asText("NONE").toUpperCase(Locale.ROOT);
        try {
            return values.stream()
                    .map(value -> transformOne(value, type, transformation, target))
                    .toList();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "Cannot map " + sourcePath + " to " + target.getKey(), ex);
        }
    }

    private JsonNode transformOne(
            JsonNode value, String transformationType, JsonNode options, FieldDefinition target) {
        String text = value.isTextual() ? value.asText() : value.toString();
        text =
                switch (transformationType) {
                    case "NONE" -> text;
                    case "TRIM" -> text.trim();
                    case "UPPERCASE" -> text.trim().toUpperCase(Locale.ROOT);
                    case "LOWERCASE" -> text.trim().toLowerCase(Locale.ROOT);
                    case "DATE_PARSE" ->
                            LocalDate.parse(
                                            text.trim(),
                                            DateTimeFormatter.ofPattern(
                                                    options.path("pattern").asText("dd.MM.yyyy")))
                                    .toString();
                    case "DECIMAL_PARSE" -> normalizeDecimal(text, options);
                    case "BOOLEAN_PARSE" -> normalizeBoolean(text, options);
                    default ->
                            throw new IllegalArgumentException(
                                    "Unsupported transformation: " + transformationType);
                };
        return convert(text, target.getDataType());
    }

    private String normalizeDecimal(String value, JsonNode options) {
        String decimalSeparator = options.path("decimalSeparator").asText(",");
        String groupingSeparator = options.path("groupingSeparator").asText(" ");
        return value.trim().replace(groupingSeparator, "").replace(decimalSeparator, ".");
    }

    private String normalizeBoolean(String value, JsonNode options) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        String trueValue = options.path("trueValue").asText("true").toLowerCase(Locale.ROOT);
        String falseValue = options.path("falseValue").asText("false").toLowerCase(Locale.ROOT);
        if (normalized.equals(trueValue)) return "true";
        if (normalized.equals(falseValue)) return "false";
        throw new IllegalArgumentException("Unknown boolean value");
    }

    private JsonNode convert(String value, FieldDataType type) {
        return switch (type) {
            case STRING -> json.getNodeFactory().textNode(value);
            case DECIMAL -> json.getNodeFactory().numberNode(new BigDecimal(value));
            case INTEGER -> json.getNodeFactory().numberNode(Long.parseLong(value));
            case DATE -> json.getNodeFactory().textNode(LocalDate.parse(value).toString());
            case DATETIME -> json.getNodeFactory().textNode(OffsetDateTime.parse(value).toString());
            case BOOLEAN -> json.getNodeFactory().booleanNode(Boolean.parseBoolean(value));
            case OBJECT -> readObject(value);
        };
    }

    private JsonNode readObject(String value) {
        try {
            JsonNode node = json.readTree(value);
            if (!node.isObject()) throw new IllegalArgumentException("Expected JSON object");
            return node;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid JSON object", ex);
        }
    }

    private boolean isEmpty(JsonNode value) {
        return value == null
                || value.isNull()
                || value.isMissingNode()
                || (value.isTextual() && value.asText().isBlank());
    }

    private void put(ObjectNode root, FieldDefinition target, List<JsonNode> values) {
        String[] segments = target.getKey().split("\\.");
        ObjectNode parent = root;
        for (int index = 0; index < segments.length - 1; index++) {
            JsonNode existing = parent.get(segments[index]);
            if (existing != null && !existing.isObject())
                throw new IllegalArgumentException(
                        "Conflicting target field path: " + target.getKey());
            parent = parent.withObject(segments[index]);
        }
        String leaf = segments[segments.length - 1];
        if (target.isCollection()) {
            ArrayNode array = parent.putArray(leaf);
            values.forEach(array::add);
        } else parent.set(leaf, values.get(0));
    }

    public record MappingResult(
            UUID mappingProfileId, String documentType, ObjectNode normalizedPayload) {}
}
