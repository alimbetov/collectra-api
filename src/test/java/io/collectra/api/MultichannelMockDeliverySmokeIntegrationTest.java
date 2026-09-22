package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.campaign.application.CampaignAttachmentService;
import io.collectra.api.campaign.application.CampaignMessageMaterializer;
import io.collectra.api.campaign.application.CampaignSelection;
import io.collectra.api.campaign.application.CampaignService;
import io.collectra.api.communication.application.MessageAttachmentService;
import io.collectra.api.communication.application.MessageDeliveryWorker;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageAttachmentStatus;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageAttachmentRepository;
import io.collectra.api.communication.infrastructure.MessageDeliveryAttemptRepository;
import io.collectra.api.communication.infrastructure.MessageRepository;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.document.application.DocumentGenerationWorker;
import io.collectra.api.document.application.DocumentStorage;
import io.collectra.api.document.domain.GenerationJobStatus;
import io.collectra.api.document.infrastructure.GeneratedDocumentRepository;
import io.collectra.api.document.infrastructure.GenerationJobRepository;
import io.collectra.api.localization.domain.TenantLocale;
import io.collectra.api.localization.infrastructure.TenantLocaleRepository;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;

@Import(MultichannelMockDeliverySmokeIntegrationTest.StorageConfig.class)
@SpringBootTest(
        properties = {
            "collectra.communication.delivery.enabled=true",
            "collectra.communication.delivery.provider=simulated",
            "collectra.communication.delivery.simulation.success-rate-percent=100",
            "collectra.communication.delivery.simulation.permanent-failure-rate-percent=0"
        })
