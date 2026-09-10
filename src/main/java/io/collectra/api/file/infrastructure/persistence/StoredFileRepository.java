package io.collectra.api.file.infrastructure.persistence;

import io.collectra.api.file.domain.FileStatus;
import io.collectra.api.file.domain.StoredFile;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
    Optional<StoredFile> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByStatus(FileStatus status);

    @Query(
            value =
                    """
                    select id
                    from stored_file
                    where expires_at is not null
                      and expires_at <= :now
                      and status in ('READY', 'DELETE_PENDING')
                      and delete_attempts < :maxAttempts
                      and (last_delete_attempt_at is null or last_delete_attempt_at <= :retryBefore)
                    order by expires_at asc, id asc
                    limit :batchSize
                    for update skip locked
                    """,
            nativeQuery = true)
    List<UUID> lockCleanupCandidateIds(
            @Param("now") Instant now,
            @Param("retryBefore") Instant retryBefore,
            @Param("maxAttempts") int maxAttempts,
            @Param("batchSize") int batchSize);
}
