package io.collectra.platform.registry;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/applications")
public class RegistryController {
    private final PlatformRegistry registry;
    public RegistryController(PlatformRegistry registry) { this.registry = registry; }

    public record Registration(String code) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_platform.registry.write')")
    public Map<String,Object> register(@RequestBody Registration request) {
        return registry.register(request.code());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_platform.registry.read')")
    public Map<String,Object> get(@PathVariable UUID id) { return registry.find(id); }
}
