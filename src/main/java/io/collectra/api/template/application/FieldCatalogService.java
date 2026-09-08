package io.collectra.api.template.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.collectra.api.template.domain.FieldDataType;
import io.collectra.api.template.domain.FieldDefinition;
import io.collectra.api.template.infrastructure.FieldDefinitionRepository;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FieldCatalogService {
    private static final Pattern CUSTOM_KEY =
            Pattern.compile("custom(?:\\.[a-z][a-z0-9_]*){2,}");

    private final FieldDefinitionRepository fields;

    public FieldCatalogService(FieldDefinitionRepository fields) {
        this.fields = fields;
    }

    @Transactional(readOnly = true)
    public List<FieldDefinition> catalog(UUID tenantId) {
        return fields.findAvailable(tenantId);
    }

    @Transactional
    public FieldDefinition create(UUID tenantId, String key, String label, FieldDataType dataType,
            String category, boolean collection, boolean required, String description,
            String exampleValue, JsonNode validationRules) {
        String normalizedKey = normalizeKey(key);
        ensureKeyAvailable(tenantId, normalizedKey);
        FieldDefinition field = new FieldDefinition(tenantId, normalizedKey, label.trim(), dataType,
                normalizeCategory(category), collection, required, rules(validationRules));
        field.update(field.getLabel(), dataType, field.getCategory(), collection, required,
                description, exampleValue, rules(validationRules));
        return fields.save(field);
    }

    @Transactional
    public FieldDefinition update(UUID tenantId, UUID fieldId, String label, FieldDataType dataType,
            String category, boolean collection, boolean required, String description,
            String exampleValue, JsonNode validationRules) {
        FieldDefinition field = mutableField(tenantId, fieldId);
        field.update(label.trim(), dataType, normalizeCategory(category), collection, required,
                description, exampleValue, rules(validationRules));
        return field;
    }

    @Transactional
    public void archive(UUID tenantId, UUID fieldId) {
        mutableField(tenantId, fieldId).archive();
    }

    private FieldDefinition mutableField(UUID tenantId, UUID fieldId) {
        FieldDefinition field = fields.findById(fieldId)
                .orElseThrow(() -> new NoSuchElementException("Field not found"));
        if (field.isSystem()) throw new IllegalArgumentException("System fields cannot be changed");
        if (!tenantId.equals(field.getTenantId()))
            throw new NoSuchElementException("Field not found");
        return field;
    }

    private void ensureKeyAvailable(UUID tenantId, String key) {
        if (fields.existsByTenantIdIsNullAndKeyIgnoreCase(key)
                || fields.existsByTenantIdAndKeyIgnoreCase(tenantId, key))
            throw new IllegalArgumentException("Field key already exists");
    }

    private String normalizeKey(String key) {
        String value = key.trim().toLowerCase(Locale.ROOT);
        if (!CUSTOM_KEY.matcher(value).matches())
            throw new IllegalArgumentException(
                    "Custom field key must match custom.<namespace>.<name>");
        return value;
    }

    private String normalizeCategory(String category) {
        return category.trim().toUpperCase(Locale.ROOT);
    }

    private JsonNode rules(JsonNode validationRules) {
        if (validationRules == null) return JsonNodeFactory.instance.objectNode();
        if (!validationRules.isObject())
            throw new IllegalArgumentException("Validation rules must be a JSON object");
        return validationRules;
    }
}
