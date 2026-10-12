package com.nexora.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI nexoraOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Nexora API")
                        .version("v1")
                        .description("Campus incident, maintenance workflow, and equipment management API"))
                // Global security: tất cả endpoints mặc định yêu cầu Bearer JWT
                // Endpoint login dùng @SecurityRequirements({}) để override thành public
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}

