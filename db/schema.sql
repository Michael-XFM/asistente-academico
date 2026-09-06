-- db/schema.sql
-- Esquema definitivo del Asistente Virtual Académico, aplicado UNICAMENTE
-- via docker-entrypoint-initdb.d al levantar el contenedor de PostgreSQL
-- desde una clonacion limpia (Bloque B - Reproducibilidad automatica).
-- Equivalente al resultado combinado de V1__schema_inicial.sql +
-- V2__add_codigo_tarea.sql + V3__matricula_y_profesor.sql en
-- src/main/resources/db/migration/ (que Flyway aplica en entornos de
-- desarrollo local sin Docker): este archivo representa el esquema
-- final ya migrado, no un historial de pasos incrementales.
-- Prohibido depender de spring.jpa.hibernate.ddl-auto=update: el esquema
-- se define unicamente aqui.

CREATE TABLE usuarios (
                          id_usuario      SERIAL PRIMARY KEY,
                          nombre          VARCHAR(100)  NOT NULL,
                          email           VARCHAR(255)  NOT NULL UNIQUE,
                          contraseña      VARCHAR(255)  NOT NULL,
                          rol             VARCHAR(20)   NOT NULL,
                          fecha_registro  TIMESTAMP
);

CREATE TABLE materia (
                         id_materia  SERIAL PRIMARY KEY,
                         nombre      VARCHAR(100) NOT NULL,
                         id_profesor INTEGER REFERENCES usuarios(id_usuario)
);

-- Matricula: relacion estudiante <-> materia. Nullable en materia.id_profesor
-- arriba porque no toda materia tiene profesor asignado; UNIQUE aqui
-- porque un estudiante no puede matricularse dos veces en la misma
-- materia.
CREATE TABLE matricula (
    id_matricula     SERIAL PRIMARY KEY,
    id_usuario       INTEGER NOT NULL REFERENCES usuarios(id_usuario),
    id_materia       INTEGER NOT NULL REFERENCES materia(id_materia),
    fecha_matricula  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (id_usuario, id_materia)
);

CREATE TABLE horario (
                         id_horario   SERIAL PRIMARY KEY,
                         id_materia   INTEGER NOT NULL REFERENCES materia(id_materia),
                         dia_semana   VARCHAR(20) NOT NULL,
                         hora_inicio  TIME NOT NULL,
                         hora_fin     TIME NOT NULL,
                         aula         VARCHAR(50)
);

CREATE TABLE tareas (
                        id_tarea      SERIAL PRIMARY KEY,
                        id_materia    INTEGER NOT NULL REFERENCES materia(id_materia),
                        id_usuario    INTEGER NOT NULL REFERENCES usuarios(id_usuario),
                        titulo        VARCHAR(200) NOT NULL,
                        descripcion   TEXT NOT NULL,
                        fecha_entrega DATE NOT NULL,
                        codigo        VARCHAR(20),
                        hora_limite   TIME NOT NULL DEFAULT '23:59:59'
);

-- Archivo que un estudiante sube para una tarea, con su calificacion y
-- comentario del profesor una vez revisada. UNIQUE (id_tarea,
-- id_estudiante): una sola entrega por estudiante por tarea (volver a
-- subir es una actualizacion de esa misma fila, no una fila nueva).
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

CREATE TABLE calificaciones (
                                id_calificacion  SERIAL PRIMARY KEY,
                                id_usuario       INTEGER NOT NULL REFERENCES usuarios(id_usuario),
                                id_materia       INTEGER NOT NULL REFERENCES materia(id_materia),
                                nota             NUMERIC(4,2) NOT NULL
);

CREATE TABLE avisos (
                        id_aviso          SERIAL PRIMARY KEY,
                        id_tarea          INTEGER NOT NULL REFERENCES tareas(id_tarea),
                        id_usuario        INTEGER NOT NULL REFERENCES usuarios(id_usuario),
                        mensaje           TEXT NOT NULL,
                        leido             BOOLEAN NOT NULL DEFAULT FALSE,
                        fecha_generacion  TIMESTAMP
);

CREATE TABLE mensajes (
                          id_mensaje   SERIAL PRIMARY KEY,
                          id_usuario   INTEGER NOT NULL REFERENCES usuarios(id_usuario),
                          contenido    TEXT NOT NULL,
                          tipo         VARCHAR(20) NOT NULL,
                          fecha_envio  TIMESTAMP
);

CREATE TABLE respuestas_bot (
                                id_respuesta   SERIAL PRIMARY KEY,
                                palabra_clave  VARCHAR(100) NOT NULL,
                                respuesta      TEXT NOT NULL,
                                activo         BOOLEAN DEFAULT TRUE
);

