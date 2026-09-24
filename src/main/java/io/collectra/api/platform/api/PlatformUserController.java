package io.collectra.api.platform.api;

import io.collectra.api.platform.application.PlatformUserLifecycleService;
import io.collectra.api.platform.application.PlatformUserQueryService;
import io.collectra.api.platform.application.PlatformUserQueryService.PageResponse;
import io.collectra.api.platform.application.PlatformUserQueryService.SessionItem;
import io.collectra.api.platform.application.PlatformUserQueryService.UserDetail;
import io.collectra.api.platform.application.PlatformUserQueryService.UserItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasAuthority('ROLE_PLATFORM_SUPER_ADMIN')")
public class PlatformUserController {
    private final PlatformUserQueryService queries;
    private final PlatformUserLifecycleService lifecycle;

    public PlatformUserController(
            PlatformUserQueryService queries, PlatformUserLifecycleService lifecycle) {
        this.queries = queries;
        this.lifecycle = lifecycle;
    }

    @GetMapping("/users")
    PageResponse<UserItem> users(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String tenantSlug,
            @RequestParam(required = false) String membershipStatus,
            @RequestParam(required = false) String accountStatus,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return queries.users(
                search,
                tenantId,
                tenantSlug,
                membershipStatus,
                accountStatus,
                roleCode,
                createdFrom,
                createdTo,
                page,
                size,
                sort);
    }

    @GetMapping("/users/{userId}")
    UserDetail user(@PathVariable UUID userId) {
        return queries.user(userId);
    }

    @PatchMapping("/memberships/{membershipId}/status")
    UserDetail changeMembershipStatus(
            JwtAuthenticationToken authentication,
            @PathVariable UUID membershipId,
            @Valid @RequestBody MembershipStatusRequest request) {
        UUID userId =
                lifecycle
                        .changeMembershipStatus(
                                actorId(authentication),
                                membershipId,
                                request.active(),
                                request.revision(),
                                request.reason())
                        .getUserId();
        return queries.user(userId);
    }

    @GetMapping("/memberships/{membershipId}/sessions")
    PageResponse<SessionItem> sessions(
            @PathVariable UUID membershipId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return queries.sessions(membershipId, page, size);
    }

    @DeleteMapping("/memberships/{membershipId}/sessions")
    UserDetail revokeSessions(
            JwtAuthenticationToken authentication,
            @PathVariable UUID membershipId,
            @RequestParam @NotBlank @Size(max = 255) String reason) {
        UUID userId =
                lifecycleUserId(
                        authentication, membershipId, reason);
        return queries.user(userId);
    }

    private UUID lifecycleUserId(
            JwtAuthenticationToken authentication, UUID membershipId, String reason) {
        lifecycle.revokeAllSessions(actorId(authentication), membershipId, reason);
        return queries.sessions(membershipId, 0, 1).items().stream()
                .findFirst()
                .map(ignored -> queriesMembershipUserId(membershipId))
                .orElseGet(() -> queriesMembershipUserId(membershipId));
    }

    private UUID queriesMembershipUserId(UUID membershipId) {
        return queries.userIdByMembership(membershipId);
    }

    private UUID actorId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getToken().getSubject());
    }

    record MembershipStatusRequest(
            @NotNull Boolean active,
            @NotNull @Min(0) Long revision,
            @NotBlank @Size(max = 255) String reason) {}
}
