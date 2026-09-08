package io.collectra.api.integration.api;

import io.collectra.api.integration.application.ServiceClientService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integration")
public class ServiceTokenController {
    private final ServiceClientService service;

    public ServiceTokenController(ServiceClientService service) {
        this.service = service;
    }

    @PostMapping("/service-token")
    ServiceClientService.TokenResponse token(@Valid @RequestBody TokenRequest request) {
        return service.token(request.clientId(), request.clientSecret(), request.scopes());
    }

    record TokenRequest(
            @NotBlank String clientId,
            @NotBlank String clientSecret,
            @NotEmpty Set<String> scopes) {}
}
