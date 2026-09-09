package io.collectra.api.importing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.collectra.api.importing.domain.FieldValuePolicy;
import io.collectra.api.importing.domain.SourceField;
import io.collectra.api.importing.domain.SourceFieldScope;
import io.collectra.api.importing.domain.SourceFormat;
import io.collectra.api.importing.domain.SourceSchema;
import io.collectra.api.importing.infrastructure.SourceFieldRepository;
import io.collectra.api.importing.infrastructure.SourceSchemaDefinitionRepository;
import io.collectra.api.importing.infrastructure.SourceSchemaRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceSchemaValidationUnitTest {
    private final SourceSchemaDefinitionRepository definitions = mock(SourceSchemaDefinitionRepository.class);
    private final SourceSchemaRepository versions = mock(SourceSchemaRepository.class);
    private final SourceFieldRepository fields = mock(SourceFieldRepository.class);
    private final SourceSchemaManagementService service =
            new SourceSchemaManagementService(definitions, versions, fields);

    @Test
    void requiresDocumentKeyAndAcceptsValidDocumentField() {
        UUID tenantId = UUID.randomUUID();
        SourceSchema schema = schema(tenantId, SourceFormat.CSV);
        SourceField ordinary = field(schema, "Invoice", SourceFieldScope.DOCUMENT, false);
        arrange(tenantId, schema, List.of(ordinary));

        assertThat(service.validate(tenantId, schema.getId()).errors())
                .extracting(SourceSchemaManagementService.ValidationIssue::code)
                .contains("DOCUMENT_KEY_MISSING");

        SourceSchema validSchema = schema(tenantId, SourceFormat.CSV);
        SourceField key = field(validSchema, "Invoice", SourceFieldScope.DOCUMENT, true);
        arrange(tenantId, validSchema, List.of(key));
        assertThat(service.validate(tenantId, validSchema.getId()).valid()).isTrue();
    }

    @Test
    void rejectsClassifierOverlapAndRecordPathForCsv() {
        UUID tenantId = UUID.randomUUID();
        SourceSchema schema = schema(tenantId, SourceFormat.CSV);
        SourceField key = field(schema, "Invoice", SourceFieldScope.DOCUMENT, true);
        SourceField type = field(schema, "Type", SourceFieldScope.ROW_CONTROL, false);
        schema.configureRows("$.rows[*]", type.getId(), Set.of("ITEM"), Set.of("ITEM"), Set.of());
        arrange(tenantId, schema, List.of(key, type));

        assertThat(service.validate(tenantId, schema.getId()).errors())
                .extracting(SourceSchemaManagementService.ValidationIssue::code)
                .contains("ITEM_TOTAL_VALUES_OVERLAP", "RECORD_PATH_UNSUPPORTED");
    }

    @Test
    void reportsImplicitItemDetectionAsWarning() {
        UUID tenantId = UUID.randomUUID();
        SourceSchema schema = schema(tenantId, SourceFormat.JSON);
        SourceField key = field(schema, "invoice", SourceFieldScope.DOCUMENT, true);
        SourceField item = field(schema, "name", SourceFieldScope.ITEM, false);
        arrange(tenantId, schema, List.of(key, item));

        var result = service.validate(tenantId, schema.getId());
        assertThat(result.valid()).isTrue();
        assertThat(result.warnings()).extracting(SourceSchemaManagementService.ValidationIssue::code)
                .containsExactly("IMPLICIT_ITEM_DETECTION");
    }

    private SourceSchema schema(UUID tenantId, SourceFormat format) {
        return new SourceSchema(tenantId, "TEST_SCHEMA", "Test schema", format, 1);
    }

    private SourceField field(SourceSchema schema, String path, SourceFieldScope scope, boolean key) {
        return new SourceField(schema.getId(), path, "STRING", null, false, 1, scope, key,
                FieldValuePolicy.FIRST_NON_EMPTY);
    }

    private void arrange(UUID tenantId, SourceSchema schema, List<SourceField> configured) {
        when(versions.findByIdAndTenantId(schema.getId(), tenantId)).thenReturn(java.util.Optional.of(schema));
        when(fields.findAllBySourceSchemaIdOrderByPositionAsc(schema.getId())).thenReturn(configured);
    }
}
