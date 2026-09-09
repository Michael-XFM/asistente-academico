package com.uteq.asistente_academico.dto;

/**
 * Una fila del desglose de GET /api/admin/log-actividad/resumen.
 * idUsuario viene null para el bucket "Sin usuario asignado" (filas de
 * log_actividad sin id_usuario valido) -- ver LogActividadRepository.
 */
public record LogActividadPorUsuario(Integer idUsuario, String nombre, Long cantidad) {
}
