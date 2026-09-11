package io.collectra.api.campaign.application;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record CampaignSelection(
        Set<UUID> customerIds,
        Set<UUID> segmentIds,
        Integer daysOverdueFrom,
        Integer daysOverdueTo,
        BigDecimal amountFrom,
        BigDecimal amountTo) {
    public CampaignSelection {
        customerIds = customerIds == null ? Set.of() : Set.copyOf(customerIds);
        segmentIds = segmentIds == null ? Set.of() : Set.copyOf(segmentIds);
    }
}
