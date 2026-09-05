package com.uteq.asistente_academico.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "Materia")
public class Materia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_materia")
    private Integer idMateria;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    // Nullable: no toda materia tiene profesor asignado (ver
    // V3__matricula_y_profesor.sql). Sin @JsonIgnore porque, a
    // diferencia de Usuario.contrasena, no hay dato sensible aca -- el
    // profesor de una materia es informacion publica dentro del sistema.
    @ManyToOne
    @JoinColumn(name = "id_profesor")
    private Usuario profesor;
}