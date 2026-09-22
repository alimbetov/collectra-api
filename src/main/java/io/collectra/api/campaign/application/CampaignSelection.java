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
        BigDecimal amountTo,
        AudienceSelectionType audienceSelectionType) {

    public CampaignSelection {
        customerIds = customerIds == null ? Set.of() : Set.copyOf(customerIds);
        segmentIds = segmentIds == null ? Set.of() : Set.copyOf(segmentIds);
        audienceSelectionType =
                audienceSelectionType == null
                        ? AudienceSelectionType.RECEIVABLE
                        : audienceSelectionType;
        if (audienceSelectionType == AudienceSelectionType.CUSTOMER
                && (daysOverdueFrom != null
                        || daysOverdueTo != null
                        || amountFrom != null
                        || amountTo != null)) {
            throw new IllegalArgumentException(
                    "CUSTOMER audience does not support receivable filters");
        }
    }

    public CampaignSelection(
            Set<UUID> customerIds,
            Set<UUID> segmentIds,
            Integer daysOverdueFrom,
            Integer daysOverdueTo,
            BigDecimal amountFrom,
            BigDecimal amountTo) {
        this(
                customerIds,
                segmentIds,
                daysOverdueFrom,
                daysOverdueTo,
                amountFrom,
                amountTo,
                AudienceSelectionType.RECEIVABLE);
    }
}
