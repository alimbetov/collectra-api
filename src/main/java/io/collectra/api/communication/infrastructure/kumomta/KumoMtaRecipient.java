package io.collectra.api.communication.infrastructure.kumomta;

import java.util.Map;

record KumoMtaRecipient(String email, Map<String, String> metadata) {}
