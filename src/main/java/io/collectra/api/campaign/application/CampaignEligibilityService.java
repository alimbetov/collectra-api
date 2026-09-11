package io.collectra.api.campaign.application;

import io.collectra.api.campaign.domain.CampaignRecipient;
import io.collectra.api.campaign.infrastructure.CampaignRecipientRepository;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.CustomerStatus;
import io.collectra.api.receivable.application.ReceivableService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignEligibilityService {
    private final CampaignRecipientRepository recipients;
    private final CustomerService customers;
    private final ReceivableService receivables;

    public CampaignEligibilityService(
            CampaignRecipientRepository recipients,
            CustomerService customers,
            ReceivableService receivables) {
        this.recipients = recipients;
        this.customers = customers;
        this.receivables = receivables;
    }

    @Transactional
    public EligibilityResult recheck(UUID tenantId, UUID runId) {
        List<CampaignRecipient> values =
                recipients.findAllByTenantIdAndRunIdOrderByCreatedAtAsc(tenantId, runId);
        int eligible = 0;
        int skipped = 0;
        for (CampaignRecipient recipient : values) {
            var customer = customers.get(tenantId, recipient.getCustomerId());
            if (customer.getStatus() != CustomerStatus.ACTIVE) {
                recipient.skip("CUSTOMER_INACTIVE");
                skipped++;
                continue;
            }
            boolean contactExists =
                    customers.emails(tenantId, customer.getId()).stream()
                            .anyMatch(
                                    email ->
                                            "ACTIVE".equals(email.getStatus())
                                                    && email.getEmail()
                                                            .equalsIgnoreCase(
                                                                    recipient.getDestination()));
            if (!contactExists) {
                recipient.skip("NO_CONTACT");
                skipped++;
                continue;
            }
            if (recipient.getInvoiceId() != null) {
                var invoice = receivables.invoice(tenantId, recipient.getInvoiceId());
                if (invoice.getOutstandingAmount().signum() <= 0) {
                    recipient.skip("PAID");
                    skipped++;
                    continue;
                }
            }
            recipient.eligible();
            eligible++;
        }
        return new EligibilityResult(values.size(), eligible, skipped);
    }

    public record EligibilityResult(int total, int eligible, int skipped) {}
}
