package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.collectra.api.template.domain.FieldDataType;
import io.collectra.api.template.domain.FieldDefinition;
import io.collectra.api.template.infrastructure.FieldDefinitionRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

class FieldCatalogServiceUnitTest {
    private final FieldDefinitionRepository fields = mock(FieldDefinitionRepository.class);
    private final FieldCatalogService service = new FieldCatalogService(fields);

    @Test
    void createsCustomFieldOnlyAfterSharedPlaceholderGrammarAcceptsItsKey() {
        UUID tenantId = UUID.randomUUID();
        when(fields.save(any(FieldDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.create(
                tenantId,
                "custom.invoice.manager_name",
                "Manager",
                FieldDataType.STRING,
                "invoice",
                false,
                false,
                null,
                null,
                null);

        ArgumentCaptor<FieldDefinition> captor = ArgumentCaptor.forClass(FieldDefinition.class);
        verify(fields).save(captor.capture());
        assertThat(captor.getValue().getKey()).isEqualTo("custom.invoice.manager_name");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "invoice.total",
                "custom.invoice",
                "Custom.Invoice.Total",
                "custom..total",
                "custom.invoice-total.value",
                "custom.invoice.1total",
                "custom.invoice.total.",
                ".custom.invoice.total",
                "custom invoice total"
            })
    void rejectsKeysThatCannotBeCanonicalCustomPlaceholders(String key) {
        assertThatThrownBy(
                        () ->
                                service.create(
                                        UUID.randomUUID(),
                                        key,
                                        "Total",
                                        FieldDataType.DECIMAL,
                                        "invoice",
                                        false,
                                        false,
                                        null,
                                        null,
                                        null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(fields);
    }

    @Test
    void rejectsInvalidCustomNamespaceBeforeAccessingRepository() {
        assertThatThrownBy(
                        () ->
                                service.create(
                                        UUID.randomUUID(),
                                        "invoice.total",
                                        "Total",
                                        FieldDataType.DECIMAL,
                                        "invoice",
                                        false,
                                        false,
                                        null,
                                        null,
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("custom.<namespace>.<name>");
        verifyNoInteractions(fields);
    }

    @Test
    void refusesToModifySystemField() {
        UUID id = UUID.randomUUID();
        FieldDefinition system =
                new FieldDefinition(
                        null,
                        "document.number",
                        "Number",
                        FieldDataType.STRING,
                        "DOCUMENT",
                        false,
                        false,
                        JsonNodeFactory.instance.objectNode());
        when(fields.findById(id)).thenReturn(Optional.of(system));

        assertThatThrownBy(() -> service.archive(UUID.randomUUID(), id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("System fields cannot be changed");
    }

    @Test
    void hidesCrossTenantFieldAsNotFound() {
        UUID id = UUID.randomUUID();
        FieldDefinition foreign =
                new FieldDefinition(
                        UUID.randomUUID(),
                        "custom.invoice.total",
                        "Total",
                        FieldDataType.DECIMAL,
                        "INVOICE",
                        false,
                        false,
                        JsonNodeFactory.instance.objectNode());
        when(fields.findById(id)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.archive(UUID.randomUUID(), id))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessage("Field not found");
    }
}
