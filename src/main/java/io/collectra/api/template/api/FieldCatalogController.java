package io.collectra.api.template.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.shared.tenant.TenantContext;
import io.collectra.api.template.application.FieldCatalogService;
import io.collectra.api.template.domain.FieldDataType;
import io.collectra.api.template.domain.FieldDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/templates/fields")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class FieldCatalogController {
    private final FieldCatalogService catalog;

    public FieldCatalogController(FieldCatalogService catalog) { this.catalog = catalog; }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('FIELD_READ')")
    List<FieldResponse> list() {
        return catalog.catalog(TenantContext.requireTenantId()).stream()
                .map(FieldResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('FIELD_CREATE')")
    FieldResponse create(@Valid @RequestBody FieldRequest request) {
        return FieldResponse.from(catalog.create(TenantContext.requireTenantId(), request.key(),
                request.label(), request.dataType(), request.category(), request.collection(),
                request.required(), request.description(), request.exampleValue(),
                request.validationRules()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('FIELD_UPDATE')")
    FieldResponse update(@PathVariable UUID id, @Valid @RequestBody FieldUpdateRequest request) {
        return FieldResponse.from(catalog.update(TenantContext.requireTenantId(), id,
                request.label(), request.dataType(), request.category(), request.collection(),
                request.required(), request.description(), request.exampleValue(),
                request.validationRules()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('FIELD_UPDATE')")
    void archive(@PathVariable UUID id) {
        catalog.archive(TenantContext.requireTenantId(), id);
    }

    record FieldRequest(@NotBlank @Size(max = 160) String key,
            @NotBlank @Size(max = 200) String label, @NotNull FieldDataType dataType,
            @NotBlank @Size(max = 60) String category, boolean collection, boolean required,
            @Size(max = 500) String description, @Size(max = 500) String exampleValue,
            JsonNode validationRules) {}

    record FieldUpdateRequest(@NotBlank @Size(max = 200) String label,
            @NotNull FieldDataType dataType, @NotBlank @Size(max = 60) String category,
            boolean collection, boolean required, @Size(max = 500) String description,
            @Size(max = 500) String exampleValue, JsonNode validationRules) {}

    record FieldResponse(UUID id, String key, String placeholder, String label,
            FieldDataType dataType, String category, boolean collection, boolean required,
            String description, String exampleValue, JsonNode validationRules, boolean system) {
        static FieldResponse from(FieldDefinition field) {
            return new FieldResponse(field.getId(), field.getKey(), "{{" + field.getKey() + "}}",
                    field.getLabel(), field.getDataType(), field.getCategory(),
                    field.isCollection(), field.isRequired(), field.getDescription(),
                    field.getExampleValue(), field.getValidationRules(), field.isSystem());
        }
    }
}
