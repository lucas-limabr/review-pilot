package com.reviewpilot.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String INTERNAL_TOKEN_SCHEME = "internalServiceToken";

    @Bean
    public OpenAPI reviewPilotOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ReviewPilot API")
                        .version("0.1.0")
                        .description("Backend API for ReviewPilot — AI Code Review Agent orchestrated via n8n workflows."))
                .components(new Components()
                        .addSecuritySchemes(INTERNAL_TOKEN_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Internal-Service-Token")
                                .description("Internal service authentication token shared between Backend and n8n")));
    }
}

