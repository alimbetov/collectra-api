package io.collectra.api.importing.infrastructure;

import io.collectra.api.importing.domain.ImportRecordDiagnostic;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRecordDiagnosticRepository
        extends JpaRepository<ImportRecordDiagnostic, UUID> {
    Page<ImportRecordDiagnostic> findAllByTenantIdAndImportId(
            UUID tenantId, UUID importId, Pageable pageable);
}
