package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.collectra.api.audit.api.SecurityAuditController;
import io.collectra.api.identity.api.CurrentUserController;
import io.collectra.api.identity.api.PlatformAccessController;
import io.collectra.api.identity.api.TenantInvitationController;
import io.collectra.api.identity.api.TenantMembershipController;
import io.collectra.api.identity.api.TenantRoleController;
import io.collectra.api.integration.api.ServiceClientController;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;

class RbacControllerContractTest {
    private static final String HUMAN = "hasAuthority('ROLE_HUMAN')";
    private static final String PLATFORM_ADMIN = "hasAuthority('ROLE_PLATFORM_SUPER_ADMIN')";

    @Test
    void humanControllersDeclareActorBoundary() {
        assertClassSecurity(CurrentUserController.class, HUMAN);
        assertClassSecurity(TenantMembershipController.class, HUMAN);
        assertClassSecurity(TenantRoleController.class, HUMAN);
        assertClassSecurity(ServiceClientController.class, HUMAN);
        assertClassSecurity(SecurityAuditController.class, HUMAN);
    }

    @Test
    void invitationsRequireHumanActorAndPermission() {
        assertClassSecurity(
                TenantInvitationController.class,
                "hasAuthority('ROLE_HUMAN') and hasAuthority('USER_INVITE')");
    }

    @Test
    void platformControllerDeclaresPlatformBoundary() {
        assertClassSecurity(PlatformAccessController.class, PLATFORM_ADMIN);
    }

    @Test
    void everyMembershipManagementOperationDeclaresPermission() {
        assertMethodsDeclarePermission(TenantMembershipController.class);
    }

    @Test
    void everyRoleManagementOperationDeclaresPermission() {
        assertMethodsDeclarePermission(TenantRoleController.class);
    }

    @Test
    void securityAuditOperationDeclaresPermission() {
        assertThat(Arrays.stream(SecurityAuditController.class.getDeclaredMethods())
                        .filter(method -> method.isAnnotationPresent(GetMapping.class)))
                .allMatch(method -> method.isAnnotationPresent(PreAuthorize.class));
    }

    @Test
    void everyServiceClientManagementOperationDeclaresPermission() {
        assertMethodsDeclarePermission(ServiceClientController.class);
    }

    @Test
    void everyMappedManagementOperationIsCoveredByTheContract() {
        assertThat(Arrays.stream(TenantInvitationController.class.getDeclaredMethods())
                        .filter(method -> !method.isSynthetic())
                        .filter(this::isManagementEndpoint))
                .isNotEmpty()
                .allMatch(
                        method ->
                                method.isAnnotationPresent(PreAuthorize.class)
                                        || TenantInvitationController.class.isAnnotationPresent(
                                                PreAuthorize.class));
    }

    private void assertMethodsDeclarePermission(Class<?> controller) {
        assertThat(Arrays.stream(controller.getDeclaredMethods())
                        .filter(method -> !method.isSynthetic())
                        .filter(this::isManagementEndpoint))
                .allMatch(method -> method.isAnnotationPresent(PreAuthorize.class))
                .allMatch(method ->
                        method.getAnnotation(PreAuthorize.class).value().contains("ROLE_HUMAN"));
    }

    private boolean isManagementEndpoint(Method method) {
        return method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class);
    }

    private void assertClassSecurity(Class<?> controller, String expression) {
        assertThat(controller.getAnnotation(PreAuthorize.class))
                .isNotNull()
                .extracting(PreAuthorize::value)
                .isEqualTo(expression);
    }
}
