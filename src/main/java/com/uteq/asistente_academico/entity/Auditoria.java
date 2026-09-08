package com.uteq.asistente_academico.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Fila de la tabla auditoria, poblada exclusivamente por el trigger de
 * Postgres (fn_auditoria) -- la app nunca inserta ni actualiza esto via
 * JPA. @Immutable evita que Hibernate haga dirty-checking sobre esta
 * entidad; a proposito no tiene setters.
 */
@Entity
@Table(name = "auditoria")
@Immutable
public class Auditoria {

    @Id
    private Long id;

    @Column(name = "fecha_hora")
    private Instant fechaHora;

    @Column(name = "usuario_bd")
    private String usuarioBd;

    @Column(name = "usuario_app")
    private String usuarioApp;

    private String operacion;

    private String esquema;

    private String tabla;

    @Column(name = "registro_id")
    private String registroId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_anteriores")
    private JsonNode datosAnteriores;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_nuevos")
    private JsonNode datosNuevos;

    @Column(name = "transaccion_id")
    private Long transaccionId;

    public Long getId() {
        return id;
    }

    public Instant getFechaHora() {
        return fechaHora;
    }

    public String getUsuarioBd() {
        return usuarioBd;
    }

    public String getUsuarioApp() {
        return usuarioApp;
    }

    public String getOperacion() {
        return operacion;
    }

    public String getEsquema() {
        return esquema;
    }

    public String getTabla() {
        return tabla;
    }

    public String getRegistroId() {
        return registroId;
    }

    public JsonNode getDatosAnteriores() {
        return datosAnteriores;
    }

    public JsonNode getDatosNuevos() {
        return datosNuevos;
    }

    public Long getTransaccionId() {
        return transaccionId;
    }
}
