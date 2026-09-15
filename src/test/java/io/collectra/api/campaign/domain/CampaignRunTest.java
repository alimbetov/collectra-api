package io.collectra.api.campaign.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class CampaignRunTest {
    private static final Instant PREPARED_AT = Instant.parse("2026-09-11T10:00:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-09-11T10:01:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-09-11T10:02:00Z");

    @Test
    void followsPreparationLifecycle() {
        CampaignRun run = new CampaignRun(UUID.randomUUID(), UUID.randomUUID());

        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.PREPARING);
        assertThat(run.getRecipientCount()).isZero();
        assertThat(run.getSentCount()).isZero();
        assertThat(run.getFailedCount()).isZero();
        assertThat(run.getSkippedCount()).isZero();
        assertThat(run.getRetryCount()).isZero();

        run.ready(0, PREPARED_AT);
        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.READY);
        assertThat(run.getPreparedAt()).isEqualTo(PREPARED_AT);

        run.start(STARTED_AT);
        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.RUNNING);
        assertThat(run.getStartedAt()).isEqualTo(STARTED_AT);

        run.complete(COMPLETED_AT);
        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.COMPLETED);
        assertThat(run.getCompletedAt()).isEqualTo(COMPLETED_AT);
    }

    @Test
    void rejectsInvalidTransition() {
        CampaignRun run = new CampaignRun(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> run.start(STARTED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected READY");
    }

    @Test
    void rejectsNegativeRecipientCount() {
        CampaignRun run = new CampaignRun(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> run.ready(-1, PREPARED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recipientCount");
    }

    @Test
    void doesNotCompleteWhileRecipientsAreNonTerminal() {
        CampaignRun run = new CampaignRun(UUID.randomUUID(), UUID.randomUUID());
        run.ready(1, PREPARED_AT);
        run.start(STARTED_AT);

        assertThatThrownBy(() -> run.complete(COMPLETED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-terminal");
    }

    @Test
    void singleRecipientSkipAndIdentityGettersAreCovered() {
        UUID tenantId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        CampaignRun run = new CampaignRun(tenantId, campaignId);
        run.ready(1, PREPARED_AT);
        run.start(STARTED_AT);
        run.recipientSkipped();

        assertThat(run.getTenantId()).isEqualTo(tenantId);
        assertThat(run.getCampaignId()).isEqualTo(campaignId);
        assertThat(run.getSkippedCount()).isOne();
    }

    @Test
    void failRejectsReadyAndTerminalRuns() {
        CampaignRun ready = new CampaignRun(UUID.randomUUID(), UUID.randomUUID());
        ready.ready(1, PREPARED_AT);
        assertThatThrownBy(() -> ready.fail(COMPLETED_AT)).isInstanceOf(IllegalStateException.class);

        CampaignRun completed = new CampaignRun(UUID.randomUUID(), UUID.randomUUID());
        completed.ready(0, PREPARED_AT);
        completed.start(STARTED_AT);
        completed.complete(COMPLETED_AT);
        assertThatThrownBy(() -> completed.fail(COMPLETED_AT)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sentCount", "failedCount", "skippedCount", "retryCount"})
    void detectsEveryNegativePersistedCounter(String field) {
        CampaignRun run = new CampaignRun(UUID.randomUUID(), UUID.randomUUID());
        run.ready(1, PREPARED_AT);
        run.start(STARTED_AT);
        ReflectionTestUtils.setField(run, field, -1);

        assertThatThrownBy(run::messageRetryScheduled)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be negative");
    }
}
