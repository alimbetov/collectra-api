package io.collectra.api.campaign.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.campaign.domain.Campaign;
import io.collectra.api.campaign.domain.CampaignRecipient;
import io.collectra.api.campaign.domain.CampaignRun;
import io.collectra.api.campaign.domain.CampaignRunStatus;
import io.collectra.api.campaign.domain.CampaignRunTemplateBinding;
import io.collectra.api.campaign.infrastructure.CampaignRecipientRepository;
import io.collectra.api.campaign.infrastructure.CampaignRepository;
import io.collectra.api.campaign.infrastructure.CampaignRunRepository;
import io.collectra.api.communication.application.MessageDeliveryEventPublisher;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.infrastructure.MessageRepository;
import io.collectra.api.template.application.CompiledTemplate;
import io.collectra.api.template.application.TemplateCompiler;
import io.collectra.api.template.application.TemplateRenderer;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignMessageMaterializer {
    private static final int MAX_BATCH_SIZE = 1000;

    private final CampaignRunRepository runs;
    private final CampaignRepository campaigns;
    private final CampaignRecipientRepository recipients;
    private final CampaignRunTemplateBindingService bindingService;
    private final CampaignEligibilityService eligibility;
    private final CampaignMessagePayloadFactory payloadFactory;
    private final TemplateVersionRepository templateVersions;
    private final TemplateCompiler compiler;
    private final TemplateRenderer renderer;
    private final MessageRepository messages;
    private final MessageDeliveryEventPublisher deliveryEvents;
    private final Clock clock;

    public CampaignMessageMaterializer(
            CampaignRunRepository runs,
            CampaignRepository campaigns,
            CampaignRecipientRepository recipients,
            CampaignRunTemplateBindingService bindingService,
            CampaignEligibilityService eligibility,
            CampaignMessagePayloadFactory payloadFactory,
            TemplateVersionRepository templateVersions,
            TemplateCompiler compiler,
            TemplateRenderer renderer,
            MessageRepository messages,
            MessageDeliveryEventPublisher deliveryEvents,
            Clock clock) {
        this.runs = runs;
        this.campaigns = campaigns;
        this.recipients = recipients;
        this.bindingService = bindingService;
        this.eligibility = eligibility;
        this.payloadFactory = payloadFactory;
        this.templateVersions = templateVersions;
        this.compiler = compiler;
        this.renderer = renderer;
        this.messages = messages;
        this.deliveryEvents = deliveryEvents;
        this.clock = clock;
    }

    @Transactional
    public MaterializationBatchResult materializeNextBatch(
            UUID tenantId, UUID campaignRunId, int batchSize) {
        requireBatchSize(batchSize);
        CampaignRun run =
                runs.findLockedByIdAndTenantId(campaignRunId, tenantId)
                        .orElseThrow(() -> new NoSuchElementException("Campaign run not found"));
        requireMaterializable(run);

        Campaign campaign =
                campaigns
                        .findByIdAndTenantId(run.getCampaignId(), tenantId)
                        .orElseThrow(() -> new NoSuchElementException("Campaign not found"));

        if (run.getStatus() == CampaignRunStatus.READY) {
            bindingService.initialize(tenantId, campaign, run);
            run.start(Instant.now(clock));
        }

        List<CampaignRecipient> batch =
                recipients.findMaterializationCandidates(tenantId, campaignRunId, batchSize);
        if (batch.isEmpty()) {
            return new MaterializationBatchResult(0, 0, 0, false);
        }

        CampaignEligibilityService.EligibilityBatch evaluated =
                eligibility.evaluateBatch(tenantId, batch);
        Map<String, CampaignRunTemplateBinding> bindingByLocale =
                bindingService.bindingsByRequestedLocale(tenantId, campaignRunId);

        Set<UUID> templateVersionIds =
                bindingByLocale.values().stream()
                        .map(CampaignRunTemplateBinding::getTemplateVersionId)
                        .collect(Collectors.toSet());
        Map<UUID, TemplateVersion> versionById =
                templateVersions.findAllByIdsAndTenantId(templateVersionIds, tenantId).stream()
                        .collect(Collectors.toMap(TemplateVersion::getId, Function.identity()));
        if (versionById.size() != templateVersionIds.size()) {
            throw new IllegalStateException(
                    "A snapshotted template version is missing or outside tenant");
        }

        Map<UUID, CompiledTemplate> compiledBodies = new HashMap<>();
        Map<UUID, CompiledTemplate> compiledSubjects = new HashMap<>();
        int queued = 0;
        for (CampaignRecipient recipient : batch) {
            CampaignEligibilityService.EligibleRecipientContext context =
                    evaluated.eligibleByRecipientId().get(recipient.getId());
            if (context == null) {
                continue;
            }

            String requestedLocale = requireSnapshotLocale(recipient.getLocale());
            CampaignRunTemplateBinding binding = bindingByLocale.get(requestedLocale);
            if (binding == null) {
                throw new IllegalStateException(
                        "No template binding for run "
                                + campaignRunId
                                + " and locale "
                                + requestedLocale);
            }
            TemplateVersion version = versionById.get(binding.getTemplateVersionId());
            if (version == null) {
                throw new IllegalStateException(
                        "Template version not loaded: " + binding.getTemplateVersionId());
            }

            JsonNode payload = payloadFactory.create(context.customer(), context.invoice());
            CompiledTemplate subjectTemplate =
                    compiledSubjects.computeIfAbsent(
                            version.getId(), id -> compiler.compileText(id, version.getSubject()));
            CompiledTemplate bodyTemplate =
                    compiledBodies.computeIfAbsent(
                            version.getId(), id -> compiler.compile(version));
            String subject = renderer.renderText(subjectTemplate, payload);
            String body = renderer.render(bodyTemplate, payload).html();

            Message message =
                    messages.save(
                            Message.queued(
                                    tenantId,
                                    campaign.getId(),
                                    campaignRunId,
                                    recipient.getId(),
                                    recipient.getCustomerId(),
                                    recipient.getInvoiceId(),
                                    version.getId(),
                                    CommunicationChannel.EMAIL,
                                    recipient.getDestination(),
                                    binding.getResolvedLocale(),
                                    subject,
                                    body));
            deliveryEvents.requestDelivery(tenantId, message.getId());
            queued++;
        }

        recipients.flush();
        messages.flush();
        boolean hasNext =
                !recipients.findMaterializationCandidates(tenantId, campaignRunId, 1).isEmpty();
        return new MaterializationBatchResult(batch.size(), queued, evaluated.skipped(), hasNext);
    }

    private static void requireMaterializable(CampaignRun run) {
        if (run.getStatus() != CampaignRunStatus.READY
                && run.getStatus() != CampaignRunStatus.RUNNING) {
            throw new IllegalStateException(
                    "Campaign run cannot be materialized from " + run.getStatus());
        }
    }

    private static void requireBatchSize(int batchSize) {
        if (batchSize <= 0 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batchSize must be between 1 and " + MAX_BATCH_SIZE);
        }
    }

    private static String requireSnapshotLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            throw new IllegalStateException("Campaign recipient locale snapshot is missing");
        }
        return locale.trim();
    }

    public record MaterializationBatchResult(
            int selected, int queued, int skipped, boolean hasNext) {}
}
