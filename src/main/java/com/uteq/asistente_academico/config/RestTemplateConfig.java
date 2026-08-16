package com.uteq.asistente_academico.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate para el consumo de la API externa de feriados
 * (FeriadosService). Con timeouts explicitos: sin ellos, RestTemplate
 * espera indefinidamente si la API externa cuelga la conexion, lo que
 * dejaria threads del servidor bloqueados esperando una respuesta que
 * nunca llega.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .build();
    }
}
