package io.collectra.api.identity.api;

import io.collectra.api.identity.application.KeycloakOnboardingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Privileged control-plane API; never accepts user passwords or email action links. */
@RestController
@RequestMapping("/api/v1/platform/iam/onboarding")
@PreAuthorize("hasAuthority('ROLE_PLATFORM_SUPER_ADMIN')")
public class KeycloakOnboardingController {
    private final KeycloakOnboardingService service;

    public KeycloakOnboardingController(KeycloakOnboardingService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody StartRequest request) {
        UUID id = service.registerExisting(request.localUserId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("onboardingId", id, "status", "PENDING_VERIFICATION"));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable UUID id) {
        service.activate(id);
        return ResponseEntity.noContent().build();
    }

    public record StartRequest(@NotNull UUID localUserId) {}
}
