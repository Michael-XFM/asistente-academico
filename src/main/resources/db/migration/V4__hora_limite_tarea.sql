-- V4__hora_limite_tarea.sql
-- Agrega la hora de cierre de una tarea, separada de fecha_entrega (DATE).
-- No se cambia el tipo de fecha_entrega a TIMESTAMP a proposito: ese tipo
-- esta usado como LocalDate en la entidad Tarea, en el CRUD propio del
-- estudiante (TareaController), en ProfesorController.CrearTareaRequest,
-- en fn_listar_tareas_pendientes (aritmetica con CURRENT_DATE) y en los
-- <input type="date"> de tareas.html/profesor.html -- cambiar el tipo
-- obligaria a tocar todo eso a la vez. Agregar una columna nueva con
-- DEFAULT es no invasivo: las tareas existentes quedan cerrando a fin de
-- dia automaticamente, sin backfill manual.
ALTER TABLE tareas
    ADD COLUMN hora_limite TIME NOT NULL DEFAULT '23:59:59';
