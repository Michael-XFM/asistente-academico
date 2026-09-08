package com.uteq.asistente_academico.dto;

/**
 * Resultado de restaurar un respaldo a la base alterna
 * (POST /api/admin/respaldos/{nombreArchivo}/restaurar). conteoUsuarios
 * es la verificacion basica de que la restauracion trajo datos reales,
 * no una base vacia.
 */
public record RestauracionInfo(String nombreArchivo, long conteoUsuarios) {
}
