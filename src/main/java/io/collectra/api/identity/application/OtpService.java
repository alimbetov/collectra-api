package io.collectra.api.identity.application;

import io.collectra.api.identity.domain.OtpChallenge;
import io.collectra.api.identity.infrastructure.OtpChallengeRepository;
import io.collectra.api.shared.security.InMemoryRateLimiter;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OtpService {
    private final OtpChallengeRepository challenges; private final InMemoryRateLimiter limiter;
    private final String pepper; private final boolean exposeCode; private final SecureRandom random = new SecureRandom();
    public OtpService(OtpChallengeRepository challenges, InMemoryRateLimiter limiter,
            @Value("${collectra.security.otp-pepper}") String pepper,
            @Value("${collectra.security.expose-otp:false}") boolean exposeCode) {
        this.challenges = challenges; this.limiter = limiter; this.pepper = pepper; this.exposeCode = exposeCode;
    }
    @Transactional
    public ChallengeResponse create(UUID tenantId, UUID subjectId, String purpose) {
        limiter.check("otp:" + subjectId + ":" + purpose, 3, Duration.ofMinutes(10));
        String code = "%06d".formatted(random.nextInt(1_000_000));
        OtpChallenge challenge = challenges.save(new OtpChallenge(tenantId, "USER", subjectId, purpose, hash(code)));
        return new ChallengeResponse(challenge.getId(), exposeCode ? code : null, 300);
    }
    @Transactional
    public boolean verify(UUID id, String code) {
        limiter.check("otp-verify:" + id, 10, Duration.ofMinutes(5));
        return challenges.findById(id).orElseThrow().verify(hash(code));
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((value + pepper).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    public record ChallengeResponse(UUID id, String developmentCode, long expiresIn) {}
}
