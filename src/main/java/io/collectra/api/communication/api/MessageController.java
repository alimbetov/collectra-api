package io.collectra.api.communication.api;

import io.collectra.api.communication.application.MessageQueryService;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/campaigns/{campaignId}/runs/{runId}/messages")
@PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_READ')")
public class MessageController {
    private final MessageQueryService messages;

    public MessageController(MessageQueryService messages) {
        this.messages = messages;
    }

    @GetMapping
    MessageQueryService.MessageSlice list(
            @PathVariable UUID campaignId,
            @PathVariable UUID runId,
            @RequestParam(required = false) MessageStatus status,
            @RequestParam(required = false) CommunicationChannel channel,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MessageQueryService.MAX_SIZE)
                    int size) {
        return messages.list(
                TenantContext.requireTenantId(),
                campaignId,
                runId,
                status,
                channel,
                customerId,
                page,
                size);
    }

    @GetMapping("/{messageId}")
    MessageQueryService.MessageDetail detail(
            @PathVariable UUID campaignId, @PathVariable UUID runId, @PathVariable UUID messageId) {
        return messages.detail(TenantContext.requireTenantId(), campaignId, runId, messageId);
    }
}
