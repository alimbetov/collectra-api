package io.collectra.api.identity.infrastructure;
import io.collectra.api.identity.domain.OtpChallenge;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {}
