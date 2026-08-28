-- db/demo-data.sql
-- Datos de demostración para la presentación. NO forma parte del arranque
-- automático vía docker-entrypoint-initdb.d (a diferencia de db/schema.sql
-- y db/seed.sql): se aplica a mano, una sola vez, contra un contenedor de
-- Postgres que ya está corriendo, sin resetear el volumen ni tocar el
-- usuario admin de db/seed.sql.
--
-- Contraseñas en texto plano de cada estudiante (para poder loguearse en
-- la demo; cada una es distinta, ninguna es la del admin):
--   mishell.chavez@uteq.edu.ec   / Estudiante01
--   jordy.zambrano@uteq.edu.ec   / Jordy2026
--   genesis.pincay@uteq.edu.ec   / Genesis123
--   kevin.moreira@uteq.edu.ec    / Kevin2026!
--   dayana.sabando@uteq.edu.ec   / Dayana456
--   bryan.intriago@uteq.edu.ec   / Bryan789
--
-- El hash se genera en el propio Postgres con pgcrypto (crypt +
-- gen_salt('bf', 10)), formato $2a$: BCryptPasswordEncoder.matches() de
-- Spring Security lee la versión del hash ya almacenado (no la fuerza a la
-- que usó para generar), así que verifica $2a$ igual que el $2b$ que ya
-- trae el admin de db/seed.sql. No se generó ningún hash fuera de la base.
--
-- Nota sobre alcance (ver conversación previa):
--  - No se cargan horarios: esa vista quedó bloqueada porque Horario no
--    tiene relación con Usuario en el modelo actual; cargar datos ahí no
--    se mostraría en ningún lado todavía.
--  - No hay tareas "completadas": el esquema de Tareas no tiene columna de
--    estado, tareas.html y dashboard.html solo distinguen PENDIENTE /
--    VENCIDA a partir de fecha_entrega. Se cubre esa variedad en su lugar
--    (pendientes próximas, pendientes con margen, vencidas).
--  - Avisos está ligado 1:1 a una tarea puntual (avisos.id_tarea es NOT
--    NULL): no existe una tabla de anuncios institucionales genéricos
--    separada. Los avisos de este script son recordatorios sobre tareas
--    concretas de cada estudiante, no un boletín general de la carrera.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
DECLARE
    v_mishell   INT;
    v_jordy     INT;
    v_genesis   INT;
    v_kevin     INT;
    v_dayana    INT;
    v_bryan     INT;

    v_mat_iso   INT; -- Ingeniería de Software I (nueva)
    v_mat_eda   INT; -- Estructuras de Datos y Algoritmos (nueva)
    v_mat_so    INT; -- Sistemas Operativos (nueva)
    v_mat_redes INT; -- Redes de Computadoras (nueva)
    v_mat_arq   INT; -- Arquitectura de Software (nueva)
    v_mat_web   INT; -- Aplicaciones Web (ya cargada por db/seed.sql)
    v_mat_bd    INT; -- Base de Datos (ya cargada por db/seed.sql)
    v_mat_calc  INT; -- Cálculo Integral (ya cargada por db/seed.sql)

    v_tarea1 INT; -- SRS de Mishell (referenciada por un aviso)
    v_tarea3 INT; -- Árbol AVL de Jordy (referenciada por un aviso)
    v_tarea7 INT; -- VLSM de Kevin (referenciada por un aviso)
    v_tarea9 INT; -- SQL/SP de Dayana (referenciada por un aviso)
