package io.collectra.api.communication.infrastructure;

import io.collectra.api.communication.domain.CommunicationChannel;
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

    @Query(
            """
            select m from Message m
            where m.tenantId = :tenantId
              and m.campaignId = :campaignId
              and m.campaignRunId = :runId
              and (:status is null or m.status = :status)
              and (:channel is null or m.channel = :channel)
              and (:customerId is null or m.customerId = :customerId)
            """)
    Slice<Message> findDeliveryMessages(
            @Param("tenantId") UUID tenantId,
            @Param("campaignId") UUID campaignId,
            @Param("runId") UUID runId,
            @Param("status") MessageStatus status,
            @Param("channel") CommunicationChannel channel,
            @Param("customerId") UUID customerId,
            Pageable pageable);

    Optional<Message> findByIdAndTenantIdAndCampaignIdAndCampaignRunId(
            UUID id, UUID tenantId, UUID campaignId, UUID campaignRunId);

    long countByTenantIdAndCampaignRunIdAndStatus(
            UUID tenantId, UUID campaignRunId, MessageStatus status);

    long countByStatusAndProcessingStartedAtBefore(MessageStatus status, Instant cutoff);

    Optional<Message> findFirstByStatusAndProcessingStartedAtBeforeOrderByProcessingStartedAtAsc(
            MessageStatus status, Instant cutoff);

    long countByStatusAndNextRetryAtLessThanEqual(MessageStatus status, Instant now);

    Optional<Message> findFirstByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            MessageStatus status, Instant now);

    @Query(
            value =
                    """
                    SELECT *
                    FROM messages
                    WHERE status = 'PROCESSING'
                      AND processing_started_at < :cutoff
                    ORDER BY processing_started_at ASC, id ASC
                    LIMIT :batchSize
                    """,
            nativeQuery = true)
    List<Message> findStaleProcessingCandidates(
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
