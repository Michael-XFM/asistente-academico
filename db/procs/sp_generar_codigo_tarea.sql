-- sp_generar_codigo_tarea.sql
-- Categoria funcional (Bloque A.2.1 - Entrega Final): generación de
--            códigos secuenciales.
-- Propósito: Genera un código de seguimiento legible para una tarea
--            recién creada (ej. "TAR-2026-000042"), distinto de la clave
--            primaria SERIAL interna (id_tarea), para que el estudiante
--            tenga un folio human-friendly que citar al pedir ayuda sobre
--            una entrega puntual.
-- Parámetro de salida: p_codigo (varchar) — formato TAR-{año}-{secuencia
--            de 6 dígitos con ceros a la izquierda}.
-- Tablas afectadas: ninguna tabla de negocio directamente; usa la
--            secuencia dedicada seq_codigo_tarea (creada por este mismo
--            archivo) para garantizar unicidad sin condición de carrera.
-- Motivo de Stored Procedure (no ORM): usar una SEQUENCE nativa de
--   PostgreSQL vía nextval() es atómico bajo concurrencia por diseño del
--   motor; un equivalente en Java (SELECT COUNT(*)+1) tendría una
--   condición de carrera real entre el SELECT y el INSERT si dos
--   estudiantes crean una tarea al mismo tiempo.
--
-- Implementado como PROCEDURE (CALL) con parámetro OUT, no como FUNCTION,
-- por el mismo motivo documentado en sp_validar_disponibilidad_horario.sql:
-- Hibernate 6.6.x invoca @Procedure siempre con "CALL nombre(param => ?,
-- ...)", sintaxis que PostgreSQL solo acepta contra PROCEDUREs.

CREATE SEQUENCE IF NOT EXISTS seq_codigo_tarea START 1;

CREATE OR REPLACE PROCEDURE sp_generar_codigo_tarea(
    OUT p_codigo VARCHAR
)
    LANGUAGE plpgsql
AS $$
BEGIN
    p_codigo := 'TAR-' || EXTRACT(YEAR FROM CURRENT_DATE)::TEXT
        || '-' || LPAD(nextval('seq_codigo_tarea')::TEXT, 6, '0');
END;
$$;
