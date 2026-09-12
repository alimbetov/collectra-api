package io.collectra.api.receivable.application;

import io.collectra.api.receivable.domain.AllocationStatus;
import io.collectra.api.receivable.domain.Invoice;
import io.collectra.api.receivable.domain.Payment;
import io.collectra.api.receivable.domain.PaymentAllocation;
import io.collectra.api.receivable.domain.PaymentStatus;
import io.collectra.api.receivable.infrastructure.InvoiceRepository;
import io.collectra.api.receivable.infrastructure.PaymentRepository;
import io.collectra.api.shared.error.InvalidRequestException;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceivableQueryService {
    public static final int MAX_SIZE = 200;

    private static final Set<String> INVOICE_SORTS =
            Set.of(
                    "createdAt",
                    "updatedAt",
                    "invoiceNumber",
                    "invoiceDate",
                    "dueDate",
                    "originalAmount",
                    "outstandingAmount",
                    "externalId");
    private static final Set<String> PAYMENT_SORTS =
            Set.of(
                    "createdAt",
                    "updatedAt",
                    "paymentDate",
                    "amount",
                    "paymentReference",
                    "externalId");

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final Clock clock;
    private final ZoneId businessZone;

    public ReceivableQueryService(
            InvoiceRepository invoices,
            PaymentRepository payments,
            Clock clock,
            ZoneId businessZone) {
        this.invoices = invoices;
        this.payments = payments;
        this.clock = clock;
        this.businessZone = businessZone;
    }

    @Transactional(readOnly = true)
    public InvoicePage invoices(
            UUID tenantId,
            UUID customerId,
            UUID contractId,
            PaymentStatus paymentStatus,
            String currency,
            String invoiceNumber,
            String externalId,
            String search,
            LocalDate issuedFrom,
            LocalDate issuedTo,
            LocalDate dueFrom,
            LocalDate dueTo,
            Boolean overdue,
            BigDecimal amountMin,
            BigDecimal amountMax,
            BigDecimal outstandingMin,
            BigDecimal outstandingMax,
            int page,
            int size,
            String sort) {
        validatePage(page, size);
        validateRange(issuedFrom, issuedTo, "issuedFrom", "issuedTo");
        validateRange(dueFrom, dueTo, "dueFrom", "dueTo");
        validateRange(amountMin, amountMax, "amountMin", "amountMax");
        validateRange(outstandingMin, outstandingMax, "outstandingMin", "outstandingMax");
        String normalizedSearch = normalizeSearch(search);
        String normalizedCurrency = normalizeCurrency(currency);
        String normalizedInvoiceNumber = trimToNull(invoiceNumber);
        String normalizedExternalId = trimToNull(externalId);
        LocalDate businessDate = LocalDate.now(clock.withZone(businessZone));

        Page<Invoice> result =
                invoices.findAll(
                        invoiceSpecification(
                                tenantId,
                                customerId,
                                contractId,
                                paymentStatus,
                                normalizedCurrency,
                                normalizedInvoiceNumber,
                                normalizedExternalId,
                                normalizedSearch,
                                issuedFrom,
                                issuedTo,
                                dueFrom,
                                dueTo,
                                overdue,
                                businessDate,
                                amountMin,
                                amountMax,
                                outstandingMin,
                                outstandingMax),
                        PageRequest.of(
                                page,
                                size,
                                parseSort(sort, INVOICE_SORTS, "createdAt", Sort.Direction.DESC)));

        List<InvoiceItem> items =
                result.getContent().stream()
                        .map(value -> InvoiceItem.from(value, businessDate))
                        .toList();
        return new InvoicePage(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext(),
                businessDate);
    }

    @Transactional(readOnly = true)
    public PaymentPage payments(
            UUID tenantId,
            UUID customerId,
            UUID invoiceId,
            String currency,
            String paymentReference,
            String externalId,
            LocalDate paymentFrom,
            LocalDate paymentTo,
            BigDecimal amountMin,
            BigDecimal amountMax,
            Boolean unallocatedOnly,
            String search,
            int page,
            int size,
            String sort) {
        validatePage(page, size);
        validateRange(paymentFrom, paymentTo, "paymentFrom", "paymentTo");
        validateRange(amountMin, amountMax, "amountMin", "amountMax");

        Page<Payment> result =
                payments.findAll(
                        paymentSpecification(
                                tenantId,
                                customerId,
                                invoiceId,
                                normalizeCurrency(currency),
                                trimToNull(paymentReference),
                                trimToNull(externalId),
                                paymentFrom,
                                paymentTo,
                                amountMin,
                                amountMax,
                                unallocatedOnly,
                                normalizeSearch(search)),
                        PageRequest.of(
                                page,
                                size,
                                parseSort(sort, PAYMENT_SORTS, "createdAt", Sort.Direction.DESC)));

        return new PaymentPage(
                result.getContent().stream().map(PaymentItem::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext());
    }

    private static Specification<Invoice> invoiceSpecification(
            UUID tenantId,
            UUID customerId,
            UUID contractId,
            PaymentStatus paymentStatus,
            String currency,
            String invoiceNumber,
            String externalId,
            String search,
            LocalDate issuedFrom,
            LocalDate issuedTo,
            LocalDate dueFrom,
            LocalDate dueTo,
            Boolean overdue,
            LocalDate businessDate,
            BigDecimal amountMin,
            BigDecimal amountMax,
            BigDecimal outstandingMin,
            BigDecimal outstandingMax) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (contractId != null) {
                predicates.add(cb.equal(root.get("contractId"), contractId));
            }
            if (paymentStatus != null) {
                predicates.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            }
            if (currency != null) {
                predicates.add(cb.equal(root.get("currency"), currency));
            }
            if (invoiceNumber != null) {
                predicates.add(
                        cb.equal(
                                cb.lower(root.<String>get("invoiceNumber")),
                                invoiceNumber.toLowerCase(Locale.ROOT)));
            }
            if (externalId != null) {
                predicates.add(cb.equal(root.get("externalId"), externalId));
            }
            if (search != null) {
                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.<String>get("invoiceNumber")), search + "%"),
                                cb.like(cb.lower(root.<String>get("externalId")), search + "%")));
            }
            if (issuedFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("invoiceDate"), issuedFrom));
            }
            if (issuedTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("invoiceDate"), issuedTo));
            }
            if (dueFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), dueFrom));
            }
            if (dueTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), dueTo));
            }
            if (Boolean.TRUE.equals(overdue)) {
                predicates.add(
                        cb.not(
                                root.get("paymentStatus")
                                        .in(PaymentStatus.PAID, PaymentStatus.CANCELLED)));
                predicates.add(cb.greaterThan(root.get("outstandingAmount"), BigDecimal.ZERO));
                predicates.add(cb.lessThan(root.get("dueDate"), businessDate));
            } else if (Boolean.FALSE.equals(overdue)) {
                predicates.add(
                        cb.or(
                                root.get("paymentStatus")
                                        .in(PaymentStatus.PAID, PaymentStatus.CANCELLED),
                                cb.lessThanOrEqualTo(
                                        root.get("outstandingAmount"), BigDecimal.ZERO),
                                cb.greaterThanOrEqualTo(root.get("dueDate"), businessDate)));
            }
            if (amountMin != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("originalAmount"), amountMin));
            }
            if (amountMax != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("originalAmount"), amountMax));
            }
            if (outstandingMin != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(root.get("outstandingAmount"), outstandingMin));
            }
            if (outstandingMax != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("outstandingAmount"), outstandingMax));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Specification<Payment> paymentSpecification(
            UUID tenantId,
            UUID customerId,
            UUID invoiceId,
            String currency,
            String paymentReference,
            String externalId,
            LocalDate paymentFrom,
            LocalDate paymentTo,
            BigDecimal amountMin,
            BigDecimal amountMax,
            Boolean unallocatedOnly,
            String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (currency != null) {
                predicates.add(cb.equal(root.get("currency"), currency));
            }
            if (paymentReference != null) {
                predicates.add(
                        cb.like(
                                cb.lower(root.<String>get("paymentReference")),
                                "%" + paymentReference.toLowerCase(Locale.ROOT) + "%"));
            }
            if (externalId != null) {
                predicates.add(cb.equal(root.get("externalId"), externalId));
            }
            if (paymentFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("paymentDate"), paymentFrom));
            }
            if (paymentTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("paymentDate"), paymentTo));
            }
            if (amountMin != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), amountMin));
            }
            if (amountMax != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), amountMax));
            }
            if (search != null) {
                predicates.add(
                        cb.or(
                                cb.like(
                                        cb.lower(root.<String>get("paymentReference")),
                                        "%" + search + "%"),
                                cb.like(cb.lower(root.<String>get("externalId")), search + "%")));
            }
            if (invoiceId != null) {
                Subquery<UUID> allocationQuery = query.subquery(UUID.class);
                Root<PaymentAllocation> allocation = allocationQuery.from(PaymentAllocation.class);
                allocationQuery.select(allocation.get("id"));
                allocationQuery.where(
                        cb.equal(allocation.get("tenantId"), tenantId),
                        cb.equal(allocation.get("paymentId"), root.get("id")),
                        cb.equal(allocation.get("invoiceId"), invoiceId),
                        cb.equal(allocation.get("status"), AllocationStatus.ACTIVE));
                predicates.add(cb.exists(allocationQuery));
            }
            if (Boolean.TRUE.equals(unallocatedOnly)) {
                Subquery<BigDecimal> allocatedQuery = query.subquery(BigDecimal.class);
                Root<PaymentAllocation> allocation = allocatedQuery.from(PaymentAllocation.class);
                allocatedQuery.select(
                        cb.coalesce(cb.sum(allocation.<BigDecimal>get("amount")), BigDecimal.ZERO));
                allocatedQuery.where(
                        cb.equal(allocation.get("tenantId"), tenantId),
                        cb.equal(allocation.get("paymentId"), root.get("id")),
                        cb.equal(allocation.get("status"), AllocationStatus.ACTIVE));
                predicates.add(cb.greaterThan(root.<BigDecimal>get("amount"), allocatedQuery));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size <= 0 || size > MAX_SIZE) {
            throw new InvalidRequestException("INVALID_REQUEST", "Invalid pagination parameters");
        }
    }

    private static <T extends Comparable<? super T>> void validateRange(
            T from, T to, String fromName, String toName) {
        if (from != null && to != null && from.compareTo(to) > 0) {
            throw new InvalidRequestException(
                    "INVALID_RANGE", fromName + " must not be after " + toName);
        }
    }

    private static Sort parseSort(
            String value,
            Set<String> allowed,
            String defaultField,
            Sort.Direction defaultDirection) {
        String normalized = trimToNull(value);
        String field = defaultField;
        Sort.Direction direction = defaultDirection;
        if (normalized != null) {
            String[] parts = normalized.split(",", -1);
            if (parts.length != 2 || !allowed.contains(parts[0].trim())) {
                throw new InvalidRequestException("INVALID_REQUEST", "Unsupported sort field");
            }
            field = parts[0].trim();
            try {
                direction = Sort.Direction.valueOf(parts[1].trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new InvalidRequestException("INVALID_REQUEST", "Unsupported sort direction");
            }
        }
        Sort primary = Sort.by(direction, field);
        return "id".equals(field) ? primary : primary.and(Sort.by(direction, "id"));
    }

    private static String normalizeSearch(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > 200) {
            throw new InvalidRequestException("INVALID_REQUEST", "Search is too long");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeCurrency(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() != 3) {
            throw new InvalidRequestException("INVALID_REQUEST", "Currency must be ISO-4217 code");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record InvoiceItem(
            UUID id,
            UUID customerId,
            UUID contractId,
            String externalId,
            String invoiceNumber,
            LocalDate invoiceDate,
            LocalDate dueDate,
            BigDecimal originalAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount,
            String currency,
            PaymentStatus paymentStatus,
            boolean overdue,
            long daysOverdue) {
        static InvoiceItem from(Invoice value, LocalDate businessDate) {
            boolean overdue = value.isOverdue(businessDate);
            long days =
                    overdue
                            ? java.time.temporal.ChronoUnit.DAYS.between(
                                    value.getDueDate(), businessDate)
                            : 0;
            return new InvoiceItem(
                    value.getId(),
                    value.getCustomerId(),
                    value.getContractId(),
                    value.getExternalId(),
                    value.getInvoiceNumber(),
                    value.getInvoiceDate(),
                    value.getDueDate(),
                    value.getOriginalAmount(),
                    value.getPaidAmount(),
                    value.getOutstandingAmount(),
                    value.getCurrency(),
                    value.getPaymentStatus(),
                    overdue,
                    days);
        }
    }

    public record InvoicePage(
            List<InvoiceItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext,
            LocalDate businessDate) {}

    public record PaymentItem(
            UUID id,
            UUID customerId,
            String externalId,
            LocalDate paymentDate,
            BigDecimal amount,
            String currency,
            String paymentReference,
            String source) {
        static PaymentItem from(Payment value) {
            return new PaymentItem(
                    value.getId(),
                    value.getCustomerId(),
                    value.getExternalId(),
                    value.getPaymentDate(),
                    value.getAmount(),
                    value.getCurrency(),
                    value.getPaymentReference(),
                    value.getSource());
        }
    }

    public record PaymentPage(
            List<PaymentItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {}
}
