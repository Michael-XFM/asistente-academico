package com.uteq.asistente_academico.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

/**
 * sp_reporte_calificaciones (Bloque A.2 — reportes): declarado aquí como
 * @NamedStoredProcedureQuery, invocado desde CalificacionRepository con
 * @Procedure(name = "Calificacion.reporteCalificaciones") — el mecanismo
 * formal de JPA 2.1 que exige explícitamente el rubro de esta entrega,
 * en vez del @Procedure(procedureName=...) "implícito" usado en
 * HorarioRepository/TareaRepository para los otros dos procedimientos
 * nuevos.
 */
@NamedStoredProcedureQuery(
        name = "Calificacion.reporteCalificaciones",
        procedureName = "sp_reporte_calificaciones",
        parameters = {
                @StoredProcedureParameter(mode = ParameterMode.IN, name = "p_id_usuario", type = Integer.class),
                @StoredProcedureParameter(mode = ParameterMode.OUT, name = "p_promedio", type = BigDecimal.class),
                @StoredProcedureParameter(mode = ParameterMode.OUT, name = "p_total_materias", type = Integer.class),
                @StoredProcedureParameter(mode = ParameterMode.OUT, name = "p_mejor_materia", type = String.class),
                @StoredProcedureParameter(mode = ParameterMode.OUT, name = "p_peor_materia", type = String.class)
        }
)
@Data
@Entity
@Table(name = "Calificaciones")
public class Calificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_calificacion")
    private Integer idCalificacion;

    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "id_materia", nullable = false)
    private Materia materia;

    @Column(name = "nota", nullable = false, precision = 4, scale = 2)
    private BigDecimal nota;
}
