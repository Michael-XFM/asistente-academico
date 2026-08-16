package com.uteq.asistente_academico.repository;

import com.uteq.asistente_academico.entity.Calificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Map;

public interface CalificacionRepository extends JpaRepository<Calificacion, Integer> {
    List<Calificacion> findByUsuario_IdUsuario(Integer idUsuario);

    /**
     * sp_reporte_calificaciones (Bloque A.2 — reportes): invocado vía
     * @Procedure(name = "Entidad.consultaNombrada"), que referencia el
     * @NamedStoredProcedureQuery declarado en Calificacion.java (JPA 2.1
     * formal), nunca con createNativeQuery + concatenación.
     *
     * Con múltiples parámetros OUT, Spring Data devuelve un
     * Map<String,Object> con los nombres declarados en
     * @StoredProcedureParameter como claves.
     */
    @Procedure(name = "Calificacion.reporteCalificaciones")
    Map<String, Object> reporteCalificaciones(@Param("p_id_usuario") Integer idUsuario);
}
