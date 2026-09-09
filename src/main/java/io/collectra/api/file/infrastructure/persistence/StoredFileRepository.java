package io.collectra.api.file.infrastructure.persistence;

import io.collectra.api.file.domain.StoredFile;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
    Optional<StoredFile> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("""
            select f
            from StoredFile f
            where f.expiresAt is not null
              and f.expiresAt <= :now
              and f.status in (io.collectra.api.file.domain.FileStatus.READY,
                               io.collectra.api.file.domain.FileStatus.DELETE_PENDING)
              and f.deleteAttempts < :maxAttempts
            order by f.expiresAt asc, f.id asc
            """)
    List<StoredFile> findCleanupCandidates(
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts,
            Pageable pageable);
}
