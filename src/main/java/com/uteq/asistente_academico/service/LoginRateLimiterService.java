package com.uteq.asistente_academico.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Control de intentos fallidos de login por IP (OWASP A07 - Fallo de
 * identificacion y autenticacion). Tras 5 intentos fallidos
 * consecutivos desde la misma IP, se bloquean nuevos intentos con 429
 * hasta que haya un login exitoso (que reinicia el contador).
 *
 * Implementacion en memoria (ConcurrentHashMap), adecuada para una
 * unica instancia del backend como en esta entrega. Si el sistema
 * llegara a escalar a multiples instancias, este contador tendria que
 * moverse a Redis para ser compartido entre ellas.
 */
@Service
public class LoginRateLimiterService {

    private static final int MAX_INTENTOS = 5;

    private final ConcurrentHashMap<String, AtomicInteger> intentosPorIp = new ConcurrentHashMap<>();

    public boolean estaBloqueado(String ip) {
        AtomicInteger intentos = intentosPorIp.get(ip);
        return intentos != null && intentos.get() >= MAX_INTENTOS;
    }

    public void registrarIntentoFallido(String ip) {
        intentosPorIp.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public void registrarLoginExitoso(String ip) {
        intentosPorIp.remove(ip);
    }

    public int intentosActuales(String ip) {
        AtomicInteger intentos = intentosPorIp.get(ip);
        return intentos == null ? 0 : intentos.get();
    }
}