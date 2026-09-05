package com.uteq.asistente_academico.repository;

import com.uteq.asistente_academico.entity.Materia;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MateriaRepository extends JpaRepository<Materia, Integer> {
    List<Materia> findByProfesor_IdUsuario(Integer idProfesor);
}
