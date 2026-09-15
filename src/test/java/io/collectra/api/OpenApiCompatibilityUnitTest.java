package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.openapitools.openapidiff.core.OpenApiCompare;

class OpenApiCompatibilityUnitTest {

    @Test
    void detectsRemovedPublicOperationAsBreaking() {
        String baseline =
                """
                {"openapi":"3.0.1","info":{"title":"test","version":"1"},"paths":{
                  "/api/v1/items":{"get":{"responses":{"200":{"description":"OK"}}}}
                }}
                """;
        String current =
                """
                {"openapi":"3.0.1","info":{"title":"test","version":"1"},"paths":{}}
                """;

        assertThat(OpenApiCompare.fromContents(baseline, current).isCompatible()).isFalse();
    }

    @Test
    void permitsAdditivePublicOperation() {
        String baseline =
                """
                {"openapi":"3.0.1","info":{"title":"test","version":"1"},"paths":{}}
                """;
        String current =
                """
                {"openapi":"3.0.1","info":{"title":"test","version":"1"},"paths":{
                  "/api/v1/items":{"get":{"responses":{"200":{"description":"OK"}}}}
                }}
                """;

        assertThat(OpenApiCompare.fromContents(baseline, current).isCompatible()).isTrue();
    }
}
