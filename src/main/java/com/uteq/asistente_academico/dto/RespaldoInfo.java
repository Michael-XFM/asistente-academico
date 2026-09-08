package com.uteq.asistente_academico.dto;

/**
 * Resultado de generar un respaldo (POST /api/admin/respaldos).
 */
public record RespaldoInfo(String nombreArchivo, long tamanoBytes) {
}
