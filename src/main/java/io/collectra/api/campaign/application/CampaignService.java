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
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.Customer;
import io.collectra.api.customer.domain.CustomerSegmentMember;
import io.collectra.api.localization.application.TenantLocaleService;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.receivable.domain.Invoice;
import io.collectra.api.shared.error.BusinessConflictException;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersionStatus;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import java.nio.charset.StandardCharsets;
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
    private final RecipientDestinationResolver destinations;
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
            RecipientDestinationResolver destinations,
            ReceivableService receivables,
            TemplateVersionRepository templates,
            TenantLocaleService tenantLocales,
            ObjectMapper json,
            Clock clock) {
        this.campaigns = campaigns;
        this.runs = runs;
        this.recipients = recipients;
        this.customers = customers;
        this.destinations = destinations;
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
        return create(
                tenantId,
                name,
                templateVersionId,
                channel,
                scheduledAt,
                selection,
                null,
                false,
                createdBy);
    }

    @Transactional
    public Campaign create(
            UUID tenantId,
            String name,
            UUID templateVersionId,
            String channel,
            Instant scheduledAt,
            CampaignSelection selection,
            UUID documentTemplateVersionId,
            boolean generatedPdfLink,
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
        communicationChannel(template.getChannel());

        if (generatedPdfLink) {
            var documentTemplate =
                    templates
                            .findByIdAndTenantId(documentTemplateVersionId, tenantId)
                            .orElseThrow(
                                    () ->
                                            new NoSuchElementException(
                                                    "Document template version not found"));
            if (documentTemplate.getStatus() != TemplateVersionStatus.PUBLISHED) {
                throw new IllegalArgumentException(
                        "Generated PDF link requires a published document template version");
            }
            if (documentTemplate.getChannel() != TemplateChannel.PDF) {
                throw new IllegalArgumentException(
                        "Generated PDF link requires a PDF document template version");
            }
        }

        JsonNode criteria = json.valueToTree(selection == null ? emptySelection() : selection);
        Campaign campaign =
                new Campaign(
                        tenantId,
                        name,
                        templateVersionId,
                        channel,
                        scheduledAt,
                        criteria,
                        createdBy);
        campaign.configureGeneratedPdfLink(documentTemplateVersionId, generatedPdfLink);
        return campaigns.save(campaign);
    }

    @Transactional
    public Campaign activate(UUID tenantId, UUID campaignId) {
        Campaign value = campaign(tenantId, campaignId);
        value.activate();
        return value;
    }

    @Transactional
    public Campaign activate(UUID tenantId, UUID campaignId, long revision) {
        Campaign value = lockedCampaign(tenantId, campaignId);
        requireRevision(value.getVersion(), revision);
        ValidationResult validation = validate(value);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Campaign is invalid: " + validation.errors());
        }
        value.activate();
        return value;
    }

    @Transactional
    public Campaign updateDraft(
            UUID tenantId,
            UUID campaignId,
            String name,
            UUID templateVersionId,
            String channel,
            Instant scheduledAt,
            CampaignSelection selection,
            UUID documentTemplateVersionId,
            boolean generatedPdfLink,
            long revision) {
        Campaign value = lockedCampaign(tenantId, campaignId);
        requireRevision(value.getVersion(), revision);
        validateTemplateConfiguration(
                tenantId, templateVersionId, channel, documentTemplateVersionId, generatedPdfLink);
        JsonNode criteria = json.valueToTree(selection == null ? emptySelection() : selection);
        value.updateDraft(name, templateVersionId, channel, scheduledAt, criteria);
        value.configureGeneratedPdfLink(documentTemplateVersionId, generatedPdfLink);
        return value;
    }

    @Transactional
    public Campaign configureGeneratedPdfAttachment(
            UUID tenantId, UUID campaignId, boolean enabled, boolean required, long revision) {
        Campaign value = lockedCampaign(tenantId, campaignId);
        requireRevision(value.getVersion(), revision);
        if (enabled && communicationChannel(value.getChannel()) != CommunicationChannel.EMAIL) {
            throw new IllegalArgumentException(
                    "Generated PDF attachment is supported for EMAIL campaigns only");
        }
        value.configureGeneratedPdfAttachment(enabled, required);
        return value;
    }

    @Transactional(readOnly = true)
    public Campaign campaign(UUID tenantId, UUID campaignId) {
        return campaigns
                .findByIdAndTenantId(campaignId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Campaign not found"));
    }

    private Campaign lockedCampaign(UUID tenantId, UUID campaignId) {
        return campaigns
                .findLockedByIdAndTenantId(campaignId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Campaign not found"));
    }

    @Transactional(readOnly = true)
    public List<Campaign> campaigns(UUID tenantId) {
        return campaigns.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public PrepareResult prepare(UUID tenantId, UUID campaignId) {
        return prepare(tenantId, campaignId, UUID.randomUUID());
    }

    @Transactional
    public PrepareResult prepare(UUID tenantId, UUID campaignId, UUID commandId) {
        if (commandId == null) {
            throw new IllegalArgumentException("X-Command-Id is required");
        }

        Campaign campaign = lockedCampaign(tenantId, campaignId);
        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            throw new IllegalStateException("Only active campaign can be prepared");
        }

        UUID effectiveCommandId =
                campaign.getScheduledAt() == null ? commandId : scheduledCommandId(campaign);
        var existing =
                runs.findByTenantIdAndCampaignIdAndPrepareCommandId(
                        tenantId, campaignId, effectiveCommandId);
        if (existing.isPresent()) {
            CampaignRun value = existing.get();
            return new PrepareResult(value.getId(), value.getRecipientCount());
        }

        ValidationResult validation = validate(campaign);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Campaign is invalid: " + validation.errors());
        }

        CampaignRun run = runs.save(new CampaignRun(tenantId, campaignId, effectiveCommandId));
        CampaignSelection selection = selection(campaign.getSelectionCriteria());
        int created =
                selection.audienceSelectionType() == AudienceSelectionType.CUSTOMER
                        ? prepareCustomerAudience(tenantId, campaign, run, selection)
                        : prepareReceivableAudience(tenantId, campaign, run, selection);

        Instant now = clock.instant();
        run.ready(created, now);
        if (campaign.getScheduledAt() != null) {
            campaign.markScheduledDispatched(now);
        }
        return new PrepareResult(run.getId(), created);
    }

    @Transactional
    public PrepareResult dispatchScheduled(UUID tenantId, UUID campaignId) {
        Campaign campaign = lockedCampaign(tenantId, campaignId);
        if (campaign.getScheduledAt() == null
                || campaign.getScheduledAt().isAfter(clock.instant())) {
            throw new IllegalStateException("Campaign is not due for scheduled dispatch");
        }
        return prepare(tenantId, campaignId, scheduledCommandId(campaign));
    }

    private int prepareCustomerAudience(
            UUID tenantId, Campaign campaign, CampaignRun run, CampaignSelection selection) {
        int created = 0;
        int pageNumber = 0;
        Page<Customer> page;
        do {
            page =
                    customers.campaignAudienceCandidates(
                            tenantId,
                            selection.customerIds(),
                            selection.segmentIds(),
                            PageRequest.of(
                                    pageNumber,
                                    PREPARE_PAGE_SIZE,
                                    Sort.by(Sort.Direction.ASC, "id")));
            created += prepareCustomerPage(tenantId, campaign, run, page.getContent());
            pageNumber++;
        } while (page.hasNext());
        return created;
    }

    private int prepareCustomerPage(
            UUID tenantId, Campaign campaign, CampaignRun run, List<Customer> pageCustomers) {
        if (pageCustomers.isEmpty()) {
            return 0;
        }

        Set<UUID> customerIds =
                pageCustomers.stream().map(Customer::getId).collect(Collectors.toSet());
        CommunicationChannel channel = communicationChannel(campaign.getChannel());
        Map<UUID, String> destinationByCustomer =
                destinations.resolvePrimary(tenantId, channel, customerIds);

        String tenantDefaultLocale = null;
        int created = 0;
        for (Customer customer : pageCustomers) {
            String destination = destinationByCustomer.get(customer.getId());
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
                            null,
                            campaign.getChannel(),
                            destination,
                            locale));
            created++;
        }
        return created;
    }

    private int prepareReceivableAudience(
            UUID tenantId, Campaign campaign, CampaignRun run, CampaignSelection selection) {
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
            created += prepareReceivablePage(tenantId, campaign, run, selection, page.getContent());
            pageNumber++;
        } while (page.hasNext());
        return created;
    }

    private int prepareReceivablePage(
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
        CommunicationChannel channel = communicationChannel(campaign.getChannel());
        Map<UUID, String> destinationByCustomer =
                destinations.resolvePrimary(tenantId, channel, customerIds);
        Map<UUID, Set<UUID>> segmentIdsByCustomer =
                segmentIdsByCustomer(tenantId, selection, customerIds);

        String tenantDefaultLocale = null;
        int created = 0;
        for (Invoice invoice : invoices) {
            Customer customer = customerById.get(invoice.getCustomerId());
            if (customer == null || !matchesCustomer(customer, selection, segmentIdsByCustomer)) {
                continue;
            }
            String destination = destinationByCustomer.get(customer.getId());
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

    @Transactional(readOnly = true)
    public ValidationResult validate(UUID tenantId, UUID campaignId) {
        return validate(campaign(tenantId, campaignId));
    }

    private ValidationResult validate(Campaign campaign) {
        java.util.ArrayList<ValidationIssue> errors = new java.util.ArrayList<>();
        try {
            validateTemplateConfiguration(
                    campaign.getTenantId(),
                    campaign.getTemplateVersionId(),
                    campaign.getChannel(),
                    campaign.getDocumentTemplateVersionId(),
                    campaign.isGeneratedPdfLink());
        } catch (RuntimeException ex) {
            errors.add(new ValidationIssue("CAMPAIGN_CONFIGURATION_INVALID", ex.getMessage()));
        }
        if (campaign.isGeneratedPdfAttachment()
                && communicationChannel(campaign.getChannel()) != CommunicationChannel.EMAIL) {
            errors.add(
                    new ValidationIssue(
                            "ATTACHMENT_CHANNEL_UNSUPPORTED",
                            "Generated PDF attachment is supported for EMAIL campaigns only"));
        }
        if (campaign.getScheduledAt() != null
                && campaign.getStatus() == CampaignStatus.DRAFT
                && campaign.getScheduledAt().isBefore(clock.instant())) {
            errors.add(
                    new ValidationIssue("SCHEDULE_IN_PAST", "scheduledAt must not be in the past"));
        }
        return new ValidationResult(errors.isEmpty(), List.copyOf(errors), List.of());
    }

    private void validateTemplateConfiguration(
            UUID tenantId,
            UUID templateVersionId,
            String channel,
            UUID documentTemplateVersionId,
            boolean generatedPdfLink) {
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
        communicationChannel(template.getChannel());

        boolean referencesDocumentUrl =
                template.getContentHtml() != null
                        && template.getContentHtml().contains("{{document.url}}");
        if (referencesDocumentUrl && !generatedPdfLink) {
            throw new IllegalArgumentException(
                    "Template references document.url but generatedPdfLink is disabled");
        }

        if (generatedPdfLink) {
            if (documentTemplateVersionId == null) {
                throw new IllegalArgumentException(
                        "documentTemplateVersionId is required for generated PDF link");
            }
            var documentTemplate =
                    templates
                            .findByIdAndTenantId(documentTemplateVersionId, tenantId)
                            .orElseThrow(
                                    () ->
                                            new NoSuchElementException(
                                                    "Document template version not found"));
            if (documentTemplate.getStatus() != TemplateVersionStatus.PUBLISHED) {
                throw new IllegalArgumentException(
                        "Generated PDF link requires a published document template version");
            }
            if (documentTemplate.getChannel() != TemplateChannel.PDF) {
                throw new IllegalArgumentException(
                        "Generated PDF link requires a PDF document template version");
            }
        }
    }

    private UUID scheduledCommandId(Campaign campaign) {
        String value =
                "campaign-scheduled:"
                        + campaign.getTenantId()
                        + ":"
                        + campaign.getId()
                        + ":"
                        + campaign.getScheduledAt();
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private void requireRevision(long actual, long expected) {
        if (actual != expected) {
            throw new BusinessConflictException(
                    "VERSION_CONFLICT",
                    "Campaign revision conflict: expected " + expected + " but was " + actual);
        }
    }

    private CommunicationChannel communicationChannel(String value) {
        try {
            return CommunicationChannel.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported campaign channel: " + value, ex);
        }
    }

    private CommunicationChannel communicationChannel(TemplateChannel value) {
        if (value == TemplateChannel.PDF) {
            throw new IllegalArgumentException("PDF is not a delivery campaign channel");
        }
        return communicationChannel(value.name());
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

    public CampaignSelection selection(Campaign campaign) {
        return selection(campaign.getSelectionCriteria());
    }

    private CampaignSelection selection(JsonNode value) {
        try {
            return json.treeToValue(value, CampaignSelection.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Cannot read campaign selection", ex);
        }
    }

    private CampaignSelection emptySelection() {
        return CampaignSelection.empty(AudienceSelectionType.RECEIVABLE);
    }

    public record PrepareResult(UUID runId, int recipients) {}

    public record ValidationIssue(String code, String message) {}

    public record ValidationResult(
            boolean valid, List<ValidationIssue> errors, List<ValidationIssue> warnings) {}
}
