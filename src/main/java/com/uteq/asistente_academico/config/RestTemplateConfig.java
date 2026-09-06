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

    /**
     * RestTemplate separado para el chatbot (ChatbotService/Gemini): un
     * LLM tarda bastante mas en responder que la API REST simple de
     * feriados -- reusar el bean de arriba (5s de lectura) haria que
     * timeoutee con respuestas normales. Bean con nombre propio para
     * poder inyectar el correcto con @Qualifier en cada service.
     *
     * 60s (no 30s): confirmado en vivo que las respuestas que requieren
     * mas razonamiento (ej. guiar en vez de resolver un ejercicio)
     * tardan lo suficiente como para que 30s dispare 502/504 seguido,
     * obligando al estudiante a reintentar a mano.
     */
    @Bean
    public RestTemplate geminiRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(60))
                .build();
    }
}
