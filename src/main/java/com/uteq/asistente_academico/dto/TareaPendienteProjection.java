package com.uteq.asistente_academico.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Proyección de Spring Data para el resultado de la función SQL
 * fn_listar_tareas_pendientes. No corresponde a ninguna entidad JPA:
 * combina Tarea + Materia (JOIN) más columnas calculadas.
 *
 * segundosRestantes combina fecha_entrega + hora_limite contra NOW() en
 * el propio SQL (ver fn_listar_tareas_pendientes.sql) — puede ser
 * negativo si la hora límite de hoy ya pasó, aunque diasRestantes siga
 * en 0 (granularidad de día, sin cambios respecto a antes).
 */
public interface TareaPendienteProjection {
    Integer getIdTarea();
    String getTitulo();
    String getMateria();
    LocalDate getFechaEntrega();
    LocalTime getHoraLimite();
    Integer getDiasRestantes();
    Integer getSegundosRestantes();
}