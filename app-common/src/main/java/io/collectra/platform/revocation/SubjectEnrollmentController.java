package io.collectra.platform.revocation;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Requires an explicit privileged provisioning scope. No public enrollment endpoint. */
@RestController
@RequestMapping("/api/v1/platform/subjects")
public class SubjectEnrollmentController {
    private final SubjectEnrollmentService service;
    public SubjectEnrollmentController(SubjectEnrollmentService service){this.service=service;}
    @PostMapping("/{subject}/initialize")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('SCOPE_platform.subject.initialize')")
    public Map<String,Object> initialize(@PathVariable String subject,@RequestParam String issuer){
        return service.initialize(issuer,subject);
    }
}
