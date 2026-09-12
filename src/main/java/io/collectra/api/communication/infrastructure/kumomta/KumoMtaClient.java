package io.collectra.api.communication.infrastructure.kumomta;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "collectra.communication.delivery.provider", havingValue = "kumomta")
public class KumoMtaClient {
    static final String INJECT_PATH = "/api/inject/v1";

    private final RestClient client;

    public KumoMtaClient(KumoMtaProperties properties) {
        properties.validate();
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        RestClient.Builder builder =
                RestClient.builder()
                        .baseUrl(stripTrailingSlash(properties.getBaseUrl()))
                        .requestFactory(requestFactory);
        if (properties.hasBasicAuth()) {
            builder.defaultHeaders(
                    headers ->
                            headers.setBasicAuth(
                                    properties.getUsername(), properties.getPassword()));
        }
        this.client = builder.build();
    }

    KumoMtaInjectResponse inject(KumoMtaInjectRequest request) {
        return client.post()
                .uri(INJECT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(KumoMtaInjectResponse.class);
    }

    private static String stripTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
