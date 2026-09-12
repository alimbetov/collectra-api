package io.collectra.api.support.api;

import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reference-data")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class ReferenceDataController {
    private static final List<String> CHANNELS =
            List.of("EMAIL", "SMS", "WHATSAPP", "TELEGRAM", "IN_APP");

    private final JdbcTemplate jdbc;

    public ReferenceDataController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ReferenceData all() {
        return new ReferenceData(CHANNELS, currencies(), locales(), statuses());
    }

    @GetMapping("/channels")
    public List<String> channels() {
        return CHANNELS;
    }

    @GetMapping("/currencies")
    public List<String> currencies() {
        return Currency.getAvailableCurrencies().stream()
                .map(Currency::getCurrencyCode)
                .sorted()
                .toList();
    }

    @GetMapping("/locales")
    public List<LocaleItem> locales() {
        return jdbc.query(
                """
                SELECT code, display_name, native_name, direction
                FROM supported_locales
                WHERE enabled = TRUE
                ORDER BY sort_order, code
                """,
                (rs, rowNum) ->
                        new LocaleItem(
                                rs.getString("code"),
                                rs.getString("display_name"),
                                rs.getString("native_name"),
                                rs.getString("direction")));
    }

    @GetMapping("/statuses")
    public Map<String, List<String>> statuses() {
        return Map.ofEntries(
                Map.entry("customer", List.of("ACTIVE", "INACTIVE")),
                Map.entry("contract", List.of("ACTIVE", "SUSPENDED", "CLOSED", "CANCELLED")),
                Map.entry("invoicePayment", List.of("UNPAID", "PARTIALLY_PAID", "PAID", "CANCELLED")),
                Map.entry("campaign", List.of("DRAFT", "ACTIVE", "ARCHIVED")),
                Map.entry("campaignRun", List.of("PREPARING", "READY", "RUNNING", "COMPLETED", "CANCELLED", "FAILED")),
                Map.entry("message", List.of("QUEUED", "PROCESSING", "RETRY_WAIT", "SENT", "FAILED")),
                Map.entry("collectionCase", List.of("OPEN", "IN_PROGRESS", "ON_HOLD", "CLOSED")),
                Map.entry("promiseToPay", List.of("ACTIVE", "FULFILLED", "BROKEN", "CANCELLED")),
                Map.entry("dispute", List.of("OPEN", "RESOLVED", "CANCELLED")),
                Map.entry("collectionAction", List.of("PENDING", "COMPLETED", "CANCELLED")));
    }

    public record LocaleItem(String code, String displayName, String nativeName, String direction) {}

    public record ReferenceData(
            List<String> channels,
            List<String> currencies,
            List<LocaleItem> locales,
            Map<String, List<String>> statuses) {}
}
