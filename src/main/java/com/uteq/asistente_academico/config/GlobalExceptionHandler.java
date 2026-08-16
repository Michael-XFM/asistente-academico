package com.uteq.asistente_academico.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

/**
 * Manejador global de excepciones no capturadas, conforme al Bloque
 * A.1: todos los errores deben responder con ProblemDetails segun
 * RFC 7807 (type, title, status, detail, instance).
 *
 * Este handler cubre las excepciones que NO se manejan explicitamente
 * dentro de cada controlador (ej. errores inesperados, argumentos
 * invalidos que Spring detecta automaticamente). Los controladores que
 * ya devuelven ProblemDetail directamente (ej. AuthController para
 * login) no pasan por aqui.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ProblemDetail manejarExcepcionGeneral(Exception ex, WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrio un error inesperado al procesar la solicitud."
        );
        problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/error-interno"));
        problema.setTitle("Error interno del servidor");
        problema.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        return problema;
    }

    /**
     * AccessDeniedException la lanza el interceptor de @PreAuthorize
     * (Bloque de roles y permisos) cuando el usuario esta autenticado
     * pero su rol no alcanza. Necesita su propio @ExceptionHandler
     * porque, sin este, cae en el manejarExcepcionGeneral(Exception) de
     * arriba y se pierde el 403: el cliente veria un 500 generico en vez
     * de un 403 explicando que le falta el rol adecuado.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail manejarAccesoDenegado(AccessDeniedException ex, WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "Tu rol no tiene permiso para acceder a este recurso."
        );
        problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/rol-insuficiente"));
        problema.setTitle("Acceso prohibido");
        problema.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        return problema;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail manejarArgumentoInvalido(IllegalArgumentException ex, WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/argumento-invalido"));
        problema.setTitle("Argumento invalido");
        problema.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        return problema;
    }
}