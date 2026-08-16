package com.uteq.asistente_academico.exception;

import org.springframework.http.HttpStatus;

/**
 * Envuelve cualquier fallo al consumir una API externa (timeout, 4xx,
 * 5xx) con el HttpStatus que le corresponde devolver AL CLIENTE de
 * nuestra propia API, que no es necesariamente el mismo status que
 * devolvio la API externa (ver FeriadosService).
 */
public class ApiExternaException extends RuntimeException {

    private final HttpStatus status;

    public ApiExternaException(HttpStatus status, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
