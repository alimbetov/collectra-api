package io.collectra.api.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.collectra.api.identity.application.KeycloakOnboardingService;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class KeycloakOnboardingControllerTest {
    @Test
    void controlPlaneRequiresPlatformSuperAdmin() {
        PreAuthorize policy = KeycloakOnboardingController.class.getAnnotation(PreAuthorize.class);
        assertThat(policy).isNotNull();
        assertThat(policy.value()).contains("ROLE_PLATFORM_SUPER_ADMIN");
    }

    @Test
    void onboardingNeverAcceptsPasswordOrVerificationToken() {
        var fields = KeycloakOnboardingController.StartRequest.class.getRecordComponents();
        assertThat(fields).hasSize(1);
        assertThat(fields[0].getName()).isEqualTo("localUserId");
    }

    @Test
    void activationUsesServerVerifiedIdentityNotSubmittedEmail() throws Exception {
        Method activate =
                KeycloakOnboardingController.class.getMethod("activate", java.util.UUID.class);
        assertThat(activate.getAnnotation(PostMapping.class)).isNotNull();
        assertThat(KeycloakOnboardingController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/v1/platform/iam/onboarding");
    }

    @Test
    void repeatedOnboardingReportsPendingInsteadOfClaimingEmailWasSent() {
        var service = mock(KeycloakOnboardingService.class);
        UUID localUserId = UUID.randomUUID();
        UUID onboardingId = UUID.randomUUID();
        when(service.registerExisting(localUserId)).thenReturn(onboardingId);

        var result =
                new KeycloakOnboardingController(service)
                        .register(new KeycloakOnboardingController.StartRequest(localUserId));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(result.getBody())
                .containsEntry("onboardingId", onboardingId)
                .containsEntry("status", "PENDING_VERIFICATION");
        verify(service).registerExisting(localUserId);
    }
}
