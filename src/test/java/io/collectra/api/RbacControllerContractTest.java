package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.collectra.api.identity.api.IdentityController;
import io.collectra.api.integration.api.ServiceClientController;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

class RbacControllerContractTest {
    @Test
    void everyIdentityOperationDeclaresPermission() {
        assertThat(Arrays.stream(IdentityController.class.getDeclaredMethods())
                        .filter(method -> !method.isSynthetic())
                        .filter(method -> method.getName().matches("users|roles|createRole|assignRoles|changeStatus")))
                .allMatch(method -> method.isAnnotationPresent(PreAuthorize.class));
    }

    @Test
    void everyServiceClientManagementOperationDeclaresPermission() {
        assertThat(Arrays.stream(ServiceClientController.class.getDeclaredMethods())
                        .filter(method -> method.isAnnotationPresent(PostMapping.class))
                        .filter(method -> !method.getName().equals("token")))
                .allMatch(method -> method.isAnnotationPresent(PreAuthorize.class));
    }
}
