package io.collectra.api.campaign.application;

import io.collectra.api.campaign.domain.CampaignStatus;
import io.collectra.api.campaign.infrastructure.CampaignRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CampaignDispatchScheduler {
    private static final Logger log = LoggerFactory.getLogger(CampaignDispatchScheduler.class);

    private final CampaignRepository campaigns;
    private final CampaignService service;
    private final Clock clock;
    private final int batchSize;

    public CampaignDispatchScheduler(
            CampaignRepository campaigns,
            CampaignService service,
            Clock clock,
            @Value("${collectra.campaign.dispatch-batch-size:100}") int batchSize) {
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("dispatch-batch-size must be between 1 and 500");
        }
        this.campaigns = campaigns;
        this.service = service;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(
            cron = "${collectra.campaign.dispatch-cron:0 * * * * *}",
            zone = "${collectra.business-zone:Asia/Almaty}")
    public void dispatchDueCampaigns() {
        var due =
                campaigns.findDueScheduled(
                        CampaignStatus.ACTIVE, clock.instant(), PageRequest.of(0, batchSize));
        for (var campaign : due) {
            try {
                var result = service.dispatchScheduled(campaign.getTenantId(), campaign.getId());
                log.info(
                        "Scheduled campaign dispatched. tenantId={}, campaignId={}, runId={}, recipients={}",
                        campaign.getTenantId(),
                        campaign.getId(),
                        result.runId(),
                        result.recipients());
            } catch (RuntimeException ex) {
                log.error(
                        "Scheduled campaign dispatch failed. tenantId={}, campaignId={}",
                        campaign.getTenantId(),
                        campaign.getId(),
                        ex);
            }
        }
    }
}