CREATE TABLE sesiones (
                          id_sesion     SERIAL PRIMARY KEY,
                          id_usuario    INTEGER REFERENCES usuarios(id_usuario),
                          token         VARCHAR(255) NOT NULL,
                          fecha_inicio  TIMESTAMP,
                          fecha_fin     TIMESTAMP
);

-- Auditoria: tabla, indices, funcion de trigger y triggers en si. El
-- REVOKE sobre app_academico NO va aca -- ese rol todavia no existe en
-- este punto del bootstrap de Docker (este archivo se monta como
-- 01-schema.sql, antes que 09-roles.sql). Ver db/auditoria.sql
-- (10-auditoria.sql), que corre despues y contiene solo ese REVOKE.
CREATE TABLE auditoria (
    id                BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    fecha_hora        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    usuario_bd        TEXT NOT NULL DEFAULT session_user,
    usuario_app       TEXT,
    operacion         TEXT NOT NULL CHECK (operacion IN ('INSERT','UPDATE','DELETE')),
    esquema           TEXT NOT NULL,
    tabla             TEXT NOT NULL,
    registro_id       TEXT,
    datos_anteriores  JSONB,
    datos_nuevos      JSONB,
    transaccion_id    BIGINT NOT NULL DEFAULT txid_current()
);

CREATE INDEX idx_auditoria_tabla_fecha ON auditoria (tabla, fecha_hora DESC);
CREATE INDEX idx_auditoria_usuario_fecha ON auditoria (usuario_app, fecha_hora DESC);

-- SET search_path fijo (no el de quien llama) para que un caller con
-- privilegios acotados (app_academico) no pueda hacerle "hijacking" al
-- search_path y colar un objeto propio que la funcion resuelva sin
-- querer, dado que corre como SECURITY DEFINER (con los privilegios del
-- owner, no los de app_academico).
CREATE OR REPLACE FUNCTION fn_auditoria()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    anterior   JSONB;
    nuevo      JSONB;
    columna_pk TEXT;
BEGIN
    -- Mecanismo para la futura carga masiva: si esta seteado, no audita
    -- nada (evita ~1 millon de filas de ruido en un solo evento).
    IF current_setting('app.omitir_auditoria', true) = 'true' THEN
        RETURN COALESCE(NEW, OLD);
    END IF;

    -- Nombre de la columna PK de la tabla, pasado como argumento del
    -- trigger (TG_ARGV[0]) porque no es uniforme entre tablas
    -- (id_usuario, id_tarea, id_entrega, id_calificacion).
    columna_pk := TG_ARGV[0];

    IF TG_OP = 'DELETE' THEN
        anterior := to_jsonb(OLD);
        nuevo    := NULL;
    ELSIF TG_OP = 'INSERT' THEN
        anterior := NULL;
        nuevo    := to_jsonb(NEW);
    ELSE
        anterior := to_jsonb(OLD);
        nuevo    := to_jsonb(NEW);
    END IF;

    -- Nunca guardar el hash de contraseña en el volcado JSONB de usuarios.
    IF TG_TABLE_NAME = 'usuarios' THEN
        anterior := anterior - 'contraseña';
        nuevo    := nuevo - 'contraseña';
    END IF;

    INSERT INTO auditoria (
        usuario_app, operacion, esquema, tabla, registro_id,
        datos_anteriores, datos_nuevos
    ) VALUES (
        NULLIF(current_setting('app.usuario_actual', true), ''),
        TG_OP,
        TG_TABLE_SCHEMA,
        TG_TABLE_NAME,
        COALESCE(nuevo ->> columna_pk, anterior ->> columna_pk),
        anterior,
        nuevo
    );

    RETURN COALESCE(NEW, OLD);
END;
$$;

-- Un trigger por tabla, generado dinamicamente porque el nombre de la PK
-- cambia entre tablas (no hay una columna "id" generica como en
-- TrailerSys) -- se resuelve con un CASE dentro del loop y se pasa como
-- argumento del trigger.
DO $$
DECLARE
    tabla      TEXT;
    columna_pk TEXT;
BEGIN
    FOREACH tabla IN ARRAY ARRAY['usuarios', 'tareas', 'entrega', 'calificaciones']
    LOOP
        columna_pk := CASE tabla
            WHEN 'usuarios'       THEN 'id_usuario'
            WHEN 'tareas'         THEN 'id_tarea'
            WHEN 'entrega'        THEN 'id_entrega'
            WHEN 'calificaciones' THEN 'id_calificacion'
        END;

        EXECUTE format('DROP TRIGGER IF EXISTS trg_auditoria ON %I;', tabla);
        EXECUTE format(
            'CREATE TRIGGER trg_auditoria
             AFTER INSERT OR UPDATE OR DELETE ON %I
             FOR EACH ROW EXECUTE FUNCTION fn_auditoria(%L);',
            tabla, columna_pk
        );
    END LOOP;
END $$;