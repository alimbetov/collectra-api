package io.collectra.api.integration.application;

import io.collectra.api.importing.application.*;
import io.collectra.api.integration.domain.IngestionRecordDiagnostic;
import io.collectra.api.integration.infrastructure.IngestionRecordDiagnosticRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IngestionRecordProcessor {
    private final BusinessRecordPersistenceService persistence;
    private final IngestionRecordDiagnosticRepository diagnostics;
    private final Clock clock;

    public IngestionRecordProcessor(
            BusinessRecordPersistenceService persistence,
            IngestionRecordDiagnosticRepository diagnostics,
            Clock clock) {
        this.persistence = persistence;
        this.diagnostics = diagnostics;
        this.clock = clock;
    }

    public Outcome process(
            UUID tenantId,
            UUID batchId,
            String documentType,
            MappingExecutionService.DocumentMappingResult document) {
        var existing =
                diagnostics.findByTenantIdAndBatchIdAndRecordOrder(
                        tenantId, batchId, document.order());
        if (existing.isPresent()) return new Outcome(existing.get().getOutcome());
        try {
            var result = persistence.persist(tenantId, documentType, document.normalizedPayload());
            String outcome = result.created() ? "CREATED" : "REUSED";
            diagnostics.save(
                    new IngestionRecordDiagnostic(
                            tenantId,
                            batchId,
                            document.order(),
                            document.documentKey(),
                            outcome,
                            "PERSISTENCE",
                            result.type(),
                            result.id(),
                            result.externalId(),
                            null,
                            null,
                            clock.instant()));
            return new Outcome(outcome);
        } catch (BusinessRecordConflictException ex) {
            diagnostics.save(
                    new IngestionRecordDiagnostic(
                            tenantId,
                            batchId,
                            document.order(),
                            document.documentKey(),
                            "CONFLICT",
                            "PERSISTENCE",
                            ex.type(),
                            null,
                            ex.externalId(),
                            "BUSINESS_IDENTITY_CONFLICT",
                            "External identity conflicts with existing canonical state",
                            clock.instant()));
            return new Outcome("CONFLICT");
        } catch (IllegalArgumentException ex) {
            diagnostics.save(
                    new IngestionRecordDiagnostic(
                            tenantId,
                            batchId,
                            document.order(),
                            document.documentKey(),
                            "FAILED",
                            "PERSISTENCE",
                            documentType,
                            null,
                            null,
                            "BUSINESS_VALIDATION_FAILED",
                            safeMessage("Business record validation failed", ex),
                            clock.instant()));
            return new Outcome("FAILED");
        }
    }

    private String safeMessage(String fallback, Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        if (message == null || message.isBlank()) message = fallback;
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    public record Outcome(String value) {}
}
