package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class Slice09dApiCompatibilityIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    void requiredFrontendOperationsRemainAvailable() throws Exception {
        Set<String> actual = new HashSet<>();
        mappings.getHandlerMethods()
                .forEach(
                        (info, method) -> {
                            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                            for (String pattern : info.getPatternValues()) {
                                for (RequestMethod requestMethod : methods) {
                                    actual.add(requestMethod.name() + " " + pattern);
                                }
                            }
                        });

        Set<String> baseline = new HashSet<>();
        ClassPathResource resource = new ClassPathResource("api/slice-09d-required-operations.txt");
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(baseline::add);
        }

        assertThat(actual).containsAll(baseline);
    }
}
