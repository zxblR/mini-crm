package com.minicrm.config;

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
  OpenAPI miniCrmOpenApi() {
    return new OpenAPI()
        .info(new Info().title("Mini CRM API").description("Mini CRM Java backend API").version("1.0"))
        .components(new Components().addSecuritySchemes("bearerAuth",
            new SecurityScheme()
                .name("Authorization")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
  }
}
