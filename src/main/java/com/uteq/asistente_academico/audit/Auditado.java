package com.uteq.asistente_academico.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un metodo que escribe (INSERT/UPDATE/DELETE) sobre una entidad
 * auditada: AuditoriaAspect intercepta la llamada, fija
 * app.usuario_actual para la transaccion, y recien despues la deja
 * continuar -- para que el trigger de auditoria en Postgres (a agregar en
 * un paso siguiente) sepa que usuario autenticado disparo el cambio.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auditado {
}
