package io.collectra.api.contract.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.contract.domain.Contract;
import io.collectra.api.contract.infrastructure.ContractRepository;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.shared.error.BusinessConflictException;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractService {
    private final ContractRepository contracts;
    private final CustomerService customers;

    public ContractService(ContractRepository contracts, CustomerService customers) {
        this.contracts = contracts;
        this.customers = customers;
    }

    @Transactional
    public Contract create(
            UUID tenantId,
            UUID customerId,
            String externalId,
            String contractNumber,
            LocalDate validFrom,
            LocalDate validTo,
            LocalDate renewalDate,
            JsonNode customFields) {
        customers.get(tenantId, customerId);
        String normalizedExternalId = externalId.trim();
        contracts
                .findByTenantIdAndExternalId(tenantId, normalizedExternalId)
                .ifPresent(
                        value -> {
                            throw new BusinessConflictException(
                                    "DUPLICATE_EXTERNAL_ID", "Contract externalId already exists");
                        });
        try {
            return contracts.save(
                    new Contract(
                            tenantId,
                            customerId,
                            normalizedExternalId,
                            contractNumber,
                            validFrom,
                            validTo,
                            renewalDate,
                            customFields));
        } catch (IllegalArgumentException ex) {
            throw new BusinessConflictException("INVALID_RANGE", ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Contract get(UUID tenantId, UUID id) {
        return contracts
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Contract not found"));
    }

    @Transactional
    public Contract update(
            UUID tenantId,
            UUID id,
            long version,
            String contractNumber,
            LocalDate validFrom,
            LocalDate validTo,
            LocalDate renewalDate,
            JsonNode customFields) {
        Contract value = get(tenantId, id);
        requireVersion(value, version);
        try {
            value.update(contractNumber, validFrom, validTo, renewalDate, customFields);
            return value;
        } catch (IllegalArgumentException ex) {
            throw new BusinessConflictException("INVALID_RANGE", ex.getMessage());
        }
    }

    @Transactional
    public Contract suspend(UUID tenantId, UUID id, long version) {
        return transition(tenantId, id, version, Contract::suspend);
    }

    @Transactional
    public Contract activate(UUID tenantId, UUID id, long version) {
        return transition(tenantId, id, version, Contract::activate);
    }

    @Transactional
    public Contract close(UUID tenantId, UUID id, long version) {
        return transition(tenantId, id, version, Contract::close);
    }

    @Transactional
    public Contract cancel(UUID tenantId, UUID id, long version) {
        return transition(tenantId, id, version, Contract::cancel);
    }

    private Contract transition(
            UUID tenantId, UUID id, long version, java.util.function.Consumer<Contract> command) {
        Contract value = get(tenantId, id);
        requireVersion(value, version);
        try {
            command.accept(value);
            return value;
        } catch (IllegalStateException ex) {
            throw new BusinessConflictException("INVALID_STATE_TRANSITION", ex.getMessage());
        }
    }

    private static void requireVersion(Contract value, long expectedVersion) {
        if (value.getVersion() != expectedVersion) {
            throw new BusinessConflictException("VERSION_CONFLICT", "Contract version conflict");
        }
    }
}
