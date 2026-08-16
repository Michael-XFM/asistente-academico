# Bitácora de Observaciones — Entregas 1A y 1B

Este documento registra, con trazabilidad a commits, la resolución de cada observación
formulada por el docente (Ing. Guerrero Ulloa) en la retroalimentación de las Entregas 1A y 1B
del PFC "Asistente Virtual Académico con Chatbot Híbrido".

**Resumen:** 10/10 observaciones cerradas (100%) de las Entregas 1A y 1B.

## Nota sobre observaciones de la Entrega 3

Al momento de esta entrega, el equipo no cuenta con un informe de
retroalimentación formal del docente sobre la Entrega 3 (v0.9.0-rc). Por
esa razón, esta bitácora no incluye observaciones con código OBS-11 en
adelante atribuidas a esa fuente — hacerlo sin retroalimentación real
implicaría inventar observaciones que nunca se recibieron. Si el docente
entrega retroalimentación de la Entrega 3 después de esta entrega, se
incorporará aquí con su numeración correspondiente y trazabilidad a
commit, actualizando este documento.

## Hallazgos autoidentificados durante el desarrollo de la Entrega Final

A diferencia de las observaciones anteriores (originadas en
retroalimentación del docente), los siguientes problemas fueron
detectados y corregidos por el propio equipo durante el trabajo de la
Entrega Final, sin que mediara una observación externa. Se documentan
aquí por transparencia y trazabilidad, con una nomenclatura distinta
(AUTO-XX) para no confundirlos con observaciones docentes:

| Código | Detectado durante | Descripción | Resolución | Commit |
|--------|---|---|---|---|
| AUTO-01 | Implementación de roles y permisos | `JwtAuthFilter` extraía el claim `rol` del JWT pero nunca lo convertía en `GrantedAuthority` de Spring Security; ningún `hasRole(...)` podía funcionar realmente. | Se mapea el claim a `ROLE_<rol>` y se habilita `@EnableMethodSecurity`. | `b66dc99` |
| AUTO-02 | Implementación de roles y permisos | `POST /api/auth/registro` aceptaba el campo `rol` tal cual lo enviara el cliente, permitiendo auto-registro como `ADMIN` (escalada de privilegios). | Se fuerza el rol a `ESTUDIANTE` en el registro público, independientemente del valor enviado. | `b66dc99` |
| AUTO-03 | Verificación del bloque de roles | El `@JsonIgnore` agregado en la Entrega 3 para ocultar el hash de contraseña también bloqueaba la deserialización, rompiendo silenciosamente `POST /api/auth/registro` desde entonces. | Reemplazado por `@JsonProperty(access = WRITE_ONLY)`, que bloquea la salida sin bloquear la entrada. | `63326d5` |

| Código | Fuente | Criterio | Observación | Decisión | Commit |
|--------|------------|--------------------------|-------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|---------|
| OBS-01 | Entrega 1A | C3 — Arquitectura (C4 N1) | Diagrama C4 Nivel 1 presente pero sin justificación de tecnologías (Gemini API, PostgreSQL) exigida por la directriz. | Aplicada. Se documenta la justificación de la pila tecnológica en `docs/arquitectura/justificacion-tecnologias.md`, reutilizada luego como base del ADR de elección de pila (Bloque D). | `32ab2c59` |
| OBS-02 | Entrega 1A | C4 — Modelo de base de datos (crítico) | El DDL entregado no correspondía al sistema: era el esquema de una clínica médica (`saludnovadb`) en sintaxis MySQL, incompatible con la pila declarada (PostgreSQL). | Aplicada. `database/schema.sql` fue reemplazado por un dump PostgreSQL real y correcto, con las tablas del dominio académico (Avisos, Calificaciones, Usuario, Horario, Materias, Tarea, etc.). | `80cf339` |
| OBS-03 | Entrega 1A | C4/C5 — Wireframes | Fragmentos SQL ajenos (JOIN paciente/médico/diagnóstico) residuales en la sección de wireframes del informe. | Aplicada. Los wireframes estáticos de la Entrega 1A quedan superados por las pantallas HTML reales ya implementadas (login, registro, dashboard, chatbot), sin ningún artefacto ajeno al proyecto. | `f061b86` |
| OBS-04 | Entrega 1A | C7 — Repositorio | No se proporcionó URL de repositorio en el informe entregado. | Aplicada. El repositorio es público desde el inicio del proyecto y su URL fue verificada explícitamente en la revisión de la Entrega 1B. | `538541b` |
| OBS-05 | Entrega 1B | C2 — Autenticación JWT | El JWT se emite pero no se registra un filtro de validación (`JwtAuthFilter`) en la cadena de seguridad; los tokens no se validaban en las peticiones protegidas. | Aplicada. Se registró `JwtAuthFilter` (`OncePerRequestFilter`) en `SecurityConfig`, validando el Bearer token y poblando el `SecurityContext` en cada request. | `f0148df` |
| OBS-06 | Entrega 1B | C3 — CRUD / Spring Data JPA | No había revocación de tokens en Redis (logout/blacklist) ni endpoint de refresh. | Aplicada. Se implementó blacklist de JTI en Redis para invalidar tokens en logout. | `932d01f` |
| OBS-07 | Entrega 1B | C3 — CRUD / Spring Data JPA | No existía controlador CRUD de una entidad de dominio expuesto vía API. | Aplicada. Se expuso el CRUD completo de la entidad `Tarea` (controlador + servicio + repositorio) con paginación. | `1eb0bbc` |
| OBS-08 | Entrega 1B | C3 — CRUD / Spring Data JPA | No se usaba Flyway; el esquema se aplicaba desde `schema.sql` sin migración versionada. | Aplicada. Se incorporó Flyway con migraciones versionadas (`V1__...sql`) para el control del esquema. | `28f4212` |
| OBS-09 | Entrega 1B | C4 — Seguridad OWASP | CORS configurado con comodín (`@CrossOrigin origins="*"`), no recomendado. | Aplicada. Se restringió CORS a orígenes explícitos declarados en configuración. | `161f743` |
| OBS-10 | Entrega 1B | C7 — Informe técnico | El PDF entregado al SGA contenía únicamente la URL del repositorio, sin informe técnico estructurado (secciones, diagramas, tablas, evidencias). | Aplicada. El informe técnico estructurado se entregó como `docs/informe-entrega-3.pdf` en la Entrega 3 (borrador inicial en `bed0100`, versión final de 21 páginas en `c6a4c8d`), conforme a la estructura exigida (portada, resumen ejecutivo, arquitectura, trazabilidad, resultados por bloque, amenazas a la validez, CRediT, ética, referencias). | `bed0100`, `c6a4c8d` |

## Convención de mensajes de commit

Cada commit que resuelve una observación referencia su código, por ejemplo:

```
fix(auth): restringe CORS a origenes explicitos (OBS-09)
docs(arquitectura): agrega justificacion de tecnologias del C4 N1 (OBS-01)
```

## Etiquetado

Sobre el commit que cierra la aplicación de observaciones (una vez completado OBS-01) se
coloca la etiqueta `v0.7.1`, dejando `v0.7.0` intacta como fotografía de la Entrega 1B.