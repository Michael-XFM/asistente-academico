package com.uteq.asistente_academico.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Entity
@Table(name = "Tareas")
public class Tarea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tarea")
    private Integer idTarea;

    @ManyToOne
    @JoinColumn(name = "id_materia", nullable = false)
    private Materia materia;

    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    @Column(name = "fecha_entrega", nullable = false)
    private LocalDate fechaEntrega;

    // Hora de cierre del dia de fecha_entrega. Columna separada (no se
    // fusiono con fecha_entrega en un TIMESTAMP) para no romper el codigo
    // existente que trata fecha_entrega como LocalDate -- ver V4__hora_limite_tarea.sql.
    // Inicializada en 23:59:59 aca (ademas del DEFAULT en la base de
    // datos) porque Hibernate incluye esta columna en todo INSERT --el
    // DEFAULT de Postgres solo aplica si la columna se omite del INSERT,
    // y varios puntos del codigo (TareaController.crear, tests, seeds)
    // construyen un Tarea sin fijarla explicitamente.
    @Column(name = "hora_limite", nullable = false)
    private LocalTime horaLimite = LocalTime.of(23, 59, 59);

    // Folio de seguimiento legible (ej. "TAR-2026-000042"), generado por
    // sp_generar_codigo_tarea al crear la tarea. Nullable: las tareas
    // creadas antes de V2__add_codigo_tarea.sql no tienen uno retroactivo.
    @Column(name = "codigo", length = 20)
    private String codigo;
}