package com.uteq.asistente_academico.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Una fila de auditoria para GET /api/admin/auditoria. datosAnteriores/
 * datosNuevos son JsonNode (no String): Jackson los serializa como JSON
 * anidado real en la respuesta, no como texto escapado.
 */
public record AuditoriaResumen(
        Long id,
        Instant fechaHora,
        String tabla,
        String esquema,
        String operacion,
        String usuarioApp,
        String usuarioBd,
        String registroId,
        JsonNode datosAnteriores,
        JsonNode datosNuevos
) {
}
