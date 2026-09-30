package io.collectra.api.document.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "collectra.document.generation-recovery")
public class GenerationJobRecoveryProperties {
    private Duration processingTimeout = Duration.ofMinutes(30);
    private Duration maxJobAge = Duration.ofHours(8);
    private int maxAttempts = 3;
    private int batchSize = 100;

    public Duration getProcessingTimeout() { return processingTimeout; }
    public void setProcessingTimeout(Duration processingTimeout) { this.processingTimeout = processingTimeout; }
    public Duration getMaxJobAge() { return maxJobAge; }
    public void setMaxJobAge(Duration maxJobAge) { this.maxJobAge = maxJobAge; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
}
