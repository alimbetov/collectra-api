package io.collectra.api.importing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.collectra.api.importing.domain.DefinitionStatus;
import io.collectra.api.importing.domain.MappingProfile;
import io.collectra.api.importing.domain.MappingRule;
import io.collectra.api.importing.domain.SourceField;
import io.collectra.api.importing.domain.SourceSchema;
import io.collectra.api.importing.domain.SourceFieldScope;
import io.collectra.api.importing.domain.FieldValuePolicy;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
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
        return executeInternal(tenantId, mappingProfileId, content, true);
    }

    @Transactional(readOnly = true)
    public BatchMappingResult executeBatch(UUID tenantId, UUID mappingProfileId, byte[] content) {
        MappingProfile profile = profiles.findByIdAndTenantId(mappingProfileId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Mapping profile not found"));
        SourceSchema schema = schemas.findByIdAndTenantId(profile.getSourceSchemaId(), tenantId)
                .orElseThrow(() -> new NoSuchElementException("Source schema not found"));
        if (profile.getStatus() != DefinitionStatus.PUBLISHED
                || schema.getStatus() != DefinitionStatus.PUBLISHED)
            throw new IllegalStateException("Only published definitions can be executed");
        List<SourceField> sources = sourceFields.findAllBySourceSchemaIdOrderByPositionAsc(schema.getId());
        List<MappingRule> configuredRules = rules.findAllByMappingProfileId(mappingProfileId);
        ParsedInput parsed = parser.parse(schema.getSourceFormat(), content,
                sources.stream().map(SourceField::getSourcePath).toList(), schema.getRecordPath());
        return mapRows(tenantId, profile, schema, parsed, sources, configuredRules);
    }

    @Transactional(readOnly = true)
    public MappingResult test(UUID tenantId, UUID mappingProfileId, byte[] content) {
        return executeInternal(tenantId, mappingProfileId, content, false);
    }

    @Transactional(readOnly = true)
    public RuleTestResult testRule(
            UUID tenantId, UUID mappingProfileId, UUID ruleId, JsonNode sourceValue) {
        MappingProfile profile = profiles.findByIdAndTenantId(mappingProfileId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Mapping profile not found"));
        MappingRule rule = rules.findByIdAndMappingProfileId(ruleId, mappingProfileId)
                .orElseThrow(() -> new NoSuchElementException("Mapping rule not found"));
        SourceField source = sourceFields.findById(rule.getSourceFieldId())
                .orElseThrow(() -> new NoSuchElementException("Source field not found"));
        FieldDefinition target = targetFields.findById(rule.getTargetFieldId())
                .orElseThrow(() -> new NoSuchElementException("Target field not found"));
        if (!profile.getSourceSchemaId().equals(source.getSourceSchemaId()))
            throw new IllegalArgumentException("Source field does not belong to mapping schema");
        validateDefinition(tenantId, source, target);
        JsonNode input = sourceValue == null ? json.nullNode() : sourceValue;
        List<JsonNode> transformed = transform(List.of(input), rule.getTransformation(), target,
                source.getSourcePath());
        JsonNode result = transformed.isEmpty() ? json.nullNode() : transformed.get(0);
        String operation = rule.getTransformation() == null
                ? "NONE" : rule.getTransformation().path("type").asText("NONE").toUpperCase(Locale.ROOT);
        return new RuleTestResult(input, result, target.getDataType().name(),
                List.of(new RuleTestStep(operation, result)));
    }

    private MappingResult executeInternal(
            UUID tenantId, UUID mappingProfileId, byte[] content, boolean publishedOnly) {
        MappingProfile profile =
                profiles.findByIdAndTenantId(mappingProfileId, tenantId)
                        .orElseThrow(() -> new NoSuchElementException("Mapping profile not found"));
        SourceSchema schema =
                schemas.findByIdAndTenantId(profile.getSourceSchemaId(), tenantId)
                        .orElseThrow(() -> new NoSuchElementException("Source schema not found"));
        if (publishedOnly && (profile.getStatus() != DefinitionStatus.PUBLISHED
                || schema.getStatus() != DefinitionStatus.PUBLISHED))
            throw new IllegalStateException(
                    "Only published source schemas and mapping profiles can be executed");
        List<SourceField> sources =
                sourceFields.findAllBySourceSchemaIdOrderByPositionAsc(schema.getId());
        ParsedInput input =
                parser.parse(
                        schema.getSourceFormat(),
                        content,
                        sources.stream().map(SourceField::getSourcePath).toList(),
                        schema.getRecordPath());
        List<MappingRule> configuredRules = rules.findAllByMappingProfileId(mappingProfileId);
        MappingResult result = apply(
                tenantId,
                profile,
                input,
                sources,
                configuredRules);
        return new MappingResult(result.mappingProfileId(), result.sourceSchemaVersionId(),
                result.documentType(), result.normalizedPayload(), configHash(profile, configuredRules));
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
        java.util.Set<UUID> mappedSources =
                configuredRules.stream()
                        .map(MappingRule::getSourceFieldId)
                        .collect(java.util.stream.Collectors.toSet());
        sources.stream()
                .filter(SourceField::isRequired)
                .filter(source -> !mappedSources.contains(source.getId()))
                .filter(
                        source ->
                                input.valuesAt(source.getSourcePath()).stream()
                                        .allMatch(this::isEmpty))
                .forEach(
                        source ->
                                violations.add(
                                        new MappingValidationException.Violation(
                                                source.getSourcePath(),
                                                null,
                                                "REQUIRED_SOURCE_MISSING",
                                                "Required source value is missing")));
        for (MappingRule rule : configuredRules) {
            SourceField source = sourceById.get(rule.getSourceFieldId());
            FieldDefinition target = targetById.get(rule.getTargetFieldId());
            validateDefinition(tenantId, source, target);
            List<JsonNode> values = new ArrayList<>(input.valuesAt(source.getSourcePath()));
            if (source.isRequired() && values.stream().allMatch(this::isEmpty)) {
                violations.add(
                        new MappingValidationException.Violation(
                                source.getSourcePath(),
                                target.getKey(),
                                "REQUIRED_SOURCE_MISSING",
                                "Required source value is missing"));
                continue;
            }
            if (values.isEmpty() && rule.getDefaultValue() != null)
                values.add(json.getNodeFactory().textNode(rule.getDefaultValue()));
            values = transform(values, rule.getTransformation(), target, source.getSourcePath());
            values = values.stream().filter(value -> !isEmpty(value)).toList();
            if (values.isEmpty() && (rule.isRequired() || target.isRequired())) {
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
        return new MappingResult(profile.getId(), profile.getSourceSchemaId(), profile.getDocumentType(), payload, null);
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
            if ("SPLIT".equals(type) || "CHANNELS_PARSE".equals(type)) {
                String delimiter = transformation == null ? "[,;]"
                        : transformation.path("delimiterRegex").asText("[,;]");
                List<JsonNode> expanded = new ArrayList<>();
                for (JsonNode value : values) {
                    if (value == null || value.isNull()) continue;
                    String[] parts = value.asText().split(delimiter);
                    for (String part : parts) {
                        String normalized = part.trim();
                        if (normalized.isEmpty()) continue;
                        if ("CHANNELS_PARSE".equals(type)) {
                            normalized = normalized.toUpperCase(Locale.ROOT);
                            try { ChannelType.valueOf(normalized); }
                            catch (IllegalArgumentException ex) {
                                throw new IllegalArgumentException("Unknown channel: " + normalized);
                            }
                        }
                        expanded.add(convert(normalized, target.getDataType()));
                    }
                }
                return expanded.stream().distinct().toList();
            }
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
                    case "NORMALIZE_PHONE" -> normalizePhone(text);
                    case "VALIDATE_EMAIL" -> validateEmail(text);
                    default ->
                            throw new IllegalArgumentException(
                                    "Unsupported transformation: " + transformationType);
                };
        return convert(text, target.getDataType());
    }

    private String normalizePhone(String value) {
        String normalized = value.trim().replaceAll("[\\s()-]", "");
        if (normalized.startsWith("8") && normalized.length() == 11)
            normalized = "+7" + normalized.substring(1);
        if (!normalized.matches("\\+[1-9][0-9]{7,14}"))
            throw new IllegalArgumentException("Invalid phone number");
        return normalized;
    }

    private String validateEmail(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw new IllegalArgumentException("Invalid email address");
        return normalized;
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

    private BatchMappingResult mapRows(UUID tenantId, MappingProfile profile, SourceSchema schema,
            ParsedInput input, List<SourceField> sources, List<MappingRule> configuredRules) {
        List<SourceField> keys = sources.stream().filter(SourceField::isDocumentKey).toList();
        if (keys.isEmpty()) throw new IllegalArgumentException("Source schema has no document key");
        Map<UUID, SourceField> sourceById = sources.stream()
                .collect(java.util.stream.Collectors.toMap(SourceField::getId, value -> value));
        Map<UUID, FieldDefinition> targetById = new HashMap<>();
        targetFields.findAllById(configuredRules.stream().map(MappingRule::getTargetFieldId).toList())
                .forEach(value -> targetById.put(value.getId(), value));
        SourceField classifier = schema.getRowTypeFieldId() == null ? null
                : sourceById.get(schema.getRowTypeFieldId());
        LinkedHashMap<String, List<ParsedInput.ParsedRow>> grouped = new LinkedHashMap<>();
        for (ParsedInput.ParsedRow row : input.rows()) {
            String key = keys.stream().map(field -> textAt(row, field.getSourcePath()))
                    .collect(java.util.stream.Collectors.joining("|"));
            if (key.isBlank() || key.replace("|", "").isBlank())
                throw new IllegalArgumentException("Document key is missing at " + row.sourceLocation());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        List<DocumentMappingResult> documents = new ArrayList<>();
        int documentOrder = 0;
        for (var group : grouped.entrySet()) {
            ObjectNode payload = json.createObjectNode();
            for (MappingRule rule : configuredRules) {
                SourceField source = sourceById.get(rule.getSourceFieldId());
                FieldDefinition target = targetById.get(rule.getTargetFieldId());
                validateDefinition(tenantId, source, target);
                if (source.getScope() == SourceFieldScope.ITEM
                        || source.getScope() == SourceFieldScope.ROW_CONTROL
                        || source.getScope() == SourceFieldScope.IGNORE) continue;
                List<JsonNode> values = aggregate(group.getValue(), source);
                if (values.isEmpty() && rule.getDefaultValue() != null)
                    values = List.of(json.getNodeFactory().textNode(rule.getDefaultValue()));
                values = transform(values, rule.getTransformation(), target, source.getSourcePath())
                        .stream().filter(value -> !isEmpty(value)).toList();
                if (values.isEmpty() && (source.isRequired() || rule.isRequired() || target.isRequired()))
                    throw new IllegalArgumentException("Required value is missing for " + target.getKey()
                            + " in document " + group.getKey());
                if (!values.isEmpty()) put(payload, target, values);
            }
            ArrayNode items = payload.putArray("items");
            for (ParsedInput.ParsedRow row : group.getValue()) {
                if (!isItemRow(row, classifier, schema, sources)) continue;
                ObjectNode item = json.createObjectNode();
                for (MappingRule rule : configuredRules) {
                    SourceField source = sourceById.get(rule.getSourceFieldId());
                    if (source == null || source.getScope() != SourceFieldScope.ITEM) continue;
                    FieldDefinition target = targetById.get(rule.getTargetFieldId());
                    JsonNode raw = row.values().get(source.getSourcePath());
                    List<JsonNode> values = raw == null || isEmpty(raw) ? List.of()
                            : transform(List.of(raw), rule.getTransformation(), target, source.getSourcePath());
                    if (values.isEmpty() && (source.isRequired() || rule.isRequired() || target.isRequired()))
                        throw new IllegalArgumentException("Required item value is missing at "
                                + row.sourceLocation());
                    if (!values.isEmpty()) putItem(item, target.getKey(), values.get(0));
                }
                if (!item.isEmpty()) items.add(item);
            }
            validateDestinations(payload, group.getKey());
            documents.add(new DocumentMappingResult(++documentOrder, group.getKey(), payload));
        }
        return new BatchMappingResult(profile.getId(), profile.getSourceSchemaId(),
                profile.getDocumentType(), configHash(profile, configuredRules), documents);
    }

    private List<JsonNode> aggregate(List<ParsedInput.ParsedRow> rows, SourceField field) {
        List<JsonNode> values = rows.stream().map(row -> row.values().get(field.getSourcePath()))
                .filter(value -> !isEmpty(value)).toList();
        if (values.isEmpty()) return List.of();
        return switch (field.getValuePolicy()) {
            case FIRST_NON_EMPTY -> List.of(values.get(0));
            case LAST_NON_EMPTY -> List.of(values.get(values.size() - 1));
            case REQUIRE_SAME -> {
                String first = values.get(0).asText();
                if (values.stream().anyMatch(value -> !first.equals(value.asText())))
                    throw new IllegalArgumentException("Inconsistent document field: " + field.getSourcePath());
                yield List.of(values.get(0));
            }
            case MERGE_DISTINCT -> values.stream().distinct().toList();
            case SUM -> List.of(json.getNodeFactory().numberNode(values.stream()
                    .map(value -> new BigDecimal(value.asText())).reduce(BigDecimal.ZERO, BigDecimal::add)));
        };
    }

    private boolean isItemRow(ParsedInput.ParsedRow row, SourceField classifier,
            SourceSchema schema, List<SourceField> sources) {
        if (classifier != null) {
            String type = textAt(row, classifier.getSourcePath()).toUpperCase(Locale.ROOT);
            if (schema.getIgnoredRowValues().contains(type) || schema.getTotalRowValues().contains(type))
                return false;
            return schema.getItemRowValues().isEmpty() || schema.getItemRowValues().contains(type);
        }
        return sources.stream().filter(field -> field.getScope() == SourceFieldScope.ITEM)
                .anyMatch(field -> !textAt(row, field.getSourcePath()).isBlank());
    }

    private String textAt(ParsedInput.ParsedRow row, String path) {
        JsonNode value = row.values().get(path);
        return value == null || value.isNull() ? "" : value.asText().trim();
    }

    private void putItem(ObjectNode item, String targetKey, JsonNode value) {
        String path = targetKey.startsWith("items.") ? targetKey.substring("items.".length()) : targetKey;
        String[] segments = path.split("\\.");
        ObjectNode parent = item;
        for (int index = 0; index < segments.length - 1; index++)
            parent = parent.withObject(segments[index]);
        parent.set(segments[segments.length - 1], value);
    }

    private void validateDestinations(ObjectNode payload, String documentKey) {
        java.util.Set<String> channels = new java.util.HashSet<>();
        JsonNode channelNode = payload.at("/delivery/channels");
        if (channelNode.isArray()) channelNode.forEach(value -> channels.add(value.asText()));
        boolean hasEmails = payload.at("/recipient/emails").isArray()
                && !payload.at("/recipient/emails").isEmpty();
        boolean hasPhones = payload.at("/recipient/phones").isArray()
                && !payload.at("/recipient/phones").isEmpty();
        if (channels.contains("EMAIL") && !hasEmails)
            throw new IllegalArgumentException("EMAIL destination is missing for " + documentKey);
        if (channels.stream().anyMatch(Set.of("SMS", "WHATSAPP", "VIBER")::contains) && !hasPhones)
            throw new IllegalArgumentException("Phone destination is missing for " + documentKey);
    }

    private String configHash(MappingProfile profile, List<MappingRule> configuredRules) {
        ObjectNode snapshot = json.createObjectNode();
        snapshot.put("profileId", profile.getId().toString());
        snapshot.put("sourceSchemaVersionId", profile.getSourceSchemaId().toString());
        ArrayNode values = snapshot.putArray("rules");
        configuredRules.stream().sorted(java.util.Comparator.comparing(MappingRule::getId))
                .forEach(rule -> {
                    ObjectNode item = values.addObject();
                    item.put("sourceFieldId", rule.getSourceFieldId().toString());
                    item.put("targetFieldId", rule.getTargetFieldId().toString());
                    item.set("transformation", rule.getTransformation());
                    item.put("defaultValue", rule.getDefaultValue());
                    item.put("required", rule.isRequired());
                });
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsBytes(snapshot));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Cannot hash mapping configuration", ex);
        }
    }

    public record MappingResult(
            UUID mappingProfileId, UUID sourceSchemaVersionId, String documentType,
            ObjectNode normalizedPayload, String mappingConfigSha256) {}
    public record RuleTestStep(String operation, JsonNode result) {}
    public record RuleTestResult(JsonNode sourceValue, JsonNode result, String resultType,
            List<RuleTestStep> steps) {}
    public record DocumentMappingResult(int order, String documentKey, ObjectNode normalizedPayload) {}
    public record BatchMappingResult(UUID mappingProfileId, UUID sourceSchemaVersionId,
            String documentType, String mappingConfigSha256,
            List<DocumentMappingResult> documents) {}
}
