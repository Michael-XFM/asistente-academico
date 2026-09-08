package com.uteq.asistente_academico.dto;

import java.time.Instant;

/**
 * Una fila del historial de respaldos (GET /api/admin/respaldos).
 */
public record RespaldoResumen(String nombreArchivo, long tamanoBytes, Instant fechaModificacion) {
}
