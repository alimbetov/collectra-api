package io.collectra.api.communication.infrastructure;

import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    Optional<Message> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Message m where m.tenantId = :tenantId and m.id = :messageId")
    Optional<Message> findLockedByIdAndTenantId(
            @Param("tenantId") UUID tenantId, @Param("messageId") UUID messageId);

    Optional<Message> findByCampaignRecipientIdAndTenantId(UUID campaignRecipientId, UUID tenantId);

    Slice<Message> findAllByTenantIdAndCampaignRunId(
            UUID tenantId, UUID campaignRunId, Pageable pageable);

    long countByTenantIdAndCampaignRunIdAndStatus(
            UUID tenantId, UUID campaignRunId, MessageStatus status);

    @Query(
            value =
                    """
                    SELECT *
                    FROM messages
                    WHERE status = 'PROCESSING'
                      AND processing_started_at < :cutoff
                    ORDER BY processing_started_at ASC, id ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT :batchSize
                    """,
            nativeQuery = true)
    List<Message> findStaleProcessingForUpdate(
            @Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

    @Query(
            value =
                    """
                    SELECT *
                    FROM messages
                    WHERE status = 'RETRY_WAIT'
                      AND next_retry_at <= :now
                    ORDER BY next_retry_at ASC, id ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT :batchSize
                    """,
            nativeQuery = true)
    List<Message> findDueRetryForUpdate(
            @Param("now") Instant now, @Param("batchSize") int batchSize);
}
