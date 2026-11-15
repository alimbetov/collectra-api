package io.collectra.platform.security;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Browser BFF CSRF bootstrap; no credential or OAuth token is exposed. */
@RestController
@RequestMapping("/api/v1/iam")
public class IamCsrfController {
    @GetMapping("/csrf")
    public ResponseEntity<Map<String,String>> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("headerName", token.getHeaderName(), "token", token.getToken()));
    }
}
