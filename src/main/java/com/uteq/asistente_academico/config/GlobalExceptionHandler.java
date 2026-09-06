package com.uteq.asistente_academico.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import com.uteq.asistente_academico.exception.ApiExternaException;
import com.uteq.asistente_academico.exception.EntregaException;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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

    /**
     * Fallo al consumir una API externa (timeout, 4xx o 5xx de la API
     * de terceros), lanzada desde FeriadosService. El status HTTP que
     * corresponde devolver ya viene decidido en la excepcion (400 si la
     * culpa es de los parametros de entrada, 502/504 si la culpa es del
     * servicio externo) — ver ApiExternaException.
     */
    @ExceptionHandler(ApiExternaException.class)
    public ProblemDetail manejarApiExterna(ApiExternaException ex, WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/api-externa"));
        problema.setTitle("Fallo en servicio externo");
        problema.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        return problema;
    }

    /**
     * Errores de negocio del flujo de entregas (formato no permitido,
     * tamano excedido, tarea vencida, no matriculado, sin permiso, etc.)
     * lanzados por EntregaService -- cada EntregaException ya trae su
     * propio status/tipo/titulo/mensaje, este handler solo los traduce a
     * ProblemDetail. Mismo patron que manejarApiExterna.
     */
    @ExceptionHandler(EntregaException.class)
    public ProblemDetail manejarEntrega(EntregaException ex, WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/" + ex.getTipo()));
        problema.setTitle(ex.getTitulo());
        problema.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        return problema;
    }

    /**
     * Spring rechaza el archivo ANTES de que llegue a EntregaController/
     * EntregaService cuando supera spring.servlet.multipart.max-file-size
     * (Bloque de entregas). ResponseEntityExceptionHandler YA declara un
     * @ExceptionHandler para MaxUploadSizeExceededException en su propio
     * handleException(...) interno -- agregar otro @ExceptionHandler para
     * el mismo tipo en esta clase produce un "Ambiguous @ExceptionHandler
     * method" en el arranque (dos metodos igual de especificos para la
     * misma excepcion). La forma correcta de personalizar la respuesta es
     * sobreescribir este hook protegido que la clase base ya expone para
     * eso, en vez de agregar un @ExceptionHandler nuevo.
     */
    @Override
    @Nullable
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "El archivo supera el tamaño máximo permitido (10MB).");
        problema.setType(URI.create("https://asistente-academico.uteq.edu.ec/errores/tamano-excedido"));
        problema.setTitle("Archivo demasiado grande");
        problema.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                .body(problema);
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