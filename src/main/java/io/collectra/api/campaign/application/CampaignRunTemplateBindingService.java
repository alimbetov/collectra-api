package io.collectra.api.campaign.application;

import io.collectra.api.campaign.domain.Campaign;
import io.collectra.api.campaign.domain.CampaignRun;
import io.collectra.api.campaign.domain.CampaignRunTemplateBinding;
import io.collectra.api.campaign.infrastructure.CampaignRecipientRepository;
import io.collectra.api.campaign.infrastructure.CampaignRunTemplateBindingRepository;
import io.collectra.api.localization.application.TenantLocaleService;
import io.collectra.api.template.application.ResolvedTemplateLocale;
import io.collectra.api.template.application.TemplateLocaleResolver;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.domain.TemplateVersionStatus;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CampaignRunTemplateBindingService {
    private final CampaignRecipientRepository recipients;
    private final CampaignRunTemplateBindingRepository bindings;
    private final TemplateVersionRepository versions;
    private final TemplateLocaleResolver localeResolver;
    private final TenantLocaleService tenantLocales;

    public CampaignRunTemplateBindingService(
            CampaignRecipientRepository recipients,
            CampaignRunTemplateBindingRepository bindings,
            TemplateVersionRepository versions,
            TemplateLocaleResolver localeResolver,
            TenantLocaleService tenantLocales) {
        this.recipients = recipients;
        this.bindings = bindings;
        this.versions = versions;
        this.localeResolver = localeResolver;
        this.tenantLocales = tenantLocales;
    }

    public void initialize(UUID tenantId, Campaign campaign, CampaignRun run) {
        if (!bindings.findAllByTenantIdAndCampaignRunId(tenantId, run.getId()).isEmpty()) {
            throw new IllegalStateException("Template bindings already exist for READY run " + run.getId());
        }
        if (!"EMAIL".equals(campaign.getChannel())) {
            throw new IllegalStateException("Slice 5 supports EMAIL campaigns only");
        }

        TemplateVersion anchor =
                versions.findByIdAndTenantId(campaign.getTemplateVersionId(), tenantId)
                        .orElseThrow(() -> new IllegalStateException("Campaign template version not found"));
        if (anchor.getChannel() != TemplateChannel.EMAIL) {
            throw new IllegalStateException("Campaign template version must use EMAIL channel");
        }
        if (anchor.getStatus() != TemplateVersionStatus.PUBLISHED) {
            throw new IllegalStateException("Campaign template version must be PUBLISHED");
        }

        String tenantDefault = tenantLocales.requireDefault(tenantId).getLocale();
        LinkedHashSet<String> requestedLocales =
                recipients.findDistinctLocalesByTenantIdAndRunId(tenantId, run.getId()).stream()
                        .map(locale -> normalize(locale, tenantDefault))
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        for (String requestedLocale : requestedLocales) {
            ResolvedTemplateLocale resolved =
                    localeResolver.resolve(
                            tenantId, anchor.getTemplateId(), TemplateChannel.EMAIL, requestedLocale);
            TemplateVersion selected =
                    versions.findFirstByTemplateIdAndLocaleAndChannelAndStatusOrderByTemplateVersionDesc(
                                    anchor.getTemplateId(),
                                    resolved.resolvedLocale(),
                                    TemplateChannel.EMAIL,
                                    TemplateVersionStatus.PUBLISHED)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Resolved published template version disappeared for locale "
                                            + resolved.resolvedLocale()));
            bindings.save(
                    new CampaignRunTemplateBinding(
                            tenantId,
                            run.getId(),
                            requestedLocale,
                            resolved.resolvedLocale(),
                            selected.getId(),
                            resolved.source()));
        }
    }

    public Map<String, CampaignRunTemplateBinding> bindingsByRequestedLocale(
            UUID tenantId, UUID runId) {
        return bindings.findAllByTenantIdAndCampaignRunId(tenantId, runId).stream()
                .collect(Collectors.toUnmodifiableMap(
                        CampaignRunTemplateBinding::getRequestedLocale, Function.identity()));
    }

    public String normalizeRequestedLocale(UUID tenantId, String requestedLocale) {
        return normalize(requestedLocale, tenantLocales.requireDefault(tenantId).getLocale());
    }

    private static String normalize(String requestedLocale, String tenantDefault) {
        return requestedLocale == null || requestedLocale.isBlank()
                ? tenantDefault
                : requestedLocale.trim();
    }
}
