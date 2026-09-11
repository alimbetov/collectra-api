package io.collectra.api.campaign.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
}
