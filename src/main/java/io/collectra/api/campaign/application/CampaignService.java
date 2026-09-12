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
import io.collectra.api.customer.domain.CustomerEmail;
import io.collectra.api.customer.domain.CustomerSegmentMember;
import io.collectra.api.localization.application.TenantLocaleService;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.receivable.domain.Invoice;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersionStatus;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignService {
    private static final int PREPARE_PAGE_SIZE = 500;

    private final CampaignRepository campaigns;
    private final CampaignRunRepository runs;
    private final CampaignRecipientRepository recipients;
    private final CustomerService customers;
    private final ReceivableService receivables;
    private final TemplateVersionRepository templates;
    private final TenantLocaleService tenantLocales;
    private final ObjectMapper json;
    private final Clock clock;

    public CampaignService(
            CampaignRepository campaigns,
            CampaignRunRepository runs,
            CampaignRecipientRepository recipients,
            CustomerService customers,
            ReceivableService receivables,
            TemplateVersionRepository templates,
            TenantLocaleService tenantLocales,
            ObjectMapper json,
            Clock clock) {
        this.campaigns = campaigns;
        this.runs = runs;
        this.recipients = recipients;
        this.customers = customers;
        this.receivables = receivables;
        this.templates = templates;
        this.tenantLocales = tenantLocales;
        this.json = json;
        this.clock = clock;
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
                        .orElseThrow(
                                () -> new NoSuchElementException("Template version not found"));
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
        LocalDate today = LocalDate.now(clock);
        LocalDate dueDateFrom = dueDateFrom(selection, today);
        LocalDate dueDateTo = dueDateTo(selection, today);

        int created = 0;
        int pageNumber = 0;
        Page<Invoice> page;
        do {
            page =
                    receivables.campaignCandidates(
                            tenantId,
                            selection.customerIds(),
                            selection.amountFrom(),
                            selection.amountTo(),
                            dueDateFrom,
                            dueDateTo,
                            PageRequest.of(
                                    pageNumber,
                                    PREPARE_PAGE_SIZE,
                                    Sort.by(Sort.Direction.ASC, "id")));
            created +=
                    preparePage(
                            tenantId,
                            campaign,
                            run,
                            selection,
                            page.getContent());
            pageNumber++;
        } while (page.hasNext());

        run.ready(created, Instant.now(clock));
        return new PrepareResult(run.getId(), created);
    }

    private int preparePage(
            UUID tenantId,
            Campaign campaign,
            CampaignRun run,
            CampaignSelection selection,
            List<Invoice> invoices) {
        if (invoices.isEmpty()) {
            return 0;
        }

        Set<UUID> customerIds =
                invoices.stream().map(Invoice::getCustomerId).collect(Collectors.toSet());
        Map<UUID, Customer> customerById =
                customers.customersByIds(tenantId, customerIds).stream()
                        .collect(Collectors.toMap(Customer::getId, value -> value));
        Map<UUID, List<CustomerEmail>> emailsByCustomer =
                customers.emailsByCustomerIds(tenantId, customerIds).stream()
                        .collect(Collectors.groupingBy(CustomerEmail::getCustomerId));
        Map<UUID, Set<UUID>> segmentIdsByCustomer =
                segmentIdsByCustomer(tenantId, selection, customerIds);

        String tenantDefaultLocale = null;
        int created = 0;
        for (Invoice invoice : invoices) {
            Customer customer = customerById.get(invoice.getCustomerId());
            if (customer == null || !matchesCustomer(customer, selection, segmentIdsByCustomer)) {
                continue;
            }
            String destination = emailDestination(emailsByCustomer.get(customer.getId()));
            String locale = customer.getPreferredLocale();
            if (locale == null || locale.isBlank()) {
                if (tenantDefaultLocale == null) {
                    tenantDefaultLocale = tenantLocales.requireDefault(tenantId).getLocale();
                }
                locale = tenantDefaultLocale;
            } else {
                locale = locale.trim();
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
                            locale));
            created++;
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<CampaignRun> runs(UUID tenantId, UUID campaignId) {
        campaign(tenantId, campaignId);
        return runs.findAllByTenantIdAndCampaignIdOrderByCreatedAtDesc(tenantId, campaignId);
    }

    @Transactional(readOnly = true)
    public CampaignRun run(UUID tenantId, UUID runId) {
        return runs.findByIdAndTenantId(runId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Campaign run not found"));
    }

    @Transactional(readOnly = true)
    public List<CampaignRecipient> recipients(UUID tenantId, UUID runId) {
        run(tenantId, runId);
        return recipients.findAllByTenantIdAndRunIdOrderByCreatedAtAsc(tenantId, runId);
    }

    private Map<UUID, Set<UUID>> segmentIdsByCustomer(
            UUID tenantId, CampaignSelection selection, Set<UUID> customerIds) {
        if (selection.segmentIds().isEmpty()) {
            return Map.of();
        }
        Map<UUID, Set<UUID>> result = new HashMap<>();
        for (CustomerSegmentMember member :
                customers.segmentMembershipsByCustomerIds(tenantId, customerIds)) {
            result.computeIfAbsent(member.getCustomerId(), ignored -> new HashSet<>())
                    .add(member.getSegmentId());
        }
        return result;
    }

    private boolean matchesCustomer(
            Customer customer,
            CampaignSelection selection,
            Map<UUID, Set<UUID>> segmentIdsByCustomer) {
        if (!selection.customerIds().isEmpty()
                && !selection.customerIds().contains(customer.getId())) {
            return false;
        }
        if (selection.segmentIds().isEmpty()) {
            return true;
        }
        Set<UUID> assigned = segmentIdsByCustomer.getOrDefault(customer.getId(), Set.of());
        return selection.segmentIds().stream().anyMatch(assigned::contains);
    }

    private String emailDestination(List<CustomerEmail> emails) {
        if (emails == null || emails.isEmpty()) {
            return null;
        }
        List<CustomerEmail> active =
                emails.stream().filter(value -> "ACTIVE".equals(value.getStatus())).toList();
        return active.stream()
                .filter(CustomerEmail::isPrimary)
                .findFirst()
                .or(() -> active.stream().findFirst())
                .map(CustomerEmail::getEmail)
                .orElse(null);
    }

    private LocalDate dueDateFrom(CampaignSelection selection, LocalDate today) {
        if (selection.daysOverdueTo() == null) {
            return null;
        }
        return today.minusDays(selection.daysOverdueTo());
    }

    private LocalDate dueDateTo(CampaignSelection selection, LocalDate today) {
        if (selection.daysOverdueFrom() == null && selection.daysOverdueTo() == null) {
            return null;
        }
        long minimumDaysOverdue =
                selection.daysOverdueFrom() == null
                        ? 1L
                        : Math.max(1L, selection.daysOverdueFrom());
        return today.minusDays(minimumDaysOverdue);
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
