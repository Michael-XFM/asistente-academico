-- db/seed.sql
-- Datos semilla reproducibles, aplicados via docker-entrypoint-initdb.d
-- justo despues de db/schema.sql. Contiene el usuario admin documentado
-- en el README (Bloque B.1).
--
-- Credenciales del usuario admin (documentadas en README.md):
--   email:      admin@uteq.edu.ec
--   contraseña: Admin123!
--
-- El hash fue generado con BCrypt (factor de costo 10), el mismo
-- algoritmo que usa UsuarioService.registrar() en la aplicacion.

INSERT INTO usuarios (nombre, email, contraseña, rol, fecha_registro)
VALUES (
           'Administrador',
           'admin@uteq.edu.ec',
           '$2b$10$FmzB3a6KgYjlrS5KfW6bp.gLaHQQam6VDdUMxIQVSpxyQ1wsHWI1q',
           'ADMIN',
           NOW()
       );

-- Materias base para poder probar el sistema de inmediato
INSERT INTO materia (nombre) VALUES
                                 ('Aplicaciones Web'),
                                 ('Base de Datos'),
                                 ('Cálculo Integral');

-- Respuestas predefinidas del chatbot híbrido (Respuestas_bot)
INSERT INTO respuestas_bot (palabra_clave, respuesta, activo) VALUES
                                                                  ('horario', 'Puedes consultar tu horario completo en la sección "Mis horarios" del dashboard.', TRUE),
                                                                  ('tareas', 'Tus tareas pendientes aparecen en la sección "Mis tareas" del dashboard.', TRUE),
                                                                  ('calificaciones', 'Tus calificaciones y promedio general están disponibles en "Calificaciones".', TRUE);

-- ============================================================
-- Rol PROFESOR y matricula: datos de prueba para demostrar el
-- concepto de principio a fin desde una clonacion limpia (sin esto,
-- horarios.html y los endpoints /api/profesor/* no tendrian nada que
-- mostrar). Mismo hash de contraseña que el admin de arriba en todos
-- los usuarios nuevos (contraseña: Admin123!).
-- ============================================================

INSERT INTO usuarios (nombre, email, contraseña, rol, fecha_registro) VALUES
    ('Carlos Salazar', 'carlos.salazar@uteq.edu.ec', '$2b$10$FmzB3a6KgYjlrS5KfW6bp.gLaHQQam6VDdUMxIQVSpxyQ1wsHWI1q', 'PROFESOR', NOW()),
    ('Diana Vélez',    'diana.velez@uteq.edu.ec',    '$2b$10$FmzB3a6KgYjlrS5KfW6bp.gLaHQQam6VDdUMxIQVSpxyQ1wsHWI1q', 'PROFESOR', NOW()),
    ('Andrés Moreira', 'andres.moreira@uteq.edu.ec', '$2b$10$FmzB3a6KgYjlrS5KfW6bp.gLaHQQam6VDdUMxIQVSpxyQ1wsHWI1q', 'PROFESOR', NOW());

-- Estudiantes de prueba (no habia ninguno mas alla del admin en una
-- clonacion limpia; se crean aca para poder matricularlos).
INSERT INTO usuarios (nombre, email, contraseña, rol, fecha_registro) VALUES
    ('María Zambrano', 'maria.zambrano@uteq.edu.ec', '$2b$10$FmzB3a6KgYjlrS5KfW6bp.gLaHQQam6VDdUMxIQVSpxyQ1wsHWI1q', 'ESTUDIANTE', NOW()),
    ('Luis Cedeño',    'luis.cedeno@uteq.edu.ec',    '$2b$10$FmzB3a6KgYjlrS5KfW6bp.gLaHQQam6VDdUMxIQVSpxyQ1wsHWI1q', 'ESTUDIANTE', NOW()),
    ('Ana Bravo',      'ana.bravo@uteq.edu.ec',      '$2b$10$FmzB3a6KgYjlrS5KfW6bp.gLaHQQam6VDdUMxIQVSpxyQ1wsHWI1q', 'ESTUDIANTE', NOW());

-- Materia nueva, exclusiva del tercer profesor (para que haya al menos
-- una materia sin superposicion con los otros dos).
INSERT INTO materia (nombre) VALUES ('Estructuras de Datos');

-- Asigna profesor a las 3 materias originales + la nueva: Carlos dicta
-- 2 (Aplicaciones Web, Base de Datos), Diana dicta 1 (Cálculo
-- Integral), Andrés dicta 1 (Estructuras de Datos).
UPDATE materia SET id_profesor = (SELECT id_usuario FROM usuarios WHERE email = 'carlos.salazar@uteq.edu.ec')
WHERE nombre IN ('Aplicaciones Web', 'Base de Datos');

UPDATE materia SET id_profesor = (SELECT id_usuario FROM usuarios WHERE email = 'diana.velez@uteq.edu.ec')
WHERE nombre = 'Cálculo Integral';

UPDATE materia SET id_profesor = (SELECT id_usuario FROM usuarios WHERE email = 'andres.moreira@uteq.edu.ec')
WHERE nombre = 'Estructuras de Datos';

-- Matricula los 3 estudiantes de prueba, cruzando profesores (cada
-- estudiante queda matriculado con mas de un profesor, y cada
-- profesor tiene mas de un estudiante, para poder probar el
-- aislamiento en ambos sentidos).
INSERT INTO matricula (id_usuario, id_materia, fecha_matricula)
SELECT u.id_usuario, m.id_materia, NOW()
FROM usuarios u
         JOIN materia m ON TRUE
WHERE (u.email, m.nombre) IN (
    ('maria.zambrano@uteq.edu.ec', 'Aplicaciones Web'),
    ('maria.zambrano@uteq.edu.ec', 'Base de Datos'),
    ('maria.zambrano@uteq.edu.ec', 'Cálculo Integral'),
    ('luis.cedeno@uteq.edu.ec', 'Base de Datos'),
    ('luis.cedeno@uteq.edu.ec', 'Estructuras de Datos'),
    ('ana.bravo@uteq.edu.ec', 'Cálculo Integral'),
    ('ana.bravo@uteq.edu.ec', 'Estructuras de Datos')
);

-- Horario minimo para que horarios.html tenga algo real que mostrar
-- (la tabla horario estaba vacia; sin esto, GET /api/horarios/mios
-- funcionaria pero devolveria siempre una lista vacia para todos).
INSERT INTO horario (id_materia, dia_semana, hora_inicio, hora_fin, aula)
SELECT m.id_materia, x.dia, x.inicio::TIME, x.fin::TIME, x.aula
FROM materia m
         JOIN (VALUES
                  ('Aplicaciones Web',     'Lunes',     '08:00', '10:00', 'Lab-1'),
                  ('Aplicaciones Web',     'Miércoles', '08:00', '10:00', 'Lab-1'),
                  ('Base de Datos',        'Martes',    '10:00', '12:00', 'Aula-204'),
                  ('Cálculo Integral',     'Jueves',    '14:00', '16:00', 'Aula-101'),
                  ('Estructuras de Datos', 'Viernes',   '08:00', '10:00', 'Lab-2')
              ) AS x(materia, dia, inicio, fin, aula) ON m.nombre = x.materia;