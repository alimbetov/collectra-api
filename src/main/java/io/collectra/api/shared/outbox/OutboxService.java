package io.collectra.api.shared.outbox;
import java.util.UUID; import org.springframework.stereotype.Service;
@Service public class OutboxService {private final OutboxRepository repository;public OutboxService(OutboxRepository repository){this.repository=repository;}public void append(UUID tenantId,String aggregateType,UUID aggregateId,String eventType,String payload){repository.save(new OutboxEvent(tenantId,aggregateType,aggregateId,eventType,payload));}}
