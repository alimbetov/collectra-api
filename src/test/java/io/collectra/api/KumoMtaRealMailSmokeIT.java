package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.campaign.application.CampaignSelection;
import io.collectra.api.campaign.application.CampaignService;
import io.collectra.api.campaign.domain.CampaignRecipient;
import io.collectra.api.campaign.domain.CampaignRun;
import io.collectra.api.campaign.infrastructure.CampaignRunRepository;
import io.collectra.api.communication.application.DeliveryGateway;
import io.collectra.api.communication.application.MessageDeliveryWorker;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageDeliveryAttemptRepository;
import io.collectra.api.communication.infrastructure.MessageRepository;
import io.collectra.api.communication.infrastructure.kumomta.KumoMtaEmailDeliveryGateway;
import io.collectra.api.communication.infrastructure.simulation.SimulatedDeliveryGateway;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        properties = {
            "collectra.communication.delivery.enabled=true",
            "collectra.communication.delivery.provider=kumomta",
            "collectra.communication.kumomta.base-url=http://localhost:18000",
            "collectra.communication.kumomta.envelope-sender=sender@smoke.test",
            "collectra.communication.kumomta.from-header=Collectra Smoke <sender@smoke.test>"
        })
class KumoMtaRealMailSmokeIT extends AbstractIntegrationTest {
    @Autowired ApplicationContext context;
    @Autowired TenantRepository tenants;
    @Autowired DocumentTemplateRepository documentTemplates;
    @Autowired TemplateVersionRepository templateVersions;
    @Autowired CustomerService customers;
    @Autowired ReceivableService receivables;
    @Autowired CampaignService campaigns;
    @Autowired CampaignRunRepository runs;
    @Autowired MessageRepository messages;
    @Autowired MessageDeliveryAttemptRepository attempts;
    @Autowired MessageDeliveryWorker worker;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void realKumoAcceptsAndDeliversThenRejectsControlledRecipient() throws Exception {
        assertThat(context.getBeansOfType(DeliveryGateway.class)).hasSize(1);
        assertThat(context.getBeansOfType(KumoMtaEmailDeliveryGateway.class)).hasSize(1);
        assertThat(context.getBeansOfType(SimulatedDeliveryGateway.class)).isEmpty();

        Fixture accepted = fixture("ok@smoke.test");
        worker.deliver(accepted.tenantId(), accepted.messageId());
        Message sent = messages.findById(accepted.messageId()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(sent.getProviderMessageId()).isNull();
        assertThat(attempts.findAllByMessageIdOrderByAttemptNoAsc(sent.getId())).hasSize(1);
        assertThat(
                        jdbc.queryForObject(
                                "select status from messages where id = ?",
                                String.class,
                                sent.getId()))
                .isEqualTo("SENT");

        JsonNode received = awaitMailbox(accepted.subject());
        assertThat(received.toString()).contains("ok@smoke.test");
        System.out.printf(
                "MAIL-SMOKE success collectraMessageId=%s dbStatus=%s providerMessageId=null mailbox=%s%n",
                sent.getId(), sent.getStatus(), received.toString());

        Fixture rejected = fixture("reject@smoke.test");
        worker.deliver(rejected.tenantId(), rejected.messageId());
        Message failed = messages.findById(rejected.messageId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(failed.getLastErrorCode()).isNotBlank();
        assertThat(attempts.findAllByMessageIdOrderByAttemptNoAsc(failed.getId())).hasSize(1);
        assertThat(
                        jdbc.queryForObject(
                                "select status from messages where id = ?",
                                String.class,
                                failed.getId()))
                .isEqualTo("FAILED");
        assertThat(mailbox().toString()).doesNotContain(rejected.subject());
        System.out.printf(
                "MAIL-SMOKE rejection collectraMessageId=%s dbStatus=%s errorCode=%s%n",
                failed.getId(), failed.getStatus(), failed.getLastErrorCode());
    }

    private JsonNode awaitMailbox(String subject) throws Exception {
        Instant deadline = Instant.now().plusSeconds(45);
        while (Instant.now().isBefore(deadline)) {
            JsonNode result = mailbox();
            for (JsonNode message : result.path("messages")) {
                if (message.toString().contains(subject)) {
                    return message;
                }
            }
            Thread.sleep(500);
        }
        throw new AssertionError(
                "KumoMTA accepted the message, but the test mailbox did not receive it");
    }

    private JsonNode mailbox() throws Exception {
        HttpResponse<String> response =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://localhost:18025/api/v1/messages"))
                                        .timeout(Duration.ofSeconds(5))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private Fixture fixture(String recipientAddress) {
        String suffix = UUID.randomUUID().toString();
        Tenant tenant = tenants.saveAndFlush(new Tenant("mail-smoke-" + suffix, "Mail smoke"));
        DocumentTemplate template =
                documentTemplates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(), "MAIL_" + suffix, "Mail smoke", "INVOICE"));
        String subject = "Collectra mail smoke " + suffix;
        TemplateVersion version =
                new TemplateVersion(
                        template.getId(),
                        1,
                        "en",
                        TemplateChannel.EMAIL,
                        subject,
                        "<p>Mail smoke</p>",
                        null);
        version.validated();
        version.publish();
        version = templateVersions.saveAndFlush(version);
        var customer =
                customers.create(
                        tenant.getId(),
                        "mail-" + suffix,
                        CustomerType.INDIVIDUAL,
                        "Smoke Recipient",
                        "Smoke",
                        "Recipient",
                        null,
                        null,
                        null,
                        "en",
                        "Asia/Almaty",
                        json.createObjectNode());
        customers.addEmail(tenant.getId(), customer.getId(), recipientAddress, "WORK", true);
        LocalDate today = LocalDate.now();
        var invoice =
                receivables.createInvoice(
                        tenant.getId(),
                        customer.getId(),
                        null,
                        "mail-invoice-" + suffix,
                        "MAIL-" + suffix,
                        today.minusDays(20),
                        today.minusDays(10),
                        new BigDecimal("10000.00"),
                        "KZT",
                        null,
                        json.createObjectNode());
        var campaign =
                campaigns.create(
                        tenant.getId(),
                        "Mail smoke",
                        version.getId(),
                        "EMAIL",
                        null,
                        CampaignSelection.customer(Set.of(customer.getId()), Set.of()),
                        null,
                        false,
                        null);
        campaigns.activate(tenant.getId(), campaign.getId());
        var prepared = campaigns.prepare(tenant.getId(), campaign.getId());
        CampaignRun run = campaigns.run(tenant.getId(), prepared.runId());
        run.start(Instant.now());
        run = runs.saveAndFlush(run);
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
                                version.getId(),
                                CommunicationChannel.EMAIL,
                                recipient.getDestination(),
                                version.getLocale(),
                                version.getSubject(),
                                version.getContentHtml()));
        return new Fixture(tenant.getId(), message.getId(), subject);
    }

    private record Fixture(UUID tenantId, UUID messageId, String subject) {}
}
