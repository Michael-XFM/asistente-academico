package com.uteq.asistente_academico.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.uteq.asistente_academico.dto.Feriado;
import com.uteq.asistente_academico.dto.ResultadoFeriados;
import com.uteq.asistente_academico.exception.ApiExternaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Consume la API publica Nager.Date (https://date.nager.at) para
 * obtener los feriados nacionales de un pais/anio, relevantes para el
 * calendario academico (dias sin clases). Implementa cache-aside con
 * Redis: se consulta el cache primero, y solo se llama a la API
 * externa en caso de fallo de cache (miss).
 */
@Service
public class FeriadosService {

    private static final Logger log = LoggerFactory.getLogger(FeriadosService.class);

    private static final String URL_NAGER = "https://date.nager.at/api/v3/PublicHolidays/{anio}/{pais}";

    // TTL corto (1 hora) a proposito: los feriados de un anio no cambian
    // en tiempo real, pero mantenerlo bajo permite demostrar el patron
    // cache-aside completo (miss -> hit -> expiracion) sin esperar dias.
    private static final long TTL_SEGUNDOS = 3600;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RedisService redisService;

    public ResultadoFeriados obtener(int anio, String pais) {
        String clave = "feriados:" + pais + ":" + anio;

        Optional<List<Feriado>> enCache = redisService.obtenerJson(clave, new TypeReference<List<Feriado>>() {});
        if (enCache.isPresent()) {
            log.info("CACHE HIT | clave={}", clave);
            return new ResultadoFeriados(enCache.get(), "CACHE");
        }

        log.info("CACHE MISS | clave={} | consultando API externa", clave);
        List<Feriado> feriados = consultarApiExterna(anio, pais);

        redisService.guardarJson(clave, feriados, TTL_SEGUNDOS);
        return new ResultadoFeriados(feriados, "API_EXTERNA");
    }

    private List<Feriado> consultarApiExterna(int anio, String pais) {
        try {
            Feriado[] respuesta = restTemplate.getForObject(URL_NAGER, Feriado[].class, anio, pais);
            return respuesta != null ? Arrays.asList(respuesta) : List.of();
        } catch (HttpClientErrorException e) {
            // 4xx: la API externa rechazo la solicitud. El caso tipico es un
            // codigo de pais o un anio invalidos, que es responsabilidad de
            // quien llamo a NUESTRO endpoint, no un fallo del servidor.
            throw new ApiExternaException(
                    HttpStatus.BAD_REQUEST,
                    "La API externa de feriados rechazo la solicitud (año o código de país inválido).",
                    e
            );
        } catch (HttpServerErrorException e) {
            // 5xx: el problema es de la API externa, no de quien nos llamo.
            throw new ApiExternaException(
                    HttpStatus.BAD_GATEWAY,
                    "El servicio externo de feriados no está disponible en este momento.",
                    e
            );
        } catch (ResourceAccessException e) {
            // Timeout de conexion o de lectura (ver RestTemplateConfig).
            throw new ApiExternaException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Se agotó el tiempo de espera al consultar el servicio externo de feriados.",
                    e
            );
        }
    }
}
