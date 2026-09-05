package com.uteq.asistente_academico.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Relacion estudiante <-> materia. Necesaria para que el rol PROFESOR
 * pueda filtrar "mis estudiantes" y para que un estudiante solo vea
 * horario de las materias donde esta matriculado (GET /api/horarios/mios).
 *
 * No hay una restriccion a nivel de base de datos que obligue a que
 * "usuario" tenga rol ESTUDIANTE (usuarios.rol es VARCHAR libre, sin
 * CHECK ni enum, mismo criterio que el resto del esquema) -- se valida
 * en el controlador que la matricula.
 */
@Data
@Entity
@Table(name = "Matricula")
public class Matricula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_matricula")
    private Integer idMatricula;

    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "id_materia", nullable = false)
    private Materia materia;

    @Column(name = "fecha_matricula", nullable = false)
    private LocalDateTime fechaMatricula;
}
