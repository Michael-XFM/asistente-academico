package com.uteq.asistente_academico.repository;

import com.uteq.asistente_academico.entity.Tarea;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.query.Procedure;
import java.util.List;

public interface TareaRepository extends JpaRepository<Tarea, Integer> {
    List<Tarea> findByUsuario_IdUsuario(Integer idUsuario);
    Page<Tarea> findByUsuario_IdUsuario(Integer idUsuario, Pageable pageable);
    List<Tarea> findByMateria_IdMateria(Integer idMateria);

    /**
     * sp_generar_codigo_tarea (Bloque A.2 — generación de códigos
     * secuenciales): invocado vía @Procedure (JPA 2.1). Sin parámetros de
     * entrada; el único parámetro OUT (p_codigo) es lo que Spring Data
     * devuelve como resultado del método.
     */
    @Procedure(procedureName = "sp_generar_codigo_tarea", outputParameterName = "p_codigo")
    String spGenerarCodigoTarea();
}