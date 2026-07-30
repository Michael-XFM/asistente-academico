package com.uteq.asistente_academico.repository;

import com.uteq.asistente_academico.entity.Tarea;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TareaRepository extends JpaRepository<Tarea, Integer> {
    List<Tarea> findByUsuario_IdUsuario(Integer idUsuario);
    Page<Tarea> findByUsuario_IdUsuario(Integer idUsuario, Pageable pageable);
}