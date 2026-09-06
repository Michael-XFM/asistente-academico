package com.uteq.asistente_academico.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Estado de entrega de UN estudiante matriculado, para UNA tarea puntual.
 * Usado por GET /api/tareas/{idTarea}/entregas (listado del profesor): no
 * es una entidad JPA, combina Usuario (siempre) + Entrega (si ya entrego).
 */
public record EstadoEntregaEstudiante(
        Integer idUsuario,
        String nombreEstudiante,
        boolean entrego,
        Integer idEntrega,
        String nombreArchivo,
        LocalDateTime fechaEnvio,
        BigDecimal calificacion,
        String comentarioProf,
        LocalDateTime fechaCalificacion
) {
}
