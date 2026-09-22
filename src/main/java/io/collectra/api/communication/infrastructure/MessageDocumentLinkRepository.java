package io.collectra.api.communication.infrastructure;

import io.collectra.api.communication.domain.MessageDocumentLink;
import io.collectra.api.communication.domain.MessageDocumentLinkStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageDocumentLinkRepository extends JpaRepository<MessageDocumentLink, UUID> {
    Optional<MessageDocumentLink> findByTenantIdAndGenerationJobId(
            UUID tenantId, UUID generationJobId);

    Optional<MessageDocumentLink> findByTokenHash(String tokenHash);

    Optional<MessageDocumentLink> findByTenantIdAndMessageId(UUID tenantId, UUID messageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select l from MessageDocumentLink l
            where l.tenantId = :tenantId and l.generationJobId = :generationJobId
            """)
    Optional<MessageDocumentLink> findLockedByTenantIdAndGenerationJobId(
            @Param("tenantId") UUID tenantId, @Param("generationJobId") UUID generationJobId);

    @Query(
            """
            select case when count(l) > 0 then true else false end
            from MessageDocumentLink l
            where l.tenantId = :tenantId
              and l.messageId = :messageId
              and l.required = true
              and l.status <> :ready
            """)
    boolean existsRequiredNotReady(
            @Param("tenantId") UUID tenantId,
            @Param("messageId") UUID messageId,
            @Param("ready") MessageDocumentLinkStatus ready);
}
