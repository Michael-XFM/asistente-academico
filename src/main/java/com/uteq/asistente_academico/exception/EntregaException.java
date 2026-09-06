package com.uteq.asistente_academico.exception;

import org.springframework.http.HttpStatus;

/**
 * Excepcion de negocio para el flujo de entregas (subida, listado,
 * descarga, calificacion). EntregaService la lanza con un status/tipo/
 * titulo especifico por cada validacion que falla (formato no permitido,
 * tamano excedido, tarea vencida, no matriculado, etc.); GlobalExceptionHandler
 * la traduce a un ProblemDetail (RFC 7807) en vez de dejarla escapar como
 * 500 generico -- mismo patron que ya usa ApiExternaException.
 */
public class EntregaException extends RuntimeException {

    private final HttpStatus status;
    private final String tipo;
    private final String titulo;

    public EntregaException(HttpStatus status, String tipo, String titulo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.tipo = tipo;
        this.titulo = titulo;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTipo() {
        return tipo;
    }

    public String getTitulo() {
        return titulo;
    }
}
