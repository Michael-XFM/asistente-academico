-- sp_reporte_calificaciones.sql
-- Categoria funcional (Bloque A.2.1 - Entrega Final): reportes.
-- Propósito: Boletín de calificaciones de un usuario — promedio general,
--            cantidad de materias calificadas, y las materias donde
--            obtuvo su mejor y peor nota. A diferencia de
--            fn_obtener_resumen_academico (categoría "cálculos
--            agregados" — KPIs operativos del dashboard: tareas
--            pendientes, avisos, próxima entrega), este procedimiento es
--            un reporte de desempeño académico, con su propio
--            significado de negocio (boletín), no un panel operativo.
-- Parámetros de entrada: p_id_usuario (integer).
-- Parámetros de salida: p_promedio (numeric(4,2)), p_total_materias
--            (integer), p_mejor_materia (varchar), p_peor_materia
--            (varchar). Si el usuario no tiene calificaciones, los
--            cuatro quedan en NULL (mismo criterio que
--            fn_obtener_resumen_academico.promedio_general).
-- Tablas afectadas (solo lectura): calificaciones, materia.
-- Motivo de Stored Procedure (no ORM): combina tres consultas agregadas
--   distintas (promedio+conteo, mejor materia, peor materia) sobre un
--   JOIN calificaciones-materia en una sola proyección de "boletín" que
--   no corresponde a ninguna entidad JPA ni se resuelve con un solo
--   findBy... de Spring Data.
--
-- Implementado como PROCEDURE (CALL) con parámetros OUT, no como
-- FUNCTION: mismo motivo documentado en sp_validar_disponibilidad_horario.sql
-- (Hibernate 6.6.x invoca @Procedure/@NamedStoredProcedureQuery con
-- sintaxis CALL, que PostgreSQL solo acepta contra PROCEDUREs).

CREATE OR REPLACE PROCEDURE sp_reporte_calificaciones(
    IN  p_id_usuario     INTEGER,
    OUT p_promedio       NUMERIC(4,2),
    OUT p_total_materias INTEGER,
    OUT p_mejor_materia  VARCHAR,
    OUT p_peor_materia   VARCHAR
)
    LANGUAGE plpgsql
AS $$
BEGIN
    SELECT ROUND(AVG(c.nota), 2), COUNT(DISTINCT c.id_materia)
    INTO p_promedio, p_total_materias
    FROM calificaciones c
    WHERE c.id_usuario = p_id_usuario;

    SELECT m.nombre INTO p_mejor_materia
    FROM calificaciones c
             JOIN materia m ON m.id_materia = c.id_materia
    WHERE c.id_usuario = p_id_usuario
    ORDER BY c.nota DESC
    LIMIT 1;

    SELECT m.nombre INTO p_peor_materia
    FROM calificaciones c
             JOIN materia m ON m.id_materia = c.id_materia
    WHERE c.id_usuario = p_id_usuario
    ORDER BY c.nota ASC
    LIMIT 1;
END;
$$;
