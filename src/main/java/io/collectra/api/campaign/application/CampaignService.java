package io.collectra.api.campaign.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.campaign.domain.Campaign;
import io.collectra.api.campaign.domain.CampaignRecipient;
import io.collectra.api.campaign.domain.CampaignRun;
import io.collectra.api.campaign.domain.CampaignStatus;
import io.collectra.api.campaign.infrastructure.CampaignRecipientRepository;
import io.collectra.api.campaign.infrastructure.CampaignRepository;
import io.collectra.api.campaign.infrastructure.CampaignRunRepository;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.Customer;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.receivable.domain.Invoice;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersionStatus;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignService {
    private final CampaignRepository campaigns;
    private final CampaignRunRepository runs;
    private final CampaignRecipientRepository recipients;
    private final CustomerService customers;
    private final ReceivableService receivables;
    private final TemplateVersionRepository templates;
    private final ObjectMapper json;

    public CampaignService(
            CampaignRepository campaigns,
            CampaignRunRepository runs,
            CampaignRecipientRepository recipients,
            CustomerService customers,
            ReceivableService receivables,
            TemplateVersionRepository templates,
            ObjectMapper json) {
        this.campaigns = campaigns;
        this.runs = runs;
        this.recipients = recipients;
        this.customers = customers;
        this.receivables = receivables;
        this.templates = templates;
        this.json = json;
    }

    @Transactional
    public Campaign create(
            UUID tenantId,
            String name,
            UUID templateVersionId,
            String channel,
            Instant scheduledAt,
            CampaignSelection selection,
            UUID createdBy) {
        var template =
                templates
                        .findByIdAndTenantId(templateVersionId, tenantId)
                        .orElseThrow(() -> new NoSuchElementException("Template version not found"));
        if (template.getStatus() != TemplateVersionStatus.PUBLISHED) {
            throw new IllegalArgumentException("Campaign requires a published template version");
        }
        if (!template.getChannel().name().equalsIgnoreCase(channel)) {
            throw new IllegalArgumentException("Campaign channel differs from template channel");
        }
        if (template.getChannel() != TemplateChannel.EMAIL) {
            throw new IllegalArgumentException("Only EMAIL campaign is supported in this slice");
        }
        JsonNode criteria = json.valueToTree(selection == null ? emptySelection() : selection);
        return campaigns.save(
                new Campaign(
                        tenantId,
                        name,
                        templateVersionId,
                        channel,
                        scheduledAt,
                        criteria,
                        createdBy));
    }

    @Transactional
    public Campaign activate(UUID tenantId, UUID campaignId) {
        Campaign value = campaign(tenantId, campaignId);
        value.activate();
        return value;
    }

    @Transactional(readOnly = true)
    public Campaign campaign(UUID tenantId, UUID campaignId) {
        return campaigns
                .findByIdAndTenantId(campaignId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Campaign not found"));
    }

    @Transactional(readOnly = true)
    public List<Campaign> campaigns(UUID tenantId) {
        return campaigns.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public PrepareResult prepare(UUID tenantId, UUID campaignId) {
        Campaign campaign = campaign(tenantId, campaignId);
        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            throw new IllegalStateException("Only active campaign can be prepared");
        }
        CampaignRun run = runs.save(new CampaignRun(tenantId, campaignId));
        CampaignSelection selection = selection(campaign.getSelectionCriteria());
        LocalDate today = LocalDate.now();
        Map<UUID, Customer> customerById = new HashMap<>();
        customers.list(tenantId).forEach(value -> customerById.put(value.getId(), value));
        int created = 0;
        for (Invoice invoice : receivables.invoices(tenantId)) {
            Customer customer = customerById.get(invoice.getCustomerId());
            if (customer == null || !matchesCustomer(tenantId, customer, selection)) {
                continue;
            }
            if (!matchesInvoice(invoice, selection, today)) {
                continue;
            }
            String destination = emailDestination(tenantId, customer.getId());
            if (destination == null) {
                continue;
            }
            recipients.save(
                    new CampaignRecipient(
                            tenantId,
                            campaign.getId(),
                            run.getId(),
                            customer.getId(),
                            invoice.getId(),
                            campaign.getChannel(),
                            destination,
                            customer.getPreferredLocale()));
            created++;
        }
        run.ready();
        return new PrepareResult(run.getId(), created);
    }

    @Transactional(readOnly = true)
    public List<CampaignRun> runs(UUID tenantId, UUID campaignId) {
        campaign(tenantId, campaignId);
        return runs.findAllByTenantIdAndCampaignIdOrderByCreatedAtDesc(tenantId, campaignId);
    }

    @Transactional(readOnly = true)
    public CampaignRun run(UUID tenantId, UUID runId) {
        return runs
                .findByIdAndTenantId(runId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Campaign run not found"));
    }

    @Transactional(readOnly = true)
    public List<CampaignRecipient> recipients(UUID tenantId, UUID runId) {
        run(tenantId, runId);
        return recipients.findAllByTenantIdAndRunIdOrderByCreatedAtAsc(tenantId, runId);
    }

    private boolean matchesCustomer(UUID tenantId, Customer customer, CampaignSelection selection) {
        if (!selection.customerIds().isEmpty() && !selection.customerIds().contains(customer.getId())) {
            return false;
        }
        if (selection.segmentIds().isEmpty()) {
            return true;
        }
        Set<UUID> assigned = new HashSet<>(customers.segmentIds(tenantId, customer.getId()));
        return selection.segmentIds().stream().anyMatch(assigned::contains);
    }

    private boolean matchesInvoice(Invoice invoice, CampaignSelection selection, LocalDate today) {
        if (invoice.getOutstandingAmount().signum() <= 0) {
            return false;
        }
        BigDecimal amount = invoice.getOutstandingAmount();
        if (selection.amountFrom() != null && amount.compareTo(selection.amountFrom()) < 0) {
            return false;
        }
        if (selection.amountTo() != null && amount.compareTo(selection.amountTo()) > 0) {
            return false;
        }
        if (selection.daysOverdueFrom() == null && selection.daysOverdueTo() == null) {
            return true;
        }
        if (!invoice.isOverdue(today)) {
            return false;
        }
        long days = ChronoUnit.DAYS.between(invoice.getDueDate(), today);
        if (selection.daysOverdueFrom() != null && days < selection.daysOverdueFrom()) {
            return false;
        }
        return selection.daysOverdueTo() == null || days <= selection.daysOverdueTo();
    }

    private String emailDestination(UUID tenantId, UUID customerId) {
        var active =
                customers.emails(tenantId, customerId).stream()
                        .filter(value -> "ACTIVE".equals(value.getStatus()))
                        .toList();
        return active.stream()
                .filter(value -> value.isPrimary())
                .findFirst()
                .or(() -> active.stream().findFirst())
                .map(value -> value.getEmail())
                .orElse(null);
    }

    private CampaignSelection selection(JsonNode value) {
        try {
            return json.treeToValue(value, CampaignSelection.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Cannot read campaign selection", ex);
        }
    }

    private CampaignSelection emptySelection() {
        return new CampaignSelection(Set.of(), Set.of(), null, null, null, null);
    }

    public record PrepareResult(UUID runId, int recipients) {}
}
