package io.collectra.api.communication.infrastructure;

import io.collectra.api.communication.domain.MessageAttachment;
import io.collectra.api.communication.domain.MessageAttachmentStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, UUID> {
    List<MessageAttachment> findAllByTenantIdAndMessageIdOrderByCreatedAtAsc(
            UUID tenantId, UUID messageId);

    Optional<MessageAttachment> findByTenantIdAndGenerationJobId(UUID tenantId, UUID generationJobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select a from MessageAttachment a
            where a.tenantId = :tenantId and a.generationJobId = :generationJobId
            """)
    Optional<MessageAttachment> findLockedByTenantIdAndGenerationJobId(
            @Param("tenantId") UUID tenantId, @Param("generationJobId") UUID generationJobId);

    @Query(
            """
            select case when count(a) > 0 then true else false end
            from MessageAttachment a
            where a.tenantId = :tenantId
              and a.messageId = :messageId
              and a.required = true
              and a.status <> :ready
            """)
    boolean existsRequiredNotReady(
            @Param("tenantId") UUID tenantId,
            @Param("messageId") UUID messageId,
            @Param("ready") MessageAttachmentStatus ready);

    boolean existsByTenantIdAndMessageIdAndRequiredTrue(UUID tenantId, UUID messageId);
}
