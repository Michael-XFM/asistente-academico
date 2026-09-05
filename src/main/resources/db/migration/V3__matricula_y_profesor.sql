-- V3__matricula_y_profesor.sql
-- Introduce el concepto de matricula (estudiante <-> materia), necesario
-- para el rol PROFESOR (ver solo sus propios estudiantes/materias) y
-- para GET /api/horarios/mios (un estudiante solo tiene horario en las
-- materias donde esta matriculado).

CREATE TABLE matricula (
    id_matricula     SERIAL PRIMARY KEY,
    id_usuario       INTEGER NOT NULL REFERENCES usuarios(id_usuario),
    id_materia       INTEGER NOT NULL REFERENCES materia(id_materia),
    fecha_matricula  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (id_usuario, id_materia)
);

-- Nullable: las materias ya sembradas no tienen profesor asignado
-- todavia, y forzar NOT NULL rompería la migracion contra esos datos
-- existentes (o forzaria un backfill con un profesor arbitrario, peor
-- que dejarlo sin asignar). Los datos de prueba lo completan.
ALTER TABLE materia ADD COLUMN id_profesor INTEGER REFERENCES usuarios(id_usuario);
