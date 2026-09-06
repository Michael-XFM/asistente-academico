-- V5__crear_tabla_entrega.sql
-- Agrega la tabla entrega (archivo que un estudiante sube para una tarea,
-- con su calificacion y comentario del profesor una vez revisada).
-- REFERENCES corregidas contra los nombres reales del esquema (V1__schema_inicial.sql):
-- la tabla de tareas es "tareas" (no "tarea") y la de usuarios es
-- "usuarios" (no "usuario"); las columnas id_tarea/id_usuario si coinciden
-- con lo pedido.
-- UNIQUE (id_tarea, id_estudiante): un estudiante entrega una sola vez por
-- tarea (volver a subir seria una actualizacion de esa misma fila, no una
-- fila nueva).

CREATE TABLE entrega (
    id_entrega          SERIAL PRIMARY KEY,
    id_tarea            INTEGER NOT NULL REFERENCES tareas(id_tarea),
    id_estudiante       INTEGER NOT NULL REFERENCES usuarios(id_usuario),
    nombre_archivo      VARCHAR(255) NOT NULL,
    ruta_archivo        VARCHAR(500) NOT NULL,
    formato             VARCHAR(10)  NOT NULL,
    tamano_bytes        BIGINT       NOT NULL,
    fecha_envio         TIMESTAMP    NOT NULL DEFAULT now(),
    calificacion        NUMERIC(4,2),
    comentario_prof     TEXT,
    fecha_calificacion  TIMESTAMP,
    UNIQUE (id_tarea, id_estudiante)
);
