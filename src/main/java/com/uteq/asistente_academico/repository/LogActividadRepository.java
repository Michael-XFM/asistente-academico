package com.uteq.asistente_academico.repository;

import com.uteq.asistente_academico.dto.LogActividadPorUsuario;
import com.uteq.asistente_academico.entity.LogActividad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LogActividadRepository extends JpaRepository<LogActividad, Long> {

    /**
     * LEFT JOIN (no INNER): log_actividad.id_usuario es nullable, asi que
     * puede haber filas sin usuario valido. Con INNER JOIN esas filas
     * desaparecerian del desglose sin explicacion y la suma de
     * "cantidad" quedaria por debajo de count(). COALESCE las agrupa
     * bajo un nombre fijo para que la suma del desglose siempre cierre
     * exacto con el total.
     */
    @Query("SELECT new com.uteq.asistente_academico.dto.LogActividadPorUsuario(" +
            "u.idUsuario, COALESCE(u.nombre, 'Sin usuario asignado'), COUNT(l)) " +
            "FROM LogActividad l LEFT JOIN Usuario u ON l.idUsuario = u.idUsuario " +
            "GROUP BY u.idUsuario, u.nombre " +
            "ORDER BY COUNT(l) DESC")
    List<LogActividadPorUsuario> contarPorUsuario();
}
