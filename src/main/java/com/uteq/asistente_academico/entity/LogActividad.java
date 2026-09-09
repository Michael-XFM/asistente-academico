package com.uteq.asistente_academico.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Fila de log_actividad, poblada exclusivamente por el script de carga
 * masiva del Bloque ABD -- la app nunca inserta ni actualiza esto via
 * JPA, igual que Auditoria. Solo mapea las columnas que hacen falta para
 * GET /api/admin/log-actividad/resumen (conteo total y desglose por
 * usuario); accion/detalle/fecha_hora/ip_origen no se usan ahi y no
 * estan mapeadas.
 */
@Entity
@Table(name = "log_actividad")
@Immutable
public class LogActividad {

    @Id
    private Long id;

    @Column(name = "id_usuario")
    private Integer idUsuario;

    public Long getId() {
        return id;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }
}
