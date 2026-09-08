-- V7__log_actividad.sql
-- Tabla dedicada a la carga masiva de prueba (Bloque ABD - 1M registros).
-- Solo la definicion de la tabla, vacia -- la carga masiva en si vive en
-- un script aparte (db/carga_masiva_log_actividad.sql), que NO corre en
-- el bootstrap (docker-entrypoint-initdb.d ni esta migracion): generar
-- un millon de filas en cada arranque desde cero rompe la reproducibilidad
-- rapida del Bloque B.
--
-- Sin trigger de fn_auditoria a proposito: log_actividad no es una de
-- las 4 tablas auditadas -- si tuviera trigger, cada fila de la carga
-- masiva generaria otra fila en auditoria, duplicando el volumen y
-- enterrando el rastro de auditoria real bajo ruido sintetico.
CREATE TABLE log_actividad (
    id          BIGSERIAL PRIMARY KEY,
    id_usuario  INTEGER REFERENCES usuarios(id_usuario),
    accion      VARCHAR(50) NOT NULL,
    detalle     TEXT,
    fecha_hora  TIMESTAMP NOT NULL,
    ip_origen   INET
);
