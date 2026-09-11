package io.collectra.api.communication.infrastructure;

import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    Optional<Message> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Message> findByCampaignRecipientIdAndTenantId(UUID campaignRecipientId, UUID tenantId);

    Slice<Message> findAllByTenantIdAndCampaignRunId(
            UUID tenantId, UUID campaignRunId, Pageable pageable);

    long countByTenantIdAndCampaignRunIdAndStatus(
            UUID tenantId, UUID campaignRunId, MessageStatus status);
}
