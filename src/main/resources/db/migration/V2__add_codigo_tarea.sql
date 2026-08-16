-- V2__add_codigo_tarea.sql
-- Agrega la columna codigo (folio de seguimiento legible, ver
-- db/procs/sp_generar_codigo_tarea.sql) a tareas. Nullable: las tareas
-- creadas antes de esta migracion no tienen codigo retroactivo, y no
-- hace falta backfill para el alcance de este bloque.

ALTER TABLE tareas ADD COLUMN codigo VARCHAR(20);
