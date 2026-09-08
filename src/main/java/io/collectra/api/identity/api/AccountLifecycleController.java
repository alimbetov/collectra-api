package io.collectra.api.identity.api;

import io.collectra.api.identity.application.AuthService;
import io.collectra.api.identity.application.UserLifecycleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AccountLifecycleController {
    private final UserLifecycleService lifecycle;

    public AccountLifecycleController(UserLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @PostMapping("/invitations/accept")
    AuthService.AuthTokens accept(@Valid @RequestBody AcceptInvitationRequest request) {
        return lifecycle.accept(
                request.token(),
                request.password(),
                request.displayName(),
                request.locale(),
                request.timezone());
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    UserLifecycleService.ResetRequestResponse forgot(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return lifecycle.forgotPassword(request.tenantId(), request.email());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset(@Valid @RequestBody ResetPasswordRequest request) {
        lifecycle.resetPassword(request.token(), request.newPassword());
    }

    record AcceptInvitationRequest(
            @NotBlank String token,
            @Size(min = 12, max = 72) String password,
            @NotBlank @Size(max = 200) String displayName,
            @Size(max = 10) String locale,
            @Size(max = 60) String timezone) {}

    record ForgotPasswordRequest(@NotNull UUID tenantId, @Email @NotBlank String email) {}

    record ResetPasswordRequest(
            @NotBlank String token, @Size(min = 12, max = 72) String newPassword) {}
}
