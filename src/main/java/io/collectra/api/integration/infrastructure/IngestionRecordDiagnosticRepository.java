package io.collectra.api.integration.infrastructure;
import io.collectra.api.integration.domain.IngestionRecordDiagnostic;import java.util.UUID;import org.springframework.data.jpa.repository.JpaRepository;
public interface IngestionRecordDiagnosticRepository extends JpaRepository<IngestionRecordDiagnostic,UUID>{}
