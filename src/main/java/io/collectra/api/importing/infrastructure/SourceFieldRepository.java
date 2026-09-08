package io.collectra.api.importing.infrastructure;

import io.collectra.api.importing.domain.SourceField;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceFieldRepository extends JpaRepository<SourceField, UUID> {
    List<SourceField> findAllBySourceSchemaIdOrderByPositionAsc(UUID sourceSchemaId);
}
