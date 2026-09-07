package io.collectra.api.identity.api;

import io.collectra.api.identity.application.AuthService;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.util.UUID;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth; public AuthController(AuthService auth){this.auth=auth;}
    @PostMapping("/tenants/register") ResponseEntity<AuthService.AuthTokens> register(@Valid @RequestBody RegisterRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(auth.register(r.slug(),r.companyName(),r.email(),r.password()));}
    @PostMapping("/login") AuthService.AuthTokens login(@Valid @RequestBody LoginRequest r){return auth.login(r.tenantId(),r.email(),r.password());}
    @PostMapping("/refresh") AuthService.AuthTokens refresh(@Valid @RequestBody TokenRequest r){return auth.refresh(r.refreshToken());}
    @PostMapping("/logout") ResponseEntity<Void> logout(@Valid @RequestBody TokenRequest r){auth.logout(r.refreshToken());return ResponseEntity.noContent().build();}
    public record RegisterRequest(@Pattern(regexp="[a-z0-9-]{3,80}") String slug,@NotBlank @Size(max=200) String companyName,@Email @NotBlank String email,@Size(min=12,max=72) String password){}
    public record LoginRequest(@NotNull UUID tenantId,@Email @NotBlank String email,@NotBlank String password){}
    public record TokenRequest(@NotBlank String refreshToken){}
}
