package io.collectra.api.template.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.collectra.api.template.domain.FieldDataType;
import io.collectra.api.template.domain.FieldDefinition;
import io.collectra.api.template.infrastructure.FieldDefinitionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class FieldCatalogService {
    private final FieldDefinitionRepository fields;
    private final FieldKeyValidator fieldKeyValidator;

    public FieldCatalogService(
            FieldDefinitionRepository fields,
            FieldKeyValidator fieldKeyValidator) {
        this.fields = fields;
        this.fieldKeyValidator = fieldKeyValidator;
    }

    @Transactional(readOnly = true)
    public List<FieldDefinition> catalog(UUID tenantId) {
        return fields.findAvailable(tenantId);
    }

    @Transactional
    public FieldDefinition create(
            UUID tenantId,
            String key,
            String label,
            FieldDataType dataType,
            String category,
            boolean collection,
            boolean required,
            String description,
            String exampleValue,
            JsonNode validationRules) {
        String canonicalKey = fieldKeyValidator.validateCustomFieldKey(key).canonical();
        ensureKeyAvailable(tenantId, canonicalKey);
        FieldDefinition field =
                new FieldDefinition(
                        tenantId,
                        canonicalKey,
                        label.trim(),
                        dataType,
                        normalizeCategory(category),
                        collection,
                        required,
                        rules(validationRules));
        field.update(
                field.getLabel(),
                dataType,
                field.getCategory(),
                collection,
                required,
                description,
                exampleValue,
                rules(validationRules));
        return fields.save(field);
    }

    @Transactional
    public FieldDefinition update(
            UUID tenantId,
            UUID fieldId,
            String label,
            FieldDataType dataType,
            String category,
            boolean collection,
            boolean required,
            String description,
            String exampleValue,
            JsonNode validationRules) {
        FieldDefinition field = mutableField(tenantId, fieldId);
        field.update(
                label.trim(),
                dataType,
                normalizeCategory(category),
                collection,
                required,
                description,
                exampleValue,
                rules(validationRules));
        return field;
    }

    @Transactional
    public void archive(UUID tenantId, UUID fieldId) {
        mutableField(tenantId, fieldId).archive();
    }

    private FieldDefinition mutableField(UUID tenantId, UUID fieldId) {
        FieldDefinition field =
                fields.findById(fieldId)
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
