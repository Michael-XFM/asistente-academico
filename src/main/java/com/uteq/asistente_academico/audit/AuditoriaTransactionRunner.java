package com.uteq.asistente_academico.audit;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unico punto donde de verdad se abre la transaccion que va a compartir
 * el set_config con el INSERT/UPDATE/DELETE real. Tiene que ser un bean
 * normal (no @Aspect): @Transactional en el metodo @Around de un
 * @Aspect no funciona, porque Spring invoca los metodos de advice por
 * reflexion directa sobre la instancia, sin pasar por el proxy dinamico
 * del bean -- y @Transactional solo intercepta llamadas que pasan por
 * ese proxy. Al ser este un bean aparte, AuditoriaAspect lo invoca como
 * una llamada normal entre beans, que si atraviesa el proxy.
 */
@Component
public class AuditoriaTransactionRunner {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public Object ejecutarConUsuarioActual(String email, ProceedingJoinPoint joinPoint) throws Throwable {
        entityManager.createNativeQuery("SELECT set_config('app.usuario_actual', :email, true)")
                .setParameter("email", email)
                .getSingleResult();
        return joinPoint.proceed();
    }
}
