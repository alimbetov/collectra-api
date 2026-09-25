package io.collectra.api.integration.api;
import io.collectra.api.integration.application.IngestionApplicationService;import io.collectra.api.shared.tenant.TenantContext;import java.util.UUID;import org.springframework.http.*;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.security.oauth2.jwt.Jwt;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/integration/sources") public class IngestionController{
 private final IngestionApplicationService service;public IngestionController(IngestionApplicationService service){this.service=service;}
 @PostMapping(value="/{sourceCode}/ingestions") @ResponseStatus(HttpStatus.ACCEPTED)
 @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ROLE_SERVICE') and hasAuthority('SCOPE_integration:imports:create')")
 IngestionApplicationService.Reservation ingest(@PathVariable String sourceCode,@RequestHeader("Idempotency-Key") String key,@RequestHeader(value=HttpHeaders.CONTENT_TYPE,defaultValue=MediaType.APPLICATION_JSON_VALUE)String contentType,@RequestBody byte[] body,@AuthenticationPrincipal Jwt jwt){return service.reserve(TenantContext.requireTenantId(),UUID.fromString(jwt.getSubject()),sourceCode,key,contentType,body);}
 @GetMapping("/{sourceCode}/ingestions/{id}") @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ROLE_SERVICE') and hasAuthority('SCOPE_integration:imports:read')")
 IngestionApplicationService.Reservation status(@PathVariable String sourceCode,@PathVariable UUID id){return service.status(TenantContext.requireTenantId(),id);}
}
