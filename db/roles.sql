-- Rol de aplicacion para el backend en tiempo de ejecucion: solo DML sobre
-- las tablas de negocio, sin DDL ni privilegios de administracion. El rol
-- "postgres" (superusuario) sigue siendo el owner del esquema y el unico
-- que corre migraciones (Flyway) o los scripts de db/.
--
-- Se crea SIN password: la contrasena real se fija aparte con
-- ALTER ROLE ... WITH PASSWORD, tomada de DB_APP_PASSWORD en .env (nunca
-- committeada) -- mismo criterio que ya se usa con GEMINI_API_KEY.
CREATE ROLE app_academico WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOREPLICATION;

GRANT USAGE ON SCHEMA public TO app_academico;

-- Privilegios sobre las tablas que ya existen al momento de correr esto
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_academico;

-- Todas las PK son SERIAL -> dependen de una secuencia implicita; INSERT
-- necesita poder llamar nextval() sobre ella.
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_academico;

-- Privilegios por defecto para objetos que se creen a futuro (nuevas
-- migraciones de Flyway agregando tablas), sin tener que volver a otorgar
-- permisos a mano cada vez. "FOR ROLE postgres" porque postgres es quien
-- crea los objetos nuevos, tanto en Docker (schema.sql) como en local
-- (Flyway, via spring.flyway.user).
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_academico;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO app_academico;
