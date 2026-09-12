package io.collectra.api.communication.infrastructure.kumomta;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

record KumoMtaInjectResponse(
        @JsonProperty("success_count") int successCount,
        @JsonProperty("fail_count") int failCount,
        @JsonProperty("failed_recipients") List<String> failedRecipients,
        List<String> errors) {
    KumoMtaInjectResponse {
        failedRecipients = failedRecipients == null ? List.of() : List.copyOf(failedRecipients);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    boolean acceptedSingleRecipient() {
        return successCount == 1 && failCount == 0 && failedRecipients.isEmpty();
    }
}
