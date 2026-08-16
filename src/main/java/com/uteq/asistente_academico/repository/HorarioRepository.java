package com.uteq.asistente_academico.repository;

import com.uteq.asistente_academico.entity.Horario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;

public interface HorarioRepository extends JpaRepository<Horario, Integer> {
    List<Horario> findByMateria_IdMateria(Integer idMateria);

    /**
     * sp_validar_disponibilidad_horario (Bloque A.2 — validaciones
     * cruzadas): invocado vía @Procedure (JPA 2.1), nunca con
     * createNativeQuery + concatenación. Es un PROCEDURE con un
     * parámetro OUT (no una FUNCTION): Hibernate 6.6.x siempre emite
     * "CALL nombre(parametro => ?, ...)" para @Procedure contra
     * PostgreSQL, sintaxis que el motor solo acepta contra PROCEDUREs.
     * outputParameterName indica cuál de los parámetros es el OUT que
     * Spring Data debe devolver como resultado del método.
     */
    @Procedure(procedureName = "sp_validar_disponibilidad_horario", outputParameterName = "p_disponible")
    Boolean spValidarDisponibilidadHorario(
            @Param("p_dia_semana") String pDiaSemana,
            @Param("p_hora_inicio") LocalTime pHoraInicio,
            @Param("p_hora_fin") LocalTime pHoraFin,
            @Param("p_aula") String pAula
    );
}