BEGIN
    -- ---------- Estudiantes ----------
    INSERT INTO usuarios (nombre, email, contraseña, rol, fecha_registro)
    VALUES ('Mishell Anahí Chávez Loor', 'mishell.chavez@uteq.edu.ec', crypt('Estudiante01', gen_salt('bf', 10)), 'ESTUDIANTE', NOW())
    RETURNING id_usuario INTO v_mishell;

    INSERT INTO usuarios (nombre, email, contraseña, rol, fecha_registro)
    VALUES ('Jordy Alexander Zambrano Vera', 'jordy.zambrano@uteq.edu.ec', crypt('Jordy2026', gen_salt('bf', 10)), 'ESTUDIANTE', NOW())
    RETURNING id_usuario INTO v_jordy;

    INSERT INTO usuarios (nombre, email, contraseña, rol, fecha_registro)
    VALUES ('Génesis Nicole Pincay Alcívar', 'genesis.pincay@uteq.edu.ec', crypt('Genesis123', gen_salt('bf', 10)), 'ESTUDIANTE', NOW())
    RETURNING id_usuario INTO v_genesis;

    INSERT INTO usuarios (nombre, email, contraseña, rol, fecha_registro)
    VALUES ('Kevin Andrés Moreira Cedeño', 'kevin.moreira@uteq.edu.ec', crypt('Kevin2026!', gen_salt('bf', 10)), 'ESTUDIANTE', NOW())
    RETURNING id_usuario INTO v_kevin;

    INSERT INTO usuarios (nombre, email, contraseña, rol, fecha_registro)
    VALUES ('Dayana Michelle Sabando Zambrano', 'dayana.sabando@uteq.edu.ec', crypt('Dayana456', gen_salt('bf', 10)), 'ESTUDIANTE', NOW())
    RETURNING id_usuario INTO v_dayana;

    INSERT INTO usuarios (nombre, email, contraseña, rol, fecha_registro)
    VALUES ('Bryan Josué Intriago Palma', 'bryan.intriago@uteq.edu.ec', crypt('Bryan789', gen_salt('bf', 10)), 'ESTUDIANTE', NOW())
    RETURNING id_usuario INTO v_bryan;

    -- ---------- Materias nuevas ----------
    INSERT INTO materia (nombre) VALUES ('Ingeniería de Software I') RETURNING id_materia INTO v_mat_iso;
    INSERT INTO materia (nombre) VALUES ('Estructuras de Datos y Algoritmos') RETURNING id_materia INTO v_mat_eda;
    INSERT INTO materia (nombre) VALUES ('Sistemas Operativos') RETURNING id_materia INTO v_mat_so;
    INSERT INTO materia (nombre) VALUES ('Redes de Computadoras') RETURNING id_materia INTO v_mat_redes;
    INSERT INTO materia (nombre) VALUES ('Arquitectura de Software') RETURNING id_materia INTO v_mat_arq;

    -- Materias ya cargadas por db/seed.sql: se buscan por nombre, sin
    -- asumir que sus id son 1/2/3.
    SELECT id_materia INTO v_mat_web  FROM materia WHERE nombre = 'Aplicaciones Web';
    SELECT id_materia INTO v_mat_bd   FROM materia WHERE nombre = 'Base de Datos';
    SELECT id_materia INTO v_mat_calc FROM materia WHERE nombre = 'Cálculo Integral';

    -- ---------- Tareas (10, mezcla de pendientes y vencidas) ----------
    -- codigo se deja NULL a propósito: solo lo asigna sp_generar_codigo_tarea
    -- cuando TareaController crea una tarea real vía POST /api/tareas. El
    -- frontend ya sabe mostrar "TAR-{id}" como respaldo para tareas sin
    -- codigo (mismo caso que las anteriores a V2__add_codigo_tarea.sql).
    INSERT INTO tareas (id_materia, id_usuario, titulo, descripcion, fecha_entrega)
    VALUES (v_mat_iso, v_mishell, 'Documento de requerimientos SRS', 'Elaborar el documento de especificación de requerimientos de software del proyecto integrador.', CURRENT_DATE + 3)
    RETURNING id_tarea INTO v_tarea1;

    INSERT INTO tareas (id_materia, id_usuario, titulo, descripcion, fecha_entrega)
    VALUES (v_mat_bd, v_mishell, 'Modelo entidad-relación normalizado', 'Diseñar el MER hasta 3FN del sistema de biblioteca asignado en el taller.', CURRENT_DATE + 10);

    INSERT INTO tareas (id_materia, id_usuario, titulo, descripcion, fecha_entrega)
    VALUES (v_mat_eda, v_jordy, 'Implementación de árbol AVL', 'Implementar inserción, eliminación y balanceo de un árbol AVL en Java.', CURRENT_DATE - 2)
    RETURNING id_tarea INTO v_tarea3;

    INSERT INTO tareas (id_materia, id_usuario, titulo, descripcion, fecha_entrega)
    VALUES (v_mat_web, v_jordy, 'API REST con autenticación JWT', 'Construir un endpoint protegido con JWT para el proyecto final del curso.', CURRENT_DATE + 5);

    INSERT INTO tareas (id_materia, id_usuario, titulo, descripcion, fecha_entrega)
    VALUES (v_mat_so, v_genesis, 'Simulador de planificación de procesos', 'Simular los algoritmos Round Robin y SJF en un programa de consola.', CURRENT_DATE + 7);

    INSERT INTO tareas (id_materia, id_usuario, titulo, descripcion, fecha_entrega)
    VALUES (v_mat_iso, v_genesis, 'Backlog priorizado con historias de usuario', 'Redactar el backlog inicial del proyecto integrador con criterios de aceptación.', CURRENT_DATE - 6);

    INSERT INTO tareas (id_materia, id_usuario, titulo, descripcion, fecha_entrega)
    VALUES (v_mat_redes, v_kevin, 'Diseño de subredes con VLSM', 'Calcular el subneteo VLSM para la red asignada en el taller de laboratorio.', CURRENT_DATE + 1)
    RETURNING id_tarea INTO v_tarea7;

    INSERT INTO tareas (id_materia, id_usuario, titulo, descripcion, fecha_entrega)
    VALUES (v_mat_arq, v_kevin, 'Diagrama de componentes C4', 'Modelar el sistema asignado con el nivel 3 (componentes) del modelo C4.', CURRENT_DATE + 14);

    INSERT INTO tareas (id_materia, id_usuario, titulo, descripcion, fecha_entrega)
    VALUES (v_mat_bd, v_dayana, 'Consultas SQL con procedimientos almacenados', 'Escribir cinco procedimientos almacenados sobre la base de datos del taller.', CURRENT_DATE - 1)
    RETURNING id_tarea INTO v_tarea9;

    INSERT INTO tareas (id_materia, id_usuario, titulo, descripcion, fecha_entrega)
    VALUES (v_mat_eda, v_bryan, 'Análisis de complejidad Big-O', 'Determinar la complejidad temporal y espacial de cuatro algoritmos de ordenamiento.', CURRENT_DATE + 4);

    -- ---------- Calificaciones (2-3 materias por estudiante) ----------
    INSERT INTO calificaciones (id_usuario, id_materia, nota) VALUES
        (v_mishell, v_mat_iso,   8.50),
        (v_mishell, v_mat_bd,    7.25),
        (v_mishell, v_mat_web,   9.00),
        (v_jordy,   v_mat_eda,   6.75),
        (v_jordy,   v_mat_web,   8.80),
        (v_genesis, v_mat_so,    9.20),
        (v_genesis, v_mat_iso,   7.60),
        (v_genesis, v_mat_bd,    8.10),
        (v_kevin,   v_mat_redes, 5.90),
        (v_kevin,   v_mat_arq,   8.40),
        (v_dayana,  v_mat_bd,    6.30),
        (v_dayana,  v_mat_calc,  7.75),
        (v_dayana,  v_mat_web,   8.95),
        (v_bryan,   v_mat_eda,   7.10),
        (v_bryan,   v_mat_so,    8.65);

    -- ---------- Avisos (4: 2 leídos, 2 no leídos) ----------
    INSERT INTO avisos (id_tarea, id_usuario, mensaje, leido, fecha_generacion) VALUES
        (v_tarea3, v_jordy,   'Tu tarea "Implementación de árbol AVL" está vencida. Contacta a tu docente si necesitas una prórroga.', FALSE, NOW() - INTERVAL '2 days'),
        (v_tarea1, v_mishell, 'Recordatorio: tu tarea "Documento de requerimientos SRS" vence en 3 días.', TRUE, NOW() - INTERVAL '1 day'),
        (v_tarea9, v_dayana,  'Tu tarea "Consultas SQL con procedimientos almacenados" está vencida.', FALSE, NOW() - INTERVAL '1 day'),
        (v_tarea7, v_kevin,   'Recordatorio: tu tarea "Diseño de subredes con VLSM" vence mañana.', TRUE, NOW());
END $$;

COMMIT;