class MultichannelMockDeliverySmokeIntegrationTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenants;
    @Autowired TenantLocaleRepository tenantLocales;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateVersionRepository versions;
    @Autowired CustomerService customers;
    @Autowired CampaignService campaigns;
    @Autowired CampaignAttachmentService campaignAttachments;
    @Autowired CampaignMessageMaterializer materializer;
    @Autowired DocumentGenerationWorker documentWorker;
    @Autowired MessageAttachmentService attachmentService;
    @Autowired MessageDeliveryWorker deliveryWorker;
    @Autowired MessageRepository messages;
    @Autowired MessageAttachmentRepository attachments;
    @Autowired MessageDeliveryAttemptRepository attempts;
    @Autowired GenerationJobRepository generationJobs;
    @Autowired GeneratedDocumentRepository generatedDocuments;
    @Autowired DocumentStorage storage;
    @Autowired ObjectMapper json;

    @Test
    void emailCampaignCreatesMessageGeneratesPdfAttachmentAndSendsThroughSimulatedGateway() {
        Tenant tenant = tenant("mock-email-pdf");
        var customer =
                customer(tenant, "EMAIL-CUSTOMER", "Email Smoke Customer", "ru");
        customers.addEmail(tenant.getId(), customer.id(), "email-smoke@example.test", "WORK", true);

        TemplateVersion emailTemplate =
                publishedTemplate(
                        tenant,
                        TemplateChannel.EMAIL,
                        "EMAIL_NOTICE",
                        "Invoice for {{customer.name}}",
                        "<p>Hello {{customer.name}}</p><p>External: {{customer.externalId}}</p>");
        TemplateVersion pdfTemplate =
                publishedTemplate(
                        tenant,
                        TemplateChannel.PDF,
                        "PDF_NOTICE",
                        null,
                        "<h1>PDF notice</h1><p>{{customer.name}}</p>");

        var campaign =
                campaigns.create(
                        tenant.getId(),
                        "Email PDF smoke",
                        emailTemplate.getId(),
                        CommunicationChannel.EMAIL.name(),
                        null,
                        CampaignSelection.customer(Set.of(customer.id()), Set.of()),
                        pdfTemplate.getId(),
                        false,
                        null);
        campaignAttachments.configureGeneratedPdf(tenant.getId(), campaign.getId(), true, true);
        campaigns.activate(tenant.getId(), campaign.getId());
        var prepared = campaigns.prepare(tenant.getId(), campaign.getId());

        var materialized = materializer.materializeNextBatch(tenant.getId(), prepared.runId(), 100);

        assertThat(materialized.queued()).isEqualTo(1);
        Message queued = onlyMessage(tenant.getId(), prepared.runId());
        assertThat(queued.getStatus()).isEqualTo(MessageStatus.QUEUED);
        assertThat(queued.getDeliveryRequestedAt()).isNull();
        assertThat(queued.getSubject()).isEqualTo("Invoice for Email Smoke Customer");
        assertThat(queued.getBody()).contains("Hello Email Smoke Customer");

        var attachment =
                attachments.findAllByTenantIdAndMessageIdOrderByCreatedAtAsc(
                                tenant.getId(), queued.getId())
                        .get(0);
        assertThat(attachment.getStatus()).isEqualTo(MessageAttachmentStatus.PENDING);

        documentWorker.generate(tenant.getId(), attachment.getGenerationJobId());
        assertThat(
                        generationJobs
                                .findById(attachment.getGenerationJobId())
                                .orElseThrow()
                                .getStatus())
                .isEqualTo(GenerationJobStatus.COMPLETED);
        var pdf =
                generatedDocuments
                        .findByTenantIdAndGenerationJobIdAndFormat(
                                tenant.getId(),
                                attachment.getGenerationJobId(),
                                io.collectra.api.document.domain.OutputFormat.PDF)
                        .orElseThrow();
        assertThat(storage.get(pdf.getStorageKey()))
                .startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        assertThat(attachmentService.generationCompleted(tenant.getId(), attachment.getGenerationJobId()))
                .isTrue();
        Message ready = messages.findById(queued.getId()).orElseThrow();
        assertThat(ready.getDeliveryRequestedAt()).isNotNull();

        deliveryWorker.deliver(tenant.getId(), queued.getId());

        Message sent = messages.findById(queued.getId()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(sent.getProviderMessageId())
                .isEqualTo("simulated:email:" + queued.getId());
        assertThat(attempts.findAllByMessageIdOrderByAttemptNoAsc(queued.getId()))
                .singleElement()
                .satisfies(
                        attempt -> {
                            assertThat(attempt.getProviderReference())
                                    .isEqualTo("simulated:email:" + queued.getId());
                            assertThat(attempt.getErrorCode()).isNull();
                        });
    }

    @Test
    void smsWhatsappAndTelegramCampaignsSendThroughSimulatedGateway() {
        for (CommunicationChannel channel :
                List.of(
                        CommunicationChannel.SMS,
                        CommunicationChannel.WHATSAPP,
                        CommunicationChannel.TELEGRAM)) {
            Tenant tenant = tenant("mock-" + channel.name().toLowerCase());
            var customer =
                    customer(tenant, channel.name() + "-CUSTOMER", channel + " Smoke Customer", "ru");
            if (channel == CommunicationChannel.TELEGRAM) {
                customers.addChannelAddress(
                        tenant.getId(),
                        customer.id(),
                        CommunicationChannel.TELEGRAM.name(),
                        "123456789",
                        true,
                        Instant.parse("2026-09-22T00:00:00Z"));
            } else {
                customers.addPhone(tenant.getId(), customer.id(), "+77011234567", "MOBILE", true);
            }

            TemplateVersion template =
                    publishedTemplate(
                            tenant,
                            TemplateChannel.valueOf(channel.name()),
                            channel + "_NOTICE",
                            null,
                            channel + " hello {{customer.name}}");

            var campaign =
                    campaigns.create(
                            tenant.getId(),
                            channel + " mock smoke",
                            template.getId(),
                            channel.name(),
                            null,
                            CampaignSelection.customer(Set.of(customer.id()), Set.of()),
                            null);
            campaigns.activate(tenant.getId(), campaign.getId());
            var prepared = campaigns.prepare(tenant.getId(), campaign.getId());
            var materialized =
                    materializer.materializeNextBatch(tenant.getId(), prepared.runId(), 100);

            assertThat(materialized.queued()).isEqualTo(1);
            Message queued = onlyMessage(tenant.getId(), prepared.runId());
            assertThat(queued.getBody()).contains(channel + " hello " + channel + " Smoke Customer");

            deliveryWorker.deliver(tenant.getId(), queued.getId());

            Message sent = messages.findById(queued.getId()).orElseThrow();
            assertThat(sent.getStatus()).isEqualTo(MessageStatus.SENT);
            assertThat(sent.getProviderMessageId())
                    .isEqualTo("simulated:" + channel.name().toLowerCase() + ":" + queued.getId());
        }
    }

    private Tenant tenant(String prefix) {
        Tenant tenant =
                tenants.saveAndFlush(new Tenant(prefix + "-" + UUID.randomUUID(), "Smoke Tenant"));
        tenantLocales.saveAndFlush(new TenantLocale(tenant.getId(), "ru", true, true, 0));
        return tenant;
    }

    private CustomerRef customer(Tenant tenant, String externalId, String name, String locale) {
        var value =
                customers.create(
                        tenant.getId(),
                        externalId + "-" + UUID.randomUUID(),
                        CustomerType.INDIVIDUAL,
                        name,
                        name.split(" ")[0],
                        "Customer",
                        null,
                        null,
                        null,
                        locale,
                        "Asia/Almaty",
                        json.createObjectNode());
        return new CustomerRef(value.getId());
    }

    private TemplateVersion publishedTemplate(
            Tenant tenant,
            TemplateChannel channel,
            String code,
            String subject,
            String content) {
        DocumentTemplate template =
                templates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(),
                                code + "_" + UUID.randomUUID(),
                                code,
                                "NOTIFICATION"));
        TemplateVersion version =
                new TemplateVersion(
                        template.getId(), 1, "ru", channel, subject, content, null);
        version.validated();
        version.publish();
        return versions.saveAndFlush(version);
    }

    private Message onlyMessage(UUID tenantId, UUID runId) {
        return messages.findAllByTenantIdAndCampaignRunId(tenantId, runId, PageRequest.of(0, 10))
                .getContent()
                .get(0);
    }

    private record CustomerRef(UUID id) {}

    @TestConfiguration
    static class StorageConfig {
        @Bean
        @Primary
        DocumentStorage inMemoryDocumentStorage() {
            return new InMemoryStorage();
        }
    }

    static class InMemoryStorage implements DocumentStorage {
        private final Map<String, byte[]> values = new ConcurrentHashMap<>();

        @Override
        public StoredObject put(String key, byte[] content, String mediaType) {
            values.put(key, content.clone());
            try {
                String sha =
                        HexFormat.of()
                                .formatHex(MessageDigest.getInstance("SHA-256").digest(content));
                return new StoredObject(key, mediaType, content.length, sha);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public byte[] get(String key) {
            return values.get(key).clone();
        }
    }
}
