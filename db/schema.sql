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
                        codigo        VARCHAR(20)
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