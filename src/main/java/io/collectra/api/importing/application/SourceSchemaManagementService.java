package io.collectra.api.importing.application;

import io.collectra.api.importing.domain.DefinitionStatus;
import io.collectra.api.importing.domain.SourceField;
import io.collectra.api.importing.domain.SourceFieldScope;
import io.collectra.api.importing.domain.FieldValuePolicy;
import io.collectra.api.importing.domain.SourceFormat;
import io.collectra.api.importing.domain.SourceSchema;
import io.collectra.api.importing.domain.SourceSchemaDefinition;
import io.collectra.api.importing.infrastructure.SourceFieldRepository;
import io.collectra.api.importing.infrastructure.SourceSchemaDefinitionRepository;
import io.collectra.api.importing.infrastructure.SourceSchemaRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceSchemaManagementService {
    private final SourceSchemaDefinitionRepository definitions;
    private final SourceSchemaRepository versions;
    private final SourceFieldRepository fields;

    public SourceSchemaManagementService(SourceSchemaDefinitionRepository definitions,
            SourceSchemaRepository versions, SourceFieldRepository fields) {
        this.definitions = definitions;
        this.versions = versions;
        this.fields = fields;
    }

    @Transactional
    public SourceSchemaDefinition create(UUID tenantId, String code, String name) {
        if (definitions.existsByTenantIdAndCodeIgnoreCase(tenantId, code))
            throw new IllegalArgumentException("Source schema code already exists");
        return definitions.save(new SourceSchemaDefinition(tenantId, normalizeCode(code), name.trim()));
    }

    @Transactional(readOnly = true)
    public List<SourceSchemaDefinition> list(UUID tenantId) {
        return definitions.findAllByTenantIdOrderByCodeAsc(tenantId);
    }

    @Transactional
    public SourceSchemaDefinition rename(UUID tenantId, UUID definitionId, String name) {
        var definition = requireDefinition(tenantId, definitionId);
        definition.rename(name.trim());
        return definition;
    }

    @Transactional
    public SourceSchema createVersion(UUID tenantId, UUID definitionId, SourceFormat format) {
        var definition = requireDefinition(tenantId, definitionId);
        var latest = versions.findTopByDefinitionIdOrderBySchemaVersionDesc(definitionId);
        int number = latest.map(v -> v.getSchemaVersion() + 1).orElse(1);
        var created = versions.save(new SourceSchema(tenantId, definitionId, definition.getCode(),
                definition.getName(), format, number));
        latest.ifPresent(previous -> fields.findAllBySourceSchemaIdOrderByPositionAsc(previous.getId())
                .forEach(field -> fields.save(new SourceField(created.getId(), field.getSourcePath(),
                        field.getDetectedType(), field.getSampleValue(), field.isRequired(),
                        field.getPosition(), field.getScope(), field.isDocumentKey(),
                        field.getValuePolicy()))));
        return created;
    }

    @Transactional(readOnly = true)
    public List<SourceSchema> versions(UUID tenantId, UUID definitionId) {
        requireDefinition(tenantId, definitionId);
        return versions.findAllByDefinitionIdOrderBySchemaVersionDesc(definitionId);
    }

    @Transactional
    public SourceField addField(UUID tenantId, UUID versionId, String path, String type,
            String sample, boolean required, Integer position) {
        return addField(tenantId, versionId, path, type, sample, required, position,
                SourceFieldScope.DOCUMENT, false, FieldValuePolicy.FIRST_NON_EMPTY);
    }

    @Transactional
    public SourceField addField(UUID tenantId, UUID versionId, String path, String type,
            String sample, boolean required, Integer position, SourceFieldScope scope,
            boolean documentKey, FieldValuePolicy valuePolicy) {
        var version = requireDraft(tenantId, versionId);
        return fields.save(new SourceField(version.getId(), path.trim(), type, sample, required,
                position, scope, documentKey, valuePolicy));
    }

    @Transactional
    public SourceField updateField(UUID tenantId, UUID versionId, UUID fieldId, String path,
            String type, String sample, boolean required, Integer position) {
        var existing = fields.findByIdAndSourceSchemaId(fieldId, versionId)
                .orElseThrow(() -> new NoSuchElementException("Source field not found"));
        return updateField(tenantId, versionId, fieldId, path, type, sample, required, position,
                existing.getScope(), existing.isDocumentKey(), existing.getValuePolicy());
    }

    @Transactional
    public SourceField updateField(UUID tenantId, UUID versionId, UUID fieldId, String path,
            String type, String sample, boolean required, Integer position, SourceFieldScope scope,
            boolean documentKey, FieldValuePolicy valuePolicy) {
        requireDraft(tenantId, versionId);
        var field = fields.findByIdAndSourceSchemaId(fieldId, versionId)
                .orElseThrow(() -> new NoSuchElementException("Source field not found"));
        field.update(path.trim(), type, sample, required, position, scope, documentKey, valuePolicy);
        return field;
    }

    @Transactional
    public SourceSchema configureRows(UUID tenantId, UUID versionId, String recordPath,
            UUID rowTypeFieldId, java.util.Set<String> itemValues,
            java.util.Set<String> totalValues, java.util.Set<String> ignoredValues) {
        var version = requireDraft(tenantId, versionId);
        if (rowTypeFieldId != null) {
            var classifier = fields.findByIdAndSourceSchemaId(rowTypeFieldId, versionId)
                    .orElseThrow(() -> new IllegalArgumentException("Row classifier field does not belong to schema"));
            if (classifier.getScope() != SourceFieldScope.ROW_CONTROL)
                throw new IllegalArgumentException("Row classifier field must have ROW_CONTROL scope");
        }
        version.configureRows(recordPath, rowTypeFieldId, itemValues, totalValues, ignoredValues);
        return version;
    }

    @Transactional
    public void deleteField(UUID tenantId, UUID versionId, UUID fieldId) {
        requireDraft(tenantId, versionId);
        var field = fields.findByIdAndSourceSchemaId(fieldId, versionId)
                .orElseThrow(() -> new NoSuchElementException("Source field not found"));
        fields.delete(field);
    }

    @Transactional(readOnly = true)
    public List<SourceField> fields(UUID tenantId, UUID versionId) {
        requireVersion(tenantId, versionId);
        return fields.findAllBySourceSchemaIdOrderByPositionAsc(versionId);
    }

    @Transactional
    public ValidationResult validate(UUID tenantId, UUID versionId) {
        var version = requireVersion(tenantId, versionId);
        if (version.getStatus() != DefinitionStatus.DRAFT)
            throw new IllegalArgumentException("Only draft schema can be validated");
        var errors = new java.util.ArrayList<ValidationIssue>();
        var warnings = new java.util.ArrayList<ValidationIssue>();
        var configured = fields.findAllBySourceSchemaIdOrderByPositionAsc(versionId);
        if (configured.isEmpty()) errors.add(new ValidationIssue("FIELDS_EMPTY", "fields",
                "At least one source field is required"));
        java.util.Set<String> paths = new java.util.HashSet<>();
        for (var field : configured) {
            String path = field.getSourcePath() == null ? "" : field.getSourcePath().trim();
            if (path.isEmpty()) errors.add(issue("SOURCE_PATH_EMPTY", "fields." + field.getId(),
                    "Source path is required"));
            else if (!paths.add(path.toLowerCase(java.util.Locale.ROOT)))
                errors.add(issue("SOURCE_PATH_DUPLICATE", "fields." + field.getId(),
                        "Source path must be unique"));
            if (field.isDocumentKey() && field.getScope() != SourceFieldScope.DOCUMENT)
                errors.add(issue("DOCUMENT_KEY_SCOPE_INVALID", "fields." + field.getId() + ".scope",
                        "Document key must have DOCUMENT scope"));
        }
        if (configured.stream().noneMatch(SourceField::isDocumentKey))
            errors.add(issue("DOCUMENT_KEY_MISSING", "fields", "At least one document key is required"));
        if (configured.stream().allMatch(field -> field.getScope() == SourceFieldScope.IGNORE))
            errors.add(issue("NON_IGNORED_FIELD_MISSING", "fields",
                    "At least one non-ignored source field is required"));

        var itemValues = version.getItemRowValues();
        var totalValues = version.getTotalRowValues();
        var ignoredValues = version.getIgnoredRowValues();
        addOverlap(errors, itemValues, totalValues, "ITEM_TOTAL_VALUES_OVERLAP");
        addOverlap(errors, itemValues, ignoredValues, "ITEM_IGNORE_VALUES_OVERLAP");
        addOverlap(errors, totalValues, ignoredValues, "TOTAL_IGNORE_VALUES_OVERLAP");
        boolean classifierValuesConfigured = !itemValues.isEmpty() || !totalValues.isEmpty()
                || !ignoredValues.isEmpty();
        if (version.getRowTypeFieldId() == null && classifierValuesConfigured)
            errors.add(issue("ROW_CLASSIFIER_MISSING", "rowTypeFieldId",
                    "Classifier values require a row type field"));
        if (version.getRowTypeFieldId() != null) {
            var classifier = configured.stream()
                    .filter(field -> field.getId().equals(version.getRowTypeFieldId())).findFirst();
            if (classifier.isEmpty()) errors.add(issue("ROW_CLASSIFIER_NOT_FOUND", "rowTypeFieldId",
                    "Row classifier must belong to this schema version"));
            else if (classifier.get().getScope() != SourceFieldScope.ROW_CONTROL)
                errors.add(issue("ROW_CLASSIFIER_SCOPE_INVALID", "rowTypeFieldId",
                        "Row classifier must have ROW_CONTROL scope"));
        }
        if (configured.stream().anyMatch(field -> field.getScope() == SourceFieldScope.ITEM)
                && version.getRowTypeFieldId() == null)
            warnings.add(issue("IMPLICIT_ITEM_DETECTION", "rowTypeFieldId",
                    "Rows with any non-empty ITEM field will be treated as item rows"));
        validateRecordPath(version, errors);
        if (errors.isEmpty()) version.validated();
        return new ValidationResult(errors.isEmpty(), List.copyOf(errors), List.copyOf(warnings));
    }

    @Transactional
    public SourceSchema publish(UUID tenantId, UUID versionId) {
        var version = requireVersion(tenantId, versionId);
        version.publish();
        return version;
    }

    @Transactional
    public SourceSchema reopen(UUID tenantId, UUID versionId) {
        var version = requireVersion(tenantId, versionId);
        version.reopen();
        return version;
    }

    @Transactional
    public SourceSchema archive(UUID tenantId, UUID versionId) {
        var version = requireVersion(tenantId, versionId);
        version.archive();
        return version;
    }

    private SourceSchemaDefinition requireDefinition(UUID tenantId, UUID id) {
        return definitions.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Source schema not found"));
    }

    private SourceSchema requireVersion(UUID tenantId, UUID id) {
        return versions.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Source schema version not found"));
    }

    private SourceSchema requireDraft(UUID tenantId, UUID id) {
        var version = requireVersion(tenantId, id);
        if (version.getStatus() != DefinitionStatus.DRAFT)
            throw new IllegalArgumentException("Only draft schema can be changed");
        return version;
    }

    private String normalizeCode(String code) {
        String value = code.trim().toUpperCase(java.util.Locale.ROOT);
        if (!value.matches("[A-Z][A-Z0-9_]{1,99}"))
            throw new IllegalArgumentException("Invalid source schema code");
        return value;
    }

    private void addOverlap(java.util.List<ValidationIssue> errors, java.util.Set<String> left,
            java.util.Set<String> right, String code) {
        java.util.Set<String> overlap = new java.util.TreeSet<>(left);
        overlap.retainAll(right);
        if (!overlap.isEmpty()) errors.add(issue(code, "rowClassification",
                "Classifier values overlap: " + String.join(",", overlap)));
    }

    private void validateRecordPath(SourceSchema version, java.util.List<ValidationIssue> errors) {
        String path = version.getRecordPath();
        if (path == null) return;
        if (version.getSourceFormat() == SourceFormat.CSV
                || version.getSourceFormat() == SourceFormat.EXCEL)
            errors.add(issue("RECORD_PATH_UNSUPPORTED", "recordPath",
                    "Record path is supported only for JSON and XML"));
        else if (version.getSourceFormat() == SourceFormat.JSON
                && !(path.startsWith("$") || path.startsWith("/")))
            errors.add(issue("JSON_RECORD_PATH_INVALID", "recordPath",
                    "JSON record path must start with $ or /"));
        else if (version.getSourceFormat() == SourceFormat.XML && !path.startsWith("/"))
            errors.add(issue("XML_RECORD_PATH_INVALID", "recordPath",
                    "XML record path must be absolute"));
    }

    private ValidationIssue issue(String code, String path, String message) {
        return new ValidationIssue(code, path, message);
    }

    public record ValidationIssue(String code, String path, String message) {}
    public record ValidationResult(boolean valid, List<ValidationIssue> errors,
            List<ValidationIssue> warnings) {}
}
