package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.reporting.application.FinancialProjectionRebuildService;
import io.collectra.api.reporting.application.FinancialProjectionStateService;
import io.collectra.api.reporting.application.TenantFinancialAnalyticsQueryService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class TenantFinancialAnalyticsIntegrationTest extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired FinancialProjectionRebuildService rebuilds;
    @Autowired FinancialProjectionStateService projectionState;
    @Autowired TenantFinancialAnalyticsQueryService analytics;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void closedDayProjectionMatchesRawAndRemainsTenantAndCurrencyIsolated() {
        LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        Instant at = day.atTime(12, 0).toInstant(ZoneOffset.UTC);
        UUID alpha = UUID.randomUUID();
        UUID beta = UUID.randomUUID();
        seedTenant(alpha, "alpha-" + UUID.randomUUID(), at);
        seedTenant(beta, "beta-" + UUID.randomUUID(), at);
        seedInvoiceAndPayment(alpha, "KZT", "1000.0000", "250.0000", at);
        seedInvoiceAndPayment(alpha, "USD", "50.0000", "10.0000", at);
        seedInvoiceAndPayment(beta, "KZT", "9999.0000", "999.0000", at);

        var raw = analytics.summary(alpha, day, day);
        assertThat(raw.currencies()).hasSize(2);
        assertThat(raw.currencies())
                .extracting(TenantFinancialAnalyticsQueryService.Metric::currency)
                .containsExactly("KZT", "USD");

        rebuilds.rebuildTenantDay(alpha, day);
        var projected = analytics.summary(alpha, day, day);

        assertThat(projected.currencies()).isEqualTo(raw.currencies());
        assertThat(projected.currencies())
                .noneMatch(m -> m.invoiced().toPlainString().equals("9999.0000"));

        rebuilds.rebuildTenantDay(alpha, day);
        Long revision =
                jdbc.queryForObject(
                        "select revision from tenant_financial_projection_state where tenant_id=? and business_date=?",
                        Long.class,
                        alpha,
                        day);
        assertThat(revision).isEqualTo(2L);

        Integer betaRows =
                jdbc.queryForObject(
                        "select count(*) from tenant_daily_financial_metrics where tenant_id=?",
                        Integer.class,
                        beta);
        assertThat(betaRows).isZero();
    }

    @Test
    void staleBuildCannotOverwriteNewProjectionOwner() {
        LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(2);
        Instant now = Instant.now();
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId, "fence-" + UUID.randomUUID(), now);

        UUID first = projectionState.tryMarkBuilding(tenantId, day, now, now.minusSeconds(1800));
        assertThat(first).isNotNull();

        UUID liveAttempt =
                projectionState.tryMarkBuilding(
                        tenantId, day, now.plusSeconds(1), now.minusSeconds(1800));
        assertThat(liveAttempt).isNull();

        jdbc.update(
                "update tenant_financial_projection_state set calculated_at=? where tenant_id=? and business_date=?",
                Timestamp.from(now.minusSeconds(3600)),
                tenantId,
                day);

        UUID second =
                projectionState.tryMarkBuilding(
                        tenantId, day, now.plusSeconds(2), now.minusSeconds(1800));
        assertThat(second).isNotNull().isNotEqualTo(first);

        assertThat(
                        projectionState.markFailed(
                                tenantId, day, first, now.plusSeconds(3), "stale worker"))
                .isFalse();

        String status =
                jdbc.queryForObject(
                        "select status from tenant_financial_projection_state where tenant_id=? and business_date=?",
                        String.class,
                        tenantId,
                        day);
        UUID owner =
                jdbc.queryForObject(
                        "select build_id from tenant_financial_projection_state where tenant_id=? and business_date=?",
                        UUID.class,
                        tenantId,
                        day);
        assertThat(status).isEqualTo("BUILDING");
        assertThat(owner).isEqualTo(second);

        assertThat(
                        projectionState.markFailed(
                                tenantId, day, second, now.plusSeconds(4), "owner failed"))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select status from tenant_financial_projection_state where tenant_id=? and business_date=?",
                                String.class,
                                tenantId,
                                day))
                .isEqualTo("FAILED");
    }

    @Test
    void tenantApiExposesSummaryAndDayWeekMonthWithoutTenantOverride() throws Exception {
        String slug = "vc9-" + UUID.randomUUID();
        String token = register(slug);
        UUID tenantId =
                jdbc.queryForObject("select id from tenants where slug=?", UUID.class, slug);
        LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        seedInvoiceAndPayment(
                tenantId,
                "KZT",
                "500.0000",
                "100.0000",
                day.atTime(12, 0).toInstant(ZoneOffset.UTC));

        for (String bucket : new String[] {"DAY", "WEEK", "MONTH"}) {
            mockMvc.perform(
                            get("/api/v1/analytics/tenant/timeseries")
                                    .header("Authorization", "Bearer " + token)
                                    .param("from", day.toString())
                                    .param("to", day.toString())
                                    .param("bucket", bucket))
                    .andExpect(status().isOk());
        }

        String body =
                mockMvc.perform(
                                get("/api/v1/analytics/tenant/summary")
                                        .header("Authorization", "Bearer " + token)
                                        .param("from", day.toString())
                                        .param("to", day.toString())
                                        .param("tenantId", UUID.randomUUID().toString()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode response = json.readTree(body);
        assertThat(response.at("/currencies/0/currency").asText()).isEqualTo("KZT");
        assertThat(response.at("/currencies/0/invoiced").decimalValue())
                .isEqualByComparingTo("500.0000");
    }

    private String register(String slug) throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/v1/auth/tenants/register")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                {"slug":"%s","companyName":"VC9","email":"%s@example.test","password":"StrongPassword123!"}
                                """
                                                        .formatted(slug, UUID.randomUUID())))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private void seedTenant(UUID id, String slug, Instant at) {
        jdbc.update(
                """
                insert into tenants(id,slug,name,status,created_at,updated_at,version)
                values (?,?,'VC9','ACTIVE',?,?,0)
                """,
                id,
                slug,
                Timestamp.from(at),
                Timestamp.from(at));
    }

    private void seedInvoiceAndPayment(
            UUID tenantId,
            String currency,
            String invoiceAmount,
            String paymentAmount,
            Instant at) {
        UUID customer = UUID.randomUUID();
        UUID invoice = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString();
        jdbc.update(
                """
                insert into customers(id,tenant_id,external_id,customer_type,display_name,status,created_at,updated_at,version)
                values (?,?,?,'COMPANY','VC9 Customer','ACTIVE',?,?,0)
                """,
                customer,
                tenantId,
                "C-" + suffix,
                Timestamp.from(at),
                Timestamp.from(at));
        jdbc.update(
                """
                insert into invoices(id,tenant_id,customer_id,external_id,invoice_number,invoice_date,due_date,
                    original_amount,paid_amount,outstanding_amount,currency,payment_status,created_at,updated_at,version)
                values (?,?,?,?,?,?,?,?,0,?,?,'OPEN',?,?,0)
                """,
                invoice,
                tenantId,
                customer,
                "I-" + suffix,
                "N-" + suffix,
                LocalDate.ofInstant(at, ZoneOffset.UTC),
                LocalDate.ofInstant(at, ZoneOffset.UTC).minusDays(1),
                new java.math.BigDecimal(invoiceAmount),
                new java.math.BigDecimal(invoiceAmount),
                currency,
                Timestamp.from(at),
                Timestamp.from(at));
        jdbc.update(
                """
                insert into payments(id,tenant_id,customer_id,external_id,payment_date,amount,currency,created_at,updated_at,version)
                values (?,?,?,?,?,?,?,?,?,0)
                """,
                payment,
                tenantId,
                customer,
                "P-" + suffix,
                LocalDate.ofInstant(at, ZoneOffset.UTC),
                new java.math.BigDecimal(paymentAmount),
                currency,
                Timestamp.from(at),
                Timestamp.from(at));
        jdbc.update(
                """
                insert into payment_allocations(id,tenant_id,payment_id,invoice_id,amount,created_at,updated_at,version,command_id,status)
                values (?,?,?,?,?,?,?,0,?,'ACTIVE')
                """,
                UUID.randomUUID(),
                tenantId,
                payment,
                invoice,
                new java.math.BigDecimal(paymentAmount),
                Timestamp.from(at),
                Timestamp.from(at),
                UUID.randomUUID());
    }
}
