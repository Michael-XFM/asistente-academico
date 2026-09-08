-- db/carga_masiva_log_actividad.sql
-- Carga masiva de prueba para log_actividad (Bloque ABD - 1M registros).
-- NO forma parte del bootstrap (docker-entrypoint-initdb.d ni Flyway) --
-- se corre manualmente, a proposito, cuando se quiera demostrar/medir la
-- carga masiva. Ver V7__log_actividad.sql / db/schema.sql para la
-- definicion de la tabla en si.
--
-- Idempotente via TRUNCATE: correr este script una vez, dos veces, o
-- despues de una corrida cortada a la mitad, siempre deja exactamente
-- 1.000.000 de filas limpias -- a diferencia de un chequeo de conteo
-- ("si hay menos de 1M, insertar"), que sumaria de mas si una corrida
-- anterior quedo a medias. Asume que log_actividad no la escribe
-- ninguna otra funcionalidad real (cierto hoy).
TRUNCATE TABLE log_actividad;

-- MATERIALIZED fuerza que el array de usuarios y el de acciones se
-- armen UNA sola vez (no en cada una de los 1M filas): sin esto, Postgres
-- 12+ puede "inlinear" el CTE y re-evaluarlo por fila. Elegir el usuario
-- por indice de array (u.ids[posicion aleatoria]) es O(1) por fila, en
-- vez de una subconsulta correlacionada (ORDER BY random() LIMIT 1) que
-- reordenaria la tabla usuarios completa 1.000.000 de veces.
WITH usuarios_ids AS MATERIALIZED (
    SELECT array_agg(id_usuario) AS ids FROM usuarios
),
acciones_posibles AS MATERIALIZED (
    SELECT ARRAY['LOGIN','LOGOUT','VER_TAREA','SUBIR_ENTREGA','DESCARGA_ENTREGA','VER_CALIFICACION','VER_AVISO'] AS acciones
)
INSERT INTO log_actividad (id_usuario, accion, detalle, fecha_hora, ip_origen)
SELECT
    u.ids[1 + floor(random() * array_length(u.ids, 1))::int],
    a.acciones[1 + floor(random() * array_length(a.acciones, 1))::int],
    'Carga masiva de prueba (ABD)',
    NOW() - (random() * INTERVAL '180 days'),
    ('10.' || floor(random()*255)::int || '.' || floor(random()*255)::int || '.' || floor(random()*255)::int)::inet
FROM generate_series(1, 1000000) AS s(n)
CROSS JOIN usuarios_ids u
CROSS JOIN acciones_posibles a;
