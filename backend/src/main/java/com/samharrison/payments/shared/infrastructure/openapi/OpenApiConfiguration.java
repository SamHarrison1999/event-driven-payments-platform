package com.samharrison.payments.shared.infrastructure.openapi;

import com.samharrison.payments.shared.config.PlatformProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI platformOpenApi(
        PlatformProperties platformProperties
    ) {
        var licence = new License()
            .name("MIT");

        var info = new Info()
            .title(platformProperties.name())
            .description(
                platformProperties.description()
                    + ". Educational system; "
                    + "does not process real money."
            )
            .version(platformProperties.version())
            .license(licence);

        return new OpenAPI()
            .info(info);
    }
}
