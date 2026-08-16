-- sp_validar_disponibilidad_horario.sql
-- Categoria funcional (Bloque A.2.1 - Entrega Final): validaciones cruzadas.
-- Propósito: Antes de crear un nuevo horario de clases, valida que el
--            aula propuesta esté libre en ese día y franja horaria,
--            cruzando la fila propuesta contra TODAS las filas existentes
--            de horario (no una validación de una sola fila contra su
--            propia clave primaria, que sí sería CRUD elemental).
-- Parámetros de entrada: p_dia_semana (varchar), p_hora_inicio (time),
--            p_hora_fin (time), p_aula (varchar).
-- Parámetro de salida: p_disponible (boolean) — TRUE si el aula está
--            disponible (sin cruce), FALSE si ya existe un horario que
--            se superpone.
-- Tablas afectadas (solo lectura): horario.
-- Motivo de Stored Procedure/función (no ORM): es una validación cruzada
--   contra el conjunto completo de horarios existentes (comparación de
--   rangos de tiempo entre filas), no una consulta de una sola entidad
--   por su clave primaria — no se puede expresar como un findById de
--   Spring Data.
--
-- Implementado como PROCEDURE (CALL), no como FUNCTION: Hibernate 6.6.x
-- invoca @Procedure de Spring Data siempre con la sintaxis nativa
-- "CALL nombre(parametro => ?, ...)" de PostgreSQL, que el propio motor
-- de PostgreSQL únicamente acepta contra PROCEDURES (PostgreSQL 11+),
-- nunca contra FUNCTIONS. Se verificó en vivo: la misma logica como
-- FUNCTION fallaba con "ERROR: syntax error at or near '=>'" al ser
-- invocada desde @Procedure.

CREATE OR REPLACE PROCEDURE sp_validar_disponibilidad_horario(
    IN  p_dia_semana  VARCHAR,
    IN  p_hora_inicio TIME,
    IN  p_hora_fin    TIME,
    IN  p_aula        VARCHAR,
    OUT p_disponible  BOOLEAN
)
    LANGUAGE plpgsql
AS $$
DECLARE
    v_conflictos INTEGER;
BEGIN
    -- Dos franjas se superponen si una empieza antes de que la otra
    -- termine, en ambos sentidos (regla estandar de solapamiento de
    -- intervalos cerrados-abiertos).
    SELECT COUNT(*) INTO v_conflictos
    FROM horario h
    WHERE h.dia_semana = p_dia_semana
      AND h.aula = p_aula
      AND h.hora_inicio < p_hora_fin
      AND h.hora_fin > p_hora_inicio;

    p_disponible := (v_conflictos = 0);
END;
$$;
