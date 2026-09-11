package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.collectra.api.campaign.application.CampaignSelection;
import io.collectra.api.campaign.application.CampaignService;
import io.collectra.api.campaign.domain.CampaignRecipient;
import io.collectra.api.campaign.domain.CampaignRun;
import io.collectra.api.communication.application.DeliveryCommand;
import io.collectra.api.communication.application.DeliveryGateway;
import io.collectra.api.communication.application.DeliveryResult;
import io.collectra.api.communication.application.MessageDeliveryWorker;
import io.collectra.api.communication.application.MessageRecoveryService;
import io.collectra.api.communication.application.MessageRetryDispatcher;
import io.collectra.api.communication.application.MessageStateService;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageRepository;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.Customer;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.receivable.domain.Invoice;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Import(MessageProcessingIntegrationTest.FixedClockConfiguration.class)
class MessageProcessingIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-11T10:00:00Z");

    @Autowired TenantRepository tenants;
    @Autowired DocumentTemplateRepository documentTemplates;
    @Autowired TemplateVersionRepository templateVersions;
    @Autowired CustomerService customers;
    @Autowired ReceivableService receivables;
    @Autowired CampaignService campaigns;
    @Autowired MessageRepository messages;
    @Autowired MessageStateService states;
    @Autowired MessageRecoveryService recovery;
    @Autowired MessageRetryDispatcher retryDispatcher;
    @Autowired MessageDeliveryWorker worker;
    @Autowired RecordingDeliveryGateway gateway;
    @Autowired ObjectMapper json;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;

    @Test
    void claimsOnceAndKeepsTenantAndTerminalStatesIsolated() {
        Fixture fixture = fixture();

        var claimed = states.begin(fixture.tenantId(), fixture.messageId()).orElseThrow();

        assertThat(claimed.attemptCount()).isOne();
        assertThat(claimed.body()).isEqualTo("<p>Please pay your invoice</p>");
        assertThat(states.begin(fixture.tenantId(), fixture.messageId())).isEmpty();
        assertThatThrownBy(() -> states.begin(UUID.randomUUID(), fixture.messageId()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Message not found");

        states.markSent(fixture.tenantId(), fixture.messageId(), "provider-1");
        Message sent = messages.findById(fixture.messageId()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(sent.getAttemptCount()).isOne();
        assertThat(sent.getProviderMessageId()).isEqualTo("provider-1");
        assertThat(sent.getSentAt()).isEqualTo(NOW);
        assertThat(states.begin(fixture.tenantId(), fixture.messageId())).isEmpty();
        assertThatThrownBy(
                        () ->
                                states.markSent(
                                        fixture.tenantId(), fixture.messageId(), "late-result"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected PROCESSING");
    }

    @Test
    void concurrentBeginHasExactlyOneWinner() throws Exception {
        Fixture fixture = fixture();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first =
                    pool.submit(
                            () -> {
                                ready.countDown();
                                start.await();
                                return states.begin(fixture.tenantId(), fixture.messageId());
                            });
            var second =
                    pool.submit(
                            () -> {
                                ready.countDown();
                                start.await();
                                return states.begin(fixture.tenantId(), fixture.messageId());
                            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(
                            java.util.stream.Stream.of(
                                            first.get(10, TimeUnit.SECONDS),
                                            second.get(10, TimeUnit.SECONDS))
                                    .filter(java.util.Optional::isPresent)
                                    .count())
                    .isOne();
        } finally {
            pool.shutdownNow();
        }

        Message claimed = messages.findById(fixture.messageId()).orElseThrow();
        assertThat(claimed.getStatus()).isEqualTo(MessageStatus.PROCESSING);
        assertThat(claimed.getAttemptCount()).isOne();
    }

    @Test
    void recoversOnlyStaleProcessingAndFailsExhaustedAttempt() {
        Fixture retryable = fixture();
        Fixture exhausted = fixture();
        Fixture fresh = fixture();
        states.begin(retryable.tenantId(), retryable.messageId());
        states.begin(exhausted.tenantId(), exhausted.messageId());
        states.begin(fresh.tenantId(), fresh.messageId());
        jdbc.update(
                "UPDATE messages SET processing_started_at = ? WHERE id = ?",
                Timestamp.from(NOW.minusSeconds(360)),
                retryable.messageId());
        jdbc.update(
                "UPDATE messages SET processing_started_at = ?, attempt_count = 4 WHERE id = ?",
                Timestamp.from(NOW.minusSeconds(360)),
                exhausted.messageId());

        assertThat(recovery.recoverStale()).isEqualTo(2);

        Message retry = messages.findById(retryable.messageId()).orElseThrow();
        assertThat(retry.getStatus()).isEqualTo(MessageStatus.RETRY_WAIT);
        assertThat(retry.getNextRetryAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(retry.getLastErrorCode()).isEqualTo("PROCESSING_TIMEOUT");
        Message failed = messages.findById(exhausted.messageId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(failed.getLastErrorCode()).isEqualTo("PROCESSING_TIMEOUT");
        assertThat(messages.findById(fresh.messageId()).orElseThrow().getStatus())
                .isEqualTo(MessageStatus.PROCESSING);
    }

    @Test
    void dispatcherRequeuesOnlyDueRetryWithoutPublishingAnything() {
        Fixture due = fixture();
        Fixture future = fixture();
        states.begin(due.tenantId(), due.messageId());
        states.begin(future.tenantId(), future.messageId());
        states.scheduleRetry(
                due.tenantId(), due.messageId(), NOW.plusSeconds(60), "TEMP", "temporary");
        states.scheduleRetry(
                future.tenantId(), future.messageId(), NOW.plusSeconds(600), "TEMP", "temporary");
        jdbc.update(
                "UPDATE messages SET next_retry_at = ? WHERE id = ?",
                Timestamp.from(NOW.minusSeconds(1)),
                due.messageId());

        assertThat(retryDispatcher.dispatchDue()).isOne();

        Message requeued = messages.findById(due.messageId()).orElseThrow();
        assertThat(requeued.getStatus()).isEqualTo(MessageStatus.QUEUED);
        assertThat(requeued.getNextRetryAt()).isNull();
        assertThat(messages.findById(future.messageId()).orElseThrow().getStatus())
                .isEqualTo(MessageStatus.RETRY_WAIT);
    }

    @Test
    void providerCallRunsAfterClaimTransactionCommits() {
        Fixture fixture = fixture();
        gateway.reset();

        worker.deliver(fixture.tenantId(), fixture.messageId());

        assertThat(gateway.transactionActive()).isFalse();
        assertThat(messages.findById(fixture.messageId()).orElseThrow().getStatus())
                .isEqualTo(MessageStatus.SENT);
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        Tenant tenant = tenants.saveAndFlush(new Tenant("processing-" + suffix, "Processing test"));
        DocumentTemplate documentTemplate =
                documentTemplates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(),
                                "REMINDER_" + suffix,
                                "Payment reminder",
                                "INVOICE"));
        TemplateVersion templateVersion =
                new TemplateVersion(
                        documentTemplate.getId(),
                        1,
                        "ru-KZ",
                        TemplateChannel.EMAIL,
                        "Payment reminder",
                        "<p>Please pay your invoice</p>",
                        null);
        templateVersion.validated();
        templateVersion.publish();
        templateVersion = templateVersions.saveAndFlush(templateVersion);

        Customer customer =
                customers.create(
                        tenant.getId(),
                        "customer-" + suffix,
                        CustomerType.INDIVIDUAL,
                        "Test Customer",
                        "Test",
                        "Customer",
                        null,
                        null,
                        null,
                        "ru-KZ",
                        "Asia/Almaty",
                        json.createObjectNode());
        customers.addEmail(tenant.getId(), customer.getId(), "client@example.com", "WORK", true);
        LocalDate today = LocalDate.now(clock);
        Invoice invoice =
                receivables.createInvoice(
                        tenant.getId(),
                        customer.getId(),
                        null,
                        "invoice-" + suffix,
                        "INV-" + suffix,
                        today.minusDays(20),
                        today.minusDays(10),
                        new BigDecimal("10000.00"),
                        "KZT",
                        null,
                        json.createObjectNode());
        var campaign =
                campaigns.create(
                        tenant.getId(),
                        "Overdue reminder",
                        templateVersion.getId(),
                        "EMAIL",
                        null,
                        new CampaignSelection(Set.of(), Set.of(), 1, 30, null, null),
                        null);
        campaigns.activate(tenant.getId(), campaign.getId());
        var prepared = campaigns.prepare(tenant.getId(), campaign.getId());
        CampaignRun run = campaigns.run(tenant.getId(), prepared.runId());
        CampaignRecipient recipient = campaigns.recipients(tenant.getId(), prepared.runId()).get(0);
        Message message =
                messages.saveAndFlush(
                        Message.queued(
                                tenant.getId(),
                                campaign.getId(),
                                run.getId(),
                                recipient.getId(),
                                customer.getId(),
                                invoice.getId(),
                                templateVersion.getId(),
                                CommunicationChannel.EMAIL,
                                recipient.getDestination(),
                                templateVersion.getLocale(),
                                templateVersion.getSubject(),
                                templateVersion.getContentHtml()));
        return new Fixture(tenant.getId(), message.getId());
    }

    private record Fixture(UUID tenantId, UUID messageId) {}

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        RecordingDeliveryGateway recordingDeliveryGateway() {
            return new RecordingDeliveryGateway();
        }
    }

    static class RecordingDeliveryGateway implements DeliveryGateway {
        private volatile boolean transactionActive;

        @Override
        public DeliveryResult deliver(DeliveryCommand command) {
            transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
            return new DeliveryResult.Accepted("test-provider-id");
        }

        boolean transactionActive() {
            return transactionActive;
        }

        void reset() {
            transactionActive = true;
        }
    }
}
