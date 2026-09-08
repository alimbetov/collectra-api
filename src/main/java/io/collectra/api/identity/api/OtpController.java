package io.collectra.api.identity.api;

import io.collectra.api.identity.application.OtpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@RestController
@RequestMapping("/api/v1/auth/otp")
public class OtpController {
    private final OtpService otp;
    public OtpController(OtpService otp) { this.otp = otp; }
    @PostMapping("/challenges") @PreAuthorize("hasAuthority('ROLE_HUMAN')")
    OtpService.ChallengeResponse create(@Valid @RequestBody CreateRequest request,
            JwtAuthenticationToken authentication) {
        UUID tenantId = UUID.fromString(authentication.getToken().getClaimAsString("tenant_id"));
        UUID subjectId = UUID.fromString(authentication.getToken().getSubject());
        return otp.create(tenantId, subjectId, request.purpose());
    }
    @PostMapping("/challenges/{id}/verify") VerifyResponse verify(@PathVariable UUID id, @Valid @RequestBody VerifyRequest request) {
        return new VerifyResponse(otp.verify(id, request.code()));
    }
    record CreateRequest(@Pattern(regexp = "[A-Z_]{3,60}") String purpose) {}
    record VerifyRequest(@Pattern(regexp = "[0-9]{6}") String code) {}
    record VerifyResponse(boolean verified) {}
}
