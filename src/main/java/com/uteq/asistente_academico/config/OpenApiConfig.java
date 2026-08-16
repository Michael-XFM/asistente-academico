package com.uteq.asistente_academico.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_JWT = "bearer-jwt";

    @Bean
    public OpenAPI asistenteAcademicoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Asistente Académico UTEQ — API")
                        .description("API REST del asistente académico: autenticación JWT, " +
                                "tareas, dashboard y roles de usuario.")
                        .version("v1")
                        .contact(new Contact()
                                .name("UTEQ — Carrera de Ingeniería de Software")
                                .email("gguerrero@uteq.edu.ec")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT))
                .components(new Components()
                        .addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
                                .name(ESQUEMA_JWT)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token obtenido en POST /api/auth/login. " +
                                        "Pegar solo el valor del token, sin el prefijo 'Bearer '.")));
    }
}
