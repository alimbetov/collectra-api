package io.collectra.api.shared.outbox;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {}
