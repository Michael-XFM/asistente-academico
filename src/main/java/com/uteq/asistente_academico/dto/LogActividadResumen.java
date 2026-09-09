package com.uteq.asistente_academico.dto;

import java.util.List;

/**
 * Respuesta de GET /api/admin/log-actividad/resumen. La suma de
 * porUsuario[].cantidad() siempre coincide exacto con total (LEFT JOIN
 * en LogActividadRepository.contarPorUsuario, ninguna fila queda fuera).
 */
public record LogActividadResumen(long total, List<LogActividadPorUsuario> porUsuario) {
}
