package com.uteq.asistente_academico.repository;

import com.uteq.asistente_academico.entity.Auditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {

    /**
     * tabla/usuarioApp son opcionales -- el patron "(:param IS NULL OR
     * campo = :param)" evita tener que escribir un metodo por cada
     * combinacion de filtros. Orden fijo mas reciente primero.
     */
    @Query("SELECT a FROM Auditoria a " +
            "WHERE (:tabla IS NULL OR a.tabla = :tabla) " +
            "AND (:usuarioApp IS NULL OR a.usuarioApp = :usuarioApp) " +
            "ORDER BY a.fechaHora DESC")
    Page<Auditoria> buscar(@Param("tabla") String tabla, @Param("usuarioApp") String usuarioApp, Pageable pageable);
}
