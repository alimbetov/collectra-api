package io.collectra.api.collection.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.collectra.api.AbstractIntegrationTest;
import io.collectra.api.collection.domain.CollectionAction;
import io.collectra.api.collection.domain.CollectionPriority;
import io.collectra.api.collection.infrastructure.CollectionActionRepository;
import io.collectra.api.collection.infrastructure.CollectionCaseRepository;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.Customer;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.receivable.domain.Invoice;
import io.collectra.api.shared.error.InvalidRequestException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CollectionQueuePostgresIntegrationTest extends AbstractIntegrationTest {
    @Autowired CollectionQueryService queries;
    @Autowired CollectionCaseRepository cases;
    @Autowired CollectionActionRepository actions;
    @Autowired CustomerService customers;
    @Autowired ReceivableService receivables;

    @Test
    void filtersAndSortsByEarliestPendingActionWithAuthoritativePaging() {
        UUID tenant = UUID.randomUUID();
        UUID foreignTenant = UUID.randomUUID();
        Instant now = Instant.now();

        var overdue = fixture(tenant, "OVERDUE");
        var future = fixture(tenant, "FUTURE");
        var noAction = fixture(tenant, "NONE");
        var foreign = fixture(foreignTenant, "FOREIGN");

        actions.save(
                new CollectionAction(
                        tenant,
                        overdue.caseId(),
                        "LATER",
                        null,
                        now.minusSeconds(60),
                        CollectionPriority.NORMAL));
        actions.save(
                new CollectionAction(
                        tenant,
                        overdue.caseId(),
                        "EARLIEST",
                        null,
                        now.minusSeconds(120),
                        CollectionPriority.HIGH));
        actions.save(
                new CollectionAction(
                        tenant,
                        future.caseId(),
                        "FUTURE",
                        null,
                        now.plusSeconds(3600),
                        CollectionPriority.NORMAL));
        actions.save(
                new CollectionAction(
                        foreignTenant,
                        foreign.caseId(),
                        "FOREIGN",
                        null,
                        now.minusSeconds(120),
                        CollectionPriority.NORMAL));

        var overduePage =
                queries.list(
                        tenant,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        null,
                        null,
                        0,
                        1,
                        "nextActionDueAt,asc");
        assertThat(overduePage.totalElements()).isEqualTo(1);
        assertThat(overduePage.items()).hasSize(1);
        assertThat(overduePage.items().get(0).id()).isEqualTo(overdue.caseId());
        assertThat(overduePage.items().get(0).nextActionType()).isEqualTo("EARLIEST");
        assertThat(overduePage.items().get(0).nextActionOverdue()).isTrue();

        var dueRange =
                queries.list(
                        tenant,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        now,
                        now.plusSeconds(7200),
                        0,
                        10,
                        "nextActionDueAt,desc");
        assertThat(dueRange.items()).extracting(CollectionQueryService.CaseItem::id).containsExactly(future.caseId());

        var all =
                queries.list(
                        tenant,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        10,
                        "nextActionDueAt,asc");
        assertThat(all.totalElements()).isEqualTo(3);
        assertThat(all.items()).extracting(CollectionQueryService.CaseItem::id)
                .containsExactly(overdue.caseId(), future.caseId(), noAction.caseId());
    }

    @Test
    void validatesOperationalRangeAndPageBoundaries() {
        UUID tenant = UUID.randomUUID();
        Instant now = Instant.now();
        assertThatThrownBy(
                        () ->
                                queries.list(
                                        tenant,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        now.plusSeconds(1),
                                        now,
                                        0,
                                        10,
                                        "nextActionDueAt,asc"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(
                        () ->
                                queries.list(
                                        tenant,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        true,
                                        null,
                                        null,
                                        -1,
                                        10,
                                        "nextActionDueAt,asc"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(
                        () ->
                                queries.list(
                                        tenant,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        true,
                                        null,
                                        null,
                                        0,
                                        CollectionQueryService.MAX_SIZE + 1,
                                        "nextActionDueAt,asc"))
                .isInstanceOf(InvalidRequestException.class);
    }

    private Fixture fixture(UUID tenant, String suffix) {
        Customer customer =
                customers.create(
                        tenant,
                        "C-" + suffix + "-" + UUID.randomUUID(),
                        CustomerType.COMPANY,
                        "Customer " + suffix,
                        null,
                        null,
                        null,
                        "Customer " + suffix,
                        null,
                        "ru-KZ",
                        "Asia/Almaty",
                        null);
        Invoice invoice =
                receivables.createInvoice(
                        tenant,
                        customer.getId(),
                        null,
                        "INV-" + suffix + "-" + UUID.randomUUID(),
                        "N-" + suffix,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 10),
                        new BigDecimal("1000.00"),
                        "KZT",
                        null,
                        null);
        var collectionCase =
                cases.save(
                        new io.collectra.api.collection.domain.CollectionCase(
                                tenant,
                                customer.getId(),
                                invoice.getId(),
                                CollectionPriority.NORMAL,
                                null,
                                Instant.now()));
        return new Fixture(collectionCase.getId());
    }

    private record Fixture(UUID caseId) {}
}
