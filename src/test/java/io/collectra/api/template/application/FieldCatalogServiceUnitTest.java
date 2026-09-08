package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.collectra.api.template.domain.FieldDataType;
import io.collectra.api.template.domain.FieldDefinition;
import io.collectra.api.template.infrastructure.FieldDefinitionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FieldCatalogServiceUnitTest {
    private final FieldDefinitionRepository fields = mock(FieldDefinitionRepository.class);
    private final FieldCatalogService service = new FieldCatalogService(fields);

    @Test
    void rejectsInvalidCustomNamespaceBeforeAccessingRepository() {
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), "invoice.total", "Total",
                FieldDataType.DECIMAL, "invoice", false, false, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("custom.<namespace>.<name>");
        verifyNoInteractions(fields);
    }

    @Test
    void refusesToModifySystemField() {
        UUID id = UUID.randomUUID();
        FieldDefinition system = new FieldDefinition(null, "document.number", "Number",
                FieldDataType.STRING, "DOCUMENT", false, false,
                JsonNodeFactory.instance.objectNode());
        when(fields.findById(id)).thenReturn(Optional.of(system));

        assertThatThrownBy(() -> service.archive(UUID.randomUUID(), id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("System fields cannot be changed");
    }

    @Test
    void hidesCrossTenantFieldAsNotFound() {
        UUID id = UUID.randomUUID();
        FieldDefinition foreign = new FieldDefinition(UUID.randomUUID(), "custom.invoice.total",
                "Total", FieldDataType.DECIMAL, "INVOICE", false, false,
                JsonNodeFactory.instance.objectNode());
        when(fields.findById(id)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.archive(UUID.randomUUID(), id))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessage("Field not found");
    }
}
