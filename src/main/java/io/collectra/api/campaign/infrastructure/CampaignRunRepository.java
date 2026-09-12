package io.collectra.api.campaign.infrastructure;

import io.collectra.api.campaign.domain.CampaignRun;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignRunRepository extends JpaRepository<CampaignRun, UUID> {
    Optional<CampaignRun> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CampaignRun r where r.id = :runId and r.tenantId = :tenantId")
    Optional<CampaignRun> findLockedByIdAndTenantId(
            @Param("runId") UUID runId, @Param("tenantId") UUID tenantId);

    List<CampaignRun> findAllByTenantIdAndCampaignIdOrderByCreatedAtDesc(
            UUID tenantId, UUID campaignId);
}
