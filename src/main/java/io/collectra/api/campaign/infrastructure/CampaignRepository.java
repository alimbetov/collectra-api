package io.collectra.api.campaign.infrastructure;

import io.collectra.api.campaign.domain.Campaign;
import io.collectra.api.campaign.domain.CampaignStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    Optional<Campaign> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Campaign c where c.id = :campaignId and c.tenantId = :tenantId")
    Optional<Campaign> findLockedByIdAndTenantId(
            @Param("campaignId") UUID campaignId, @Param("tenantId") UUID tenantId);

    List<Campaign> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    @Query(
            """
            select c
            from Campaign c
            where c.status = :status
              and c.scheduledAt is not null
              and c.scheduledAt <= :now
              and c.scheduledDispatchedAt is null
            order by c.scheduledAt asc, c.id asc
            """)
    List<Campaign> findDueScheduled(
            @Param("status") CampaignStatus status, @Param("now") Instant now, Pageable pageable);
}
