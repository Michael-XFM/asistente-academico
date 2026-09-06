package com.uteq.asistente_academico.repository;

import com.uteq.asistente_academico.entity.Entrega;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EntregaRepository extends JpaRepository<Entrega, Integer> {
    List<Entrega> findByTarea_IdTarea(Integer idTarea);

    // Una sola entrega posible por (tarea, estudiante) -- ver UNIQUE
    // (id_tarea, id_estudiante) en V5__crear_tabla_entrega.sql. Sirve tanto
    // para el listado del profesor (estado por estudiante) como para
    // decidir en EntregaService si una subida nueva reemplaza una existente.
    Optional<Entrega> findByTarea_IdTareaAndEstudiante_IdUsuario(Integer idTarea, Integer idEstudiante);
}
