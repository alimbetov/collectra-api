package io.collectra.api.campaign.infrastructure;

import io.collectra.api.campaign.domain.CampaignRecipient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignRecipientRepository extends JpaRepository<CampaignRecipient, UUID> {
    Optional<CampaignRecipient> findByIdAndTenantId(UUID id, UUID tenantId);

    List<CampaignRecipient> findAllByTenantIdAndRunIdOrderByCreatedAtAsc(UUID tenantId, UUID runId);

    @Modifying(flushAutomatically = true)
    @Query(
            value =
                    """
                    UPDATE campaign_recipients
                    SET locale = :locale,
                        updated_at = CURRENT_TIMESTAMP,
                        version = version + 1
                    WHERE tenant_id = :tenantId
                      AND run_id = :runId
                      AND (locale IS NULL OR BTRIM(locale) = '')
                    """,
            nativeQuery = true)
    int snapshotMissingLocales(
            @Param("tenantId") UUID tenantId,
            @Param("runId") UUID runId,
            @Param("locale") String locale);

    @Query(
            value =
                    """
                    SELECT cr.*
                    FROM campaign_recipients cr
                    WHERE cr.tenant_id = :tenantId
                      AND cr.run_id = :runId
                      AND cr.status <> 'SKIPPED'
                      AND NOT EXISTS (
                          SELECT 1
                          FROM messages m
                          WHERE m.campaign_recipient_id = cr.id
                      )
                    ORDER BY cr.created_at ASC, cr.id ASC
                    LIMIT :batchSize
                    """,
            nativeQuery = true)
    List<CampaignRecipient> findMaterializationCandidates(
            @Param("tenantId") UUID tenantId,
            @Param("runId") UUID runId,
            @Param("batchSize") int batchSize);

    @Query(
            value =
                    """
                    SELECT DISTINCT cr.locale
                    FROM campaign_recipients cr
                    WHERE cr.tenant_id = :tenantId
                      AND cr.run_id = :runId
                    """,
            nativeQuery = true)
    List<String> findDistinctLocalesByTenantIdAndRunId(
            @Param("tenantId") UUID tenantId, @Param("runId") UUID runId);
}
