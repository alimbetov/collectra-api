package io.collectra.api.campaign.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.collectra.api.campaign.domain.Campaign;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.Customer;
import io.collectra.api.customer.domain.CustomerSegmentMember;
import io.collectra.api.localization.application.TenantLocaleService;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.receivable.domain.Invoice;
import io.collectra.api.template.application.CompiledTemplate;
import io.collectra.api.template.application.ResolvedTemplateLocale;
import io.collectra.api.template.application.TemplateCompiler;
import io.collectra.api.template.application.TemplateLocaleResolver;
import io.collectra.api.template.application.TemplateRenderer;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.domain.TemplateVersionStatus;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignPreviewService {
    private static final String PREVIEW_DOCUMENT_URL = "https://preview.invalid/document";

    private final CampaignService campaigns;
    private final CustomerService customers;
    private final ReceivableService receivables;
    private final RecipientDestinationResolver destinations;
    private final CampaignMessagePayloadFactory payloadFactory;
    private final TemplateVersionRepository versions;
    private final TemplateLocaleResolver localeResolver;
    private final TenantLocaleService tenantLocales;
    private final TemplateCompiler compiler;
    private final TemplateRenderer renderer;
    private final Clock clock;

    public CampaignPreviewService(
            CampaignService campaigns,
            CustomerService customers,
            ReceivableService receivables,
            RecipientDestinationResolver destinations,
            CampaignMessagePayloadFactory payloadFactory,
            TemplateVersionRepository versions,
            TemplateLocaleResolver localeResolver,
            TenantLocaleService tenantLocales,
            TemplateCompiler compiler,
            TemplateRenderer renderer,
            Clock clock) {
        this.campaigns = campaigns;
        this.customers = customers;
        this.receivables = receivables;
        this.destinations = destinations;
        this.payloadFactory = payloadFactory;
        this.versions = versions;
        this.localeResolver = localeResolver;
        this.tenantLocales = tenantLocales;
        this.compiler = compiler;
        this.renderer = renderer;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PreviewResult preview(UUID tenantId, UUID campaignId, UUID customerId, UUID invoiceId) {
        Campaign campaign = campaigns.campaign(tenantId, campaignId);
        CampaignService.ValidationResult validation = campaigns.validate(tenantId, campaignId);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Campaign is invalid: " + validation.errors());
        }

        CampaignSelection selection = campaigns.selection(campaign);
        Candidate candidate = candidate(tenantId, selection, customerId, invoiceId);
        CommunicationChannel channel = CommunicationChannel.valueOf(campaign.getChannel());

        String destination =
                destinations
                        .resolvePrimary(tenantId, channel, Set.of(candidate.customer().getId()))
                        .get(candidate.customer().getId());
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException(
                    "Representative customer has no active destination for " + channel);
        }

        String requestedLocale = requestedLocale(tenantId, candidate.customer());
        TemplateVersion anchor =
                versions.findByIdAndTenantId(campaign.getTemplateVersionId(), tenantId)
                        .orElseThrow(
                                () -> new NoSuchElementException("Template version not found"));
        TemplateChannel templateChannel = TemplateChannel.valueOf(campaign.getChannel());
        ResolvedTemplateLocale resolved =
                localeResolver.resolve(
                        tenantId, anchor.getTemplateId(), templateChannel, requestedLocale);
        TemplateVersion selected =
                versions.findFirstByTemplateIdAndLocaleAndChannelAndStatusOrderByTemplateVersionDesc(
                                anchor.getTemplateId(),
                                resolved.resolvedLocale(),
                                templateChannel,
                                TemplateVersionStatus.PUBLISHED)
                        .orElseThrow(
                                () -> new IllegalStateException("Resolved template disappeared"));

        JsonNode payload = payloadFactory.create(candidate.customer(), candidate.invoice());
        if (campaign.isGeneratedPdfLink() && payload instanceof ObjectNode root) {
            root.with("document").put("url", PREVIEW_DOCUMENT_URL);
        }

        String subject = null;
        String body;
        if (channel == CommunicationChannel.EMAIL) {
            CompiledTemplate bodyTemplate = compiler.compile(selected);
            body = renderer.render(bodyTemplate, payload).html();
            subject =
                    renderer.renderText(
                            compiler.compileText(selected.getId(), selected.getSubject()), payload);
        } else {
            body =
                    renderer.renderText(
                            compiler.compileText(selected.getId(), selected.getContentHtml()),
                            payload);
        }

        return new PreviewResult(
                candidate.customer().getId(),
                candidate.invoice() == null ? null : candidate.invoice().getId(),
                channel,
                mask(destination),
                requestedLocale,
                resolved.resolvedLocale(),
                selected.getId(),
                subject,
                body,
                campaign.getDocumentTemplateVersionId(),
                campaign.isGeneratedPdfLink() ? PREVIEW_DOCUMENT_URL : null,
                validation.warnings());
    }

    private Candidate candidate(
            UUID tenantId, CampaignSelection selection, UUID customerId, UUID invoiceId) {
        if (customerId != null) {
            Customer customer = customers.get(tenantId, customerId);
            Invoice invoice = invoiceId == null ? null : receivables.invoice(tenantId, invoiceId);
            if (invoice != null && !invoice.getCustomerId().equals(customer.getId())) {
                throw new IllegalArgumentException(
                        "Invoice does not belong to representative customer");
            }
            return new Candidate(customer, invoice);
        }

        if (selection.audienceSelectionType() == AudienceSelectionType.CUSTOMER) {
            var page =
                    customers.campaignAudienceCandidates(
                            tenantId,
                            selection.customerIds(),
                            selection.segmentIds(),
                            PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "id")));
            if (page.isEmpty()) {
                throw new NoSuchElementException("Campaign audience has no preview candidate");
            }
            return new Candidate(page.getContent().get(0), null);
        }

        LocalDate today = LocalDate.now(clock);
        int pageNumber = 0;
        while (pageNumber < 10) {
            var page =
                    receivables.campaignCandidates(
                            tenantId,
                            selection.customerIds(),
                            selection.amountFrom(),
                            selection.amountTo(),
                            dueDateFrom(selection, today),
                            dueDateTo(selection, today),
                            PageRequest.of(pageNumber, 20, Sort.by(Sort.Direction.ASC, "id")));
            if (page.isEmpty()) {
                break;
            }

            Set<UUID> customerIds =
                    page.getContent().stream()
                            .map(Invoice::getCustomerId)
                            .collect(Collectors.toSet());
            Map<UUID, Customer> customerById =
                    customers.customersByIds(tenantId, customerIds).stream()
                            .collect(Collectors.toMap(Customer::getId, value -> value));
            Map<UUID, Set<UUID>> segmentIdsByCustomer =
                    segmentIdsByCustomer(tenantId, selection, customerIds);

            for (Invoice invoice : page.getContent()) {
                Customer customer = customerById.get(invoice.getCustomerId());
                if (customer != null
                        && matchesSegments(customer, selection, segmentIdsByCustomer)) {
                    return new Candidate(customer, invoice);
                }
            }
            if (!page.hasNext()) {
                break;
            }
            pageNumber++;
        }
        throw new NoSuchElementException("Campaign audience has no preview candidate");
    }

    private Map<UUID, Set<UUID>> segmentIdsByCustomer(
            UUID tenantId, CampaignSelection selection, Set<UUID> customerIds) {
        if (selection.segmentIds().isEmpty()) {
            return Map.of();
        }
        return customers.segmentMembershipsByCustomerIds(tenantId, customerIds).stream()
                .collect(
                        Collectors.groupingBy(
                                CustomerSegmentMember::getCustomerId,
                                Collectors.mapping(
                                        CustomerSegmentMember::getSegmentId, Collectors.toSet())));
    }

    private boolean matchesSegments(
            Customer customer,
            CampaignSelection selection,
            Map<UUID, Set<UUID>> segmentIdsByCustomer) {
        if (selection.segmentIds().isEmpty()) {
            return true;
        }
        Set<UUID> assigned = segmentIdsByCustomer.getOrDefault(customer.getId(), Set.of());
        return selection.segmentIds().stream().anyMatch(assigned::contains);
    }

    private String requestedLocale(UUID tenantId, Customer customer) {
        String locale = customer.getPreferredLocale();
        return locale == null || locale.isBlank()
                ? tenantLocales.requireDefault(tenantId).getLocale()
                : locale.trim();
    }

    private LocalDate dueDateFrom(CampaignSelection selection, LocalDate today) {
        return selection.daysOverdueTo() == null
                ? null
                : today.minusDays(selection.daysOverdueTo());
    }

    private LocalDate dueDateTo(CampaignSelection selection, LocalDate today) {
        if (selection.daysOverdueFrom() == null && selection.daysOverdueTo() == null) {
            return null;
        }
        long minimum =
                selection.daysOverdueFrom() == null
                        ? 1L
                        : Math.max(1L, selection.daysOverdueFrom());
        return today.minusDays(minimum);
    }

    private String mask(String destination) {
        int at = destination.indexOf('@');
        if (at > 1) {
            return destination.substring(0, 1) + "***" + destination.substring(at);
        }
        String digits = destination.replaceAll("\\D", "");
        return digits.length() >= 4 ? "***" + digits.substring(digits.length() - 4) : "***";
    }

    private record Candidate(Customer customer, Invoice invoice) {}

    public record PreviewResult(
            UUID customerId,
            UUID invoiceId,
            CommunicationChannel channel,
            String destination,
            String requestedLocale,
            String resolvedLocale,
            UUID messageTemplateVersionId,
            String subject,
            String body,
            UUID documentTemplateVersionId,
            String documentUrlPreview,
            java.util.List<CampaignService.ValidationIssue> warnings) {}
}
