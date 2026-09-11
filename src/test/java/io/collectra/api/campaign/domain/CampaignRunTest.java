package io.collectra.api.campaign.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CampaignRunTest {
    @Test
    void followsPreparationLifecycle() {
        CampaignRun run = new CampaignRun(UUID.randomUUID(), UUID.randomUUID());

        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.PREPARING);

        run.ready();
        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.READY);
        assertThat(run.getPreparedAt()).isNotNull();

        run.start();
        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.RUNNING);

        run.complete();
        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.COMPLETED);
        assertThat(run.getCompletedAt()).isNotNull();
    }

    @Test
    void rejectsInvalidTransition() {
        CampaignRun run = new CampaignRun(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(run::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected READY");
    }
}
