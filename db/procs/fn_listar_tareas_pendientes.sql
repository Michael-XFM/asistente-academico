-- fn_listar_tareas_pendientes.sql
-- Propósito: Lista las tareas pendientes de un usuario con el nombre de su
--            materia, los días restantes hasta fecha_entrega y los
--            segundos restantes hasta fecha_entrega + hora_limite.
-- Parámetros de entrada: p_id_usuario (integer) — id del usuario autenticado.
-- Retorna: conjunto de filas (id_tarea, titulo, materia, fecha_entrega,
--          hora_limite, dias_restantes, segundos_restantes).
-- Tablas afectadas (solo lectura): Tareas, Materia.
-- Motivo de Stored Procedure/función (no ORM): requiere JOIN entre Tareas y
--   Materia, más columnas calculadas (dias_restantes, segundos_restantes)
--   que no existen en ninguna entidad JPA — es una proyección (DTO), no
--   una entidad completa.
--
-- dias_restantes se mantiene igual que antes (granularidad de día, con
-- CURRENT_DATE): el filtro WHERE y el ORDER BY siguen basados en el día,
-- sin cambios de comportamiento. segundos_restantes es nuevo: combina
-- fecha_entrega + hora_limite contra NOW() para la cuenta regresiva
-- precisa que pide el frontend (puede dar negativo si la hora límite de
-- HOY ya pasó, aunque el día siga contando como "pendiente").
--
-- Se hace DROP antes del CREATE OR REPLACE porque Postgres no permite
-- cambiar la lista de columnas de salida (RETURNS TABLE) de una función
-- existente con CREATE OR REPLACE — solo permite reemplazar el cuerpo.

DROP FUNCTION IF EXISTS fn_listar_tareas_pendientes(INTEGER);

CREATE FUNCTION fn_listar_tareas_pendientes(p_id_usuario INTEGER)
    RETURNS TABLE (
                      id_tarea           INTEGER,
                      titulo             VARCHAR,
                      materia            VARCHAR,
                      fecha_entrega      DATE,
                      hora_limite        TIME,
                      dias_restantes     INTEGER,
                      segundos_restantes INTEGER
                  )
    LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
        SELECT
            t.id_tarea,
            t.titulo,
            m.nombre AS materia,
            t.fecha_entrega,
            t.hora_limite,
            (t.fecha_entrega - CURRENT_DATE)::INTEGER AS dias_restantes,
            EXTRACT(EPOCH FROM ((t.fecha_entrega + t.hora_limite) - NOW()))::INTEGER AS segundos_restantes
        FROM tareas t
                 JOIN materia m ON m.id_materia = t.id_materia
        WHERE t.id_usuario = p_id_usuario
          AND t.fecha_entrega >= CURRENT_DATE
        ORDER BY t.fecha_entrega ASC;
END;
$$;
