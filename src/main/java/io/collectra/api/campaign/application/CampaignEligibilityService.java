package io.collectra.api.campaign.application;

import io.collectra.api.campaign.domain.CampaignRecipient;
import io.collectra.api.campaign.infrastructure.CampaignRecipientRepository;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.Customer;
import io.collectra.api.customer.domain.CustomerEmail;
import io.collectra.api.customer.domain.CustomerStatus;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.receivable.domain.Invoice;
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
        if (values.isEmpty()) {
            return new EligibilityResult(0, 0, 0);
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
        Map<UUID, List<CustomerEmail>> emailsByCustomer =
                customers.emailsByCustomerIds(tenantId, customerIds).stream()
                        .collect(Collectors.groupingBy(CustomerEmail::getCustomerId));
        Map<UUID, Invoice> invoiceById =
                receivables.invoicesByIds(tenantId, invoiceIds).stream()
                        .collect(Collectors.toMap(Invoice::getId, value -> value));

        int eligible = 0;
        int skipped = 0;
        for (CampaignRecipient recipient : values) {
            Customer customer = customerById.get(recipient.getCustomerId());
            if (customer == null || customer.getStatus() != CustomerStatus.ACTIVE) {
                recipient.skip("CUSTOMER_INACTIVE");
                skipped++;
                continue;
            }
            boolean contactExists =
                    emailsByCustomer.getOrDefault(customer.getId(), List.of()).stream()
                            .anyMatch(
                                    email ->
                                            "ACTIVE".equals(email.getStatus())
                                                    && recipient.getDestination() != null
                                                    && email.getEmail()
                                                            .equalsIgnoreCase(
                                                                    recipient.getDestination()));
            if (!contactExists) {
                recipient.skip("NO_CONTACT");
                skipped++;
                continue;
            }
            if (recipient.getInvoiceId() != null) {
                Invoice invoice = invoiceById.get(recipient.getInvoiceId());
                if (invoice == null || invoice.getOutstandingAmount().signum() <= 0) {
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
