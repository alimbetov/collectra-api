package io.collectra.api.shared.openapi;

import io.collectra.api.shared.api.DecimalString;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    static {
        SpringDocUtils.getConfig()
                .replaceWithSchema(
                        DecimalString.class, new StringSchema().pattern(DecimalString.PATTERN));
    }

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
