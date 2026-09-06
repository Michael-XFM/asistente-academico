package com.uteq.asistente_academico.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Intercepta los metodos marcados @Auditado y delega en
 * AuditoriaTransactionRunner (bean aparte, no @Aspect -- ver el porque
 * en su Javadoc) para fijar app.usuario_actual y recien despues dejar
 * continuar el metodo real, todo dentro de la misma transaccion.
 */
@Aspect
@Component
public class AuditoriaAspect {

    @Autowired
    private AuditoriaTransactionRunner transactionRunner;

    @Around("@annotation(Auditado)")
    public Object auditar(ProceedingJoinPoint joinPoint) throws Throwable {
        return transactionRunner.ejecutarConUsuarioActual(resolverEmailAutenticado(), joinPoint);
    }

    /**
     * NULL si no hay sesion real (llamada directa sin HTTP, ej. tests) o
     * si es una AnonymousAuthenticationToken (request sin JWT valido a un
     * endpoint permitAll, como el registro publico) -- nunca el string
     * literal "anonymousUser" que devolveria Authentication.getName() en
     * ese caso.
     */
    private String resolverEmailAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean haySesionReal = auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
        return haySesionReal ? auth.getName() : null;
    }
}
