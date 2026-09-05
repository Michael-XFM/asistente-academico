package com.uteq.asistente_academico.repository;

import com.uteq.asistente_academico.entity.Matricula;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MatriculaRepository extends JpaRepository<Matricula, Integer> {
    List<Matricula> findByUsuario_IdUsuario(Integer idUsuario);
    List<Matricula> findByMateria_IdMateria(Integer idMateria);
    boolean existsByUsuario_IdUsuarioAndMateria_IdMateria(Integer idUsuario, Integer idMateria);
}
