package io.collectra.api.communication.infrastructure;

import io.collectra.api.communication.domain.DeliveryAttemptStatus;
import io.collectra.api.communication.domain.MessageDeliveryAttempt;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageDeliveryAttemptRepository
        extends JpaRepository<MessageDeliveryAttempt, UUID> {

    Optional<MessageDeliveryAttempt> findByMessageIdAndAttemptNo(UUID messageId, int attemptNo);

    Optional<MessageDeliveryAttempt> findFirstByMessageIdOrderByAttemptNoDesc(UUID messageId);

    List<MessageDeliveryAttempt> findAllByMessageIdOrderByAttemptNoAsc(UUID messageId);

    boolean existsByMessageIdAndStatus(UUID messageId, DeliveryAttemptStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select a from MessageDeliveryAttempt a
            where a.messageId = :messageId
              and a.attemptNo = :attemptNo
            """)
    Optional<MessageDeliveryAttempt> findLockedByMessageIdAndAttemptNo(
            @Param("messageId") UUID messageId, @Param("attemptNo") int attemptNo);
}
