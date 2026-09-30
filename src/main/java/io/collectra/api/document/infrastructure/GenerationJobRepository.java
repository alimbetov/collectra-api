package io.collectra.api.document.infrastructure;

import io.collectra.api.document.domain.GenerationJob;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, UUID> {
    Optional<GenerationJob> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GenerationJob> findLockedByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            value = """
                    select id
                    from generation_jobs
                    where status = 'PROCESSING'
                      and started_at <= :staleBefore
                    order by started_at, id
                    limit :limit
                    """,
            nativeQuery = true)
    List<UUID> findStaleProcessingIds(
            @Param("staleBefore") Instant staleBefore, @Param("limit") int limit);
}
