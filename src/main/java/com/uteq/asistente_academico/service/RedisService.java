package com.uteq.asistente_academico.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    private static final Logger log = LoggerFactory.getLogger(RedisService.class);

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public void agregarTokenBlacklist(String token, long tiempoExpiracion) {
        redisTemplate.opsForValue().set(
                "blacklist:" + token,
                "revocado",
                tiempoExpiracion,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * Verifica si un token esta en la blacklist de logout.
     *
     * Si Redis no esta disponible, se registra un WARN y se asume que el
     * token NO esta en la blacklist (fail-open), en vez de tumbar toda
     * peticion autenticada de la aplicacion. Esta decision es deliberada:
     * Redis aqui solo respalda la revocacion por logout, no la validacion
     * principal del JWT (que sigue verificando firma y expiracion). Perder
     * disponibilidad completa de la API por una caida de un componente
     * secundario de cache es un riesgo mayor que dejar pasar, por unos
     * segundos, un token legitimo que aun no habia expirado por su cuenta.
     */
    public boolean estaEnBlacklist(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + token));
        } catch (RedisConnectionFailureException e) {
            log.warn("No se pudo conectar a Redis para verificar blacklist de token; " +
                    "se asume que el token no esta revocado (fail-open). Causa: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Cache-aside generico (Bloque de API externa + cache): guarda
     * cualquier objeto serializable como JSON bajo una clave, con TTL.
     * Usado por FeriadosService para no golpear la API externa en cada
     * request.
     */
    public void guardarJson(String key, Object valor, long ttlSegundos) {
        try {
            String json = objectMapper.writeValueAsString(valor);
            redisTemplate.opsForValue().set(key, json, ttlSegundos, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Igual que estaEnBlacklist: si Redis falla, no debe tumbar la
            // respuesta al cliente. Simplemente no queda cacheado esta vez.
            log.warn("No se pudo guardar en cache la clave '{}' (fail-open). Causa: {}", key, e.getMessage());
        }
    }

    public <T> Optional<T> obtenerJson(String key, TypeReference<T> tipo) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, tipo));
        } catch (Exception e) {
            log.warn("No se pudo leer de cache la clave '{}' (fail-open, se consulta la fuente original). Causa: {}",
                    key, e.getMessage());
            return Optional.empty();
        }
    }
}