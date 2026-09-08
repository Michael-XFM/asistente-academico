package com.uteq.asistente_academico.exception;

import org.springframework.http.HttpStatus;

/**
 * Excepcion de negocio para respaldo y restauracion (RespaldoService).
 * Mismo patron que EntregaException: status/tipo/titulo/mensaje propios,
 * traducidos a ProblemDetail por GlobalExceptionHandler.
 */
public class RespaldoException extends RuntimeException {

    private final HttpStatus status;
    private final String tipo;
    private final String titulo;

    public RespaldoException(HttpStatus status, String tipo, String titulo, String mensaje) {
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
