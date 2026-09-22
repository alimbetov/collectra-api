package io.collectra.api.campaign.application;

import io.collectra.api.campaign.domain.CampaignRecipient;
import io.collectra.api.campaign.infrastructure.CampaignRecipientRepository;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.Customer;
import io.collectra.api.customer.domain.CustomerStatus;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.receivable.domain.Invoice;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignEligibilityService {
    private final CampaignRecipientRepository recipients;
    private final CustomerService customers;
    private final RecipientDestinationResolver destinations;
    private final ReceivableService receivables;

    public CampaignEligibilityService(
            CampaignRecipientRepository recipients,
            CustomerService customers,
            RecipientDestinationResolver destinations,
            ReceivableService receivables) {
        this.recipients = recipients;
        this.customers = customers;
        this.destinations = destinations;
        this.receivables = receivables;
    }

    @Transactional
    public EligibilityResult recheck(UUID tenantId, UUID runId) {
        EligibilityBatch batch =
                evaluateBatch(
                        tenantId,
                        recipients.findAllByTenantIdAndRunIdOrderByCreatedAtAsc(tenantId, runId));
        return new EligibilityResult(batch.total(), batch.eligibleCount(), batch.skipped());
    }

    public EligibilityBatch evaluateBatch(UUID tenantId, List<CampaignRecipient> values) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(values, "recipients are required");
        if (values.isEmpty()) {
            return new EligibilityBatch(0, 0, 0, Map.of());
        }
        if (values.stream().anyMatch(recipient -> !tenantId.equals(recipient.getTenantId()))) {
            throw new IllegalArgumentException("Eligibility batch contains another tenant");
        }

        Set<UUID> customerIds =
                values.stream().map(CampaignRecipient::getCustomerId).collect(Collectors.toSet());
        Set<UUID> invoiceIds =
                values.stream()
                        .map(CampaignRecipient::getInvoiceId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        Map<UUID, Customer> customerById =
                customers.customersByIds(tenantId, customerIds).stream()
                        .collect(Collectors.toMap(Customer::getId, value -> value));
        CommunicationChannel channel = batchChannel(values);
        Map<UUID, Set<String>> activeDestinations =
                destinations.activeDestinations(tenantId, channel, customerIds);
        Map<UUID, Invoice> invoiceById =
                receivables.invoicesByIds(tenantId, invoiceIds).stream()
                        .collect(Collectors.toMap(Invoice::getId, value -> value));

        Map<UUID, EligibleRecipientContext> eligible = new LinkedHashMap<>();
        int skipped = 0;
        for (CampaignRecipient recipient : values) {
            Customer customer = customerById.get(recipient.getCustomerId());
            if (customer == null || customer.getStatus() != CustomerStatus.ACTIVE) {
                recipient.skip("CUSTOMER_INACTIVE");
                skipped++;
                continue;
            }
            boolean contactExists =
                    recipient.getDestination() != null
                            && activeDestinations
                                    .getOrDefault(customer.getId(), Set.of())
                                    .contains(recipient.getDestination());
            if (!contactExists) {
                recipient.skip("NO_CONTACT");
                skipped++;
                continue;
            }

            Invoice invoice = null;
            if (recipient.getInvoiceId() != null) {
                invoice = invoiceById.get(recipient.getInvoiceId());
                if (invoice == null || invoice.getOutstandingAmount().signum() <= 0) {
                    recipient.skip("PAID");
                    skipped++;
                    continue;
                }
            }

            recipient.eligible();
            eligible.put(recipient.getId(), new EligibleRecipientContext(customer, invoice));
        }
        return new EligibilityBatch(values.size(), eligible.size(), skipped, Map.copyOf(eligible));
    }

    private static CommunicationChannel batchChannel(List<CampaignRecipient> values) {
        String value = values.get(0).getChannel();
        if (values.stream().anyMatch(recipient -> !value.equals(recipient.getChannel()))) {
            throw new IllegalArgumentException("Eligibility batch contains multiple channels");
        }
        try {
            return CommunicationChannel.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported campaign channel: " + value, ex);
        }
    }

    public record EligibleRecipientContext(Customer customer, Invoice invoice) {}

    public record EligibilityBatch(
            int total,
            int eligibleCount,
            int skipped,
            Map<UUID, EligibleRecipientContext> eligibleByRecipientId) {
        public EligibleRecipientContext contextFor(UUID recipientId) {
            EligibleRecipientContext context = eligibleByRecipientId.get(recipientId);
            if (context == null) {
                throw new IllegalArgumentException("Recipient is not eligible: " + recipientId);
            }
            return context;
        }
    }

    public record EligibilityResult(int total, int eligible, int skipped) {}
}
