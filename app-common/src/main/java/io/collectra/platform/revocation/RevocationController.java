package io.collectra.platform.revocation;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/subjects")
public class RevocationController {
    private final SubjectAccessService access;
    public RevocationController(SubjectAccessService access) { this.access = access; }

    @PostMapping("/{subject}/block")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('SCOPE_platform.subject.block')")
    public Map<String,Object> block(@PathVariable String subject, @RequestParam String issuer) {
        return access.block(issuer,subject);
    }
}
