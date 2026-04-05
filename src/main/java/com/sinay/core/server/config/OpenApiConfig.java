package com.sinay.core.server.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger konfigürasyonu.
 * <p>
 * API dokümantasyonu için OpenAPI 3.0 spesifikasyonunu yapılandırır.
 * JWT token tabanlı kimlik doğrulama içerir.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI sinayOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        Server localServer = new Server();
        localServer.setUrl("http://localhost:" + serverPort);
        localServer.setDescription("Yerel geliştirme sunucusu");

        return new OpenAPI()
                .info(new Info()
                        .title("Sinay REST API")
                        .description("Sinay REST API Core - Gelişime açık REST API çekirdek yapısı")
                        .version("v1.0")
                        .contact(new Contact()
                                .name("Uğur Işık")
                                .email("ugur@sinay.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(localServer))
                .addSecurityItem(new SecurityRequirement()
                        .addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT token ile yetkilendirme (token'ı 'Bearer <token>' formatında girin)")));
    }
}
