package io.collectra.api.shared.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI collectraOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Collectra API")
                                .version("v1")
                                .description(
                                        "Tenant-aware collection and communications platform"));
    }
}
