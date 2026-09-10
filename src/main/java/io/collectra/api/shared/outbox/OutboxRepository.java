package io.collectra.api.shared.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(
            value =
                    """
                    SELECT *
                    FROM outbox_events
                    WHERE status IN ('PENDING', 'RETRY_WAIT')
                      AND next_attempt_at <= :now
                    ORDER BY created_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT :batchSize
                    """,
            nativeQuery = true)
    List<OutboxEvent> findReadyForUpdate(
            @Param("now") Instant now, @Param("batchSize") int batchSize);

    @Query(
            value =
                    """
                    SELECT *
                    FROM outbox_events
                    WHERE status = 'PROCESSING'
                      AND locked_at < :cutoff
                    ORDER BY locked_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT :batchSize
                    """,
            nativeQuery = true)
    List<OutboxEvent> findStaleForUpdate(
            @Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
