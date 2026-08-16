# Catálogo de Procedimientos Almacenados y Funciones SQL

Este catálogo documenta cada función/procedimiento SQL usado por el sistema,
conforme a la estrategia de acceso a datos del Bloque A.2: los CRUD
elementales se resuelven vía ORM (Spring Data JPA), y toda operación que
implique JOIN, agregación, actualización masiva, validación cruzada,
generación de códigos o proyecciones que no correspondan a una entidad se
encapsula aquí.

El umbral mínimo de la Entrega Final (A.2.1) exige seis procedimientos o
funciones, uno por cada categoría funcional. Las seis categorías están
cubiertas:

| Categoría funcional | Cubierta por |
|---|---|
| Consultas multi-tabla | `fn_listar_tareas_pendientes` |
| Cálculos agregados | `fn_obtener_resumen_academico` |
| Actualizaciones masivas | `fn_marcar_avisos_leidos` |
| Reportes | `sp_reporte_calificaciones` |
| Validaciones cruzadas | `sp_validar_disponibilidad_horario` |
| Generación de códigos secuenciales | `sp_generar_codigo_tarea` |

`fn_obtener_resumen_academico` combina `COUNT`/`AVG`/`MIN` sobre tres
tablas — es, por técnica, un cálculo agregado, no un "reporte" en el
sentido de `sp_reporte_calificaciones` (un boletín de desempeño académico
con su propio significado de negocio, aunque también use agregaciones).

---

## fn_obtener_resumen_academico

- **Archivo:** `db/procs/fn_obtener_resumen_academico.sql`
- **Categoría funcional:** Cálculos agregados
- **Tipo:** Función (retorna tabla de 1 fila)
- **Propósito:** Calcula el resumen agregado del dashboard de un usuario:
  total de tareas pendientes, promedio general de calificaciones, total de
  avisos no leídos y fecha de la próxima entrega.
- **Parámetros de entrada:** `p_id_usuario INTEGER`
- **Parámetros de salida (columnas):** `tareas_pendientes INTEGER`,
  `promedio_general NUMERIC(4,2)`, `avisos_no_leidos INTEGER`,
  `proxima_entrega DATE`
- **Cursores devueltos:** Ninguno (retorna directamente una fila).
- **Tablas afectadas:** `tareas`, `calificaciones`, `avisos` (solo lectura).
- **Motivo de SP (no ORM):** combina agregaciones (`COUNT`, `AVG`, `MIN`)
  sobre tres tablas distintas en una sola proyección que no corresponde a
  ninguna entidad JPA.
- **Invocado desde:** `DashboardRepository.obtenerResumenAcademico()` vía
  `@Query(nativeQuery = true)` con parámetro nombrado `:idUsuario`.
- **Endpoint que lo usa:** `GET /api/dashboard`

---

## fn_listar_tareas_pendientes

- **Archivo:** `db/procs/fn_listar_tareas_pendientes.sql`
- **Categoría funcional:** Consultas multi-tabla
- **Tipo:** Función (retorna conjunto de filas)
- **Propósito:** Lista las tareas pendientes de un usuario con el nombre
  de su materia y los días restantes hasta la fecha de entrega.
- **Parámetros de entrada:** `p_id_usuario INTEGER`
- **Parámetros de salida (columnas):** `id_tarea INTEGER`,
  `titulo VARCHAR`, `materia VARCHAR`, `fecha_entrega DATE`,
  `dias_restantes INTEGER`
- **Cursores devueltos:** Ninguno (retorna un conjunto de filas directo).
- **Tablas afectadas:** `tareas`, `materia` (solo lectura, vía JOIN).
- **Motivo de SP (no ORM):** requiere JOIN entre `tareas` y `materia`, más
  una columna calculada (`dias_restantes`) que no existe en ninguna
  entidad JPA — es una proyección (DTO), no una entidad completa.
- **Invocado desde:** `DashboardRepository.listarTareasPendientes()` vía
  `@Query(nativeQuery = true)` con parámetro nombrado `:idUsuario`.
- **Endpoint que lo usa:** `GET /api/dashboard`

---

## fn_marcar_avisos_leidos

- **Archivo:** `db/procs/fn_marcar_avisos_leidos.sql`
- **Categoría funcional:** Actualizaciones masivas
- **Tipo:** Función (retorna escalar, con efecto de escritura)
- **Propósito:** Marca como leídos todos los avisos pendientes de un
  usuario en una sola operación, y devuelve cuántos fueron actualizados.
- **Parámetros de entrada:** `p_id_usuario INTEGER`
- **Parámetros de salida:** `INTEGER` (cantidad de filas actualizadas,
  obtenido con `GET DIAGNOSTICS ... ROW_COUNT`).
- **Cursores devueltos:** Ninguno.
- **Tablas afectadas:** `avisos` (escritura — `UPDATE`).
- **Motivo de SP (no ORM):** es una actualización masiva sobre múltiples
  filas seleccionadas por un criterio distinto de la clave primaria, por
  lo que no califica como CRUD elemental (A.2.1) y debe encapsularse como
  función (A.2.2).
- **Invocado desde:** `DashboardRepository.marcarAvisosLeidos()` vía
  `@Query(nativeQuery = true)` con parámetro nombrado `:idUsuario`. No usa
  `@Modifying` porque PostgreSQL la ejecuta como una función que retorna
  un valor (no como una sentencia `UPDATE` directa desde JDBC).
- **Endpoint que lo usa:** `PUT /api/dashboard/avisos/marcar-leidos`

---

## sp_validar_disponibilidad_horario

- **Archivo:** `db/procs/sp_validar_disponibilidad_horario.sql`
- **Categoría funcional:** Validaciones cruzadas
- **Tipo:** Procedimiento (`CALL`, un parámetro `OUT`)
- **Propósito:** Antes de crear un horario de clases, valida que el aula
  propuesta esté libre en ese día y franja horaria, cruzando la fila
  propuesta contra todas las filas existentes de `horario`.
- **Parámetros de entrada:** `p_dia_semana VARCHAR`, `p_hora_inicio TIME`,
  `p_hora_fin TIME`, `p_aula VARCHAR`
- **Parámetros de salida:** `p_disponible BOOLEAN` (`OUT`) — `TRUE` si no
  hay cruce de horario, `FALSE` si ya existe un horario superpuesto.
- **Cursores devueltos:** Ninguno.
- **Tablas afectadas:** `horario` (solo lectura).
- **Motivo de SP (no ORM):** es una validación cruzada contra el conjunto
  completo de horarios existentes (comparación de rangos de tiempo entre
  filas), no una consulta de una entidad por su clave primaria.
- **Invocado desde:** `HorarioRepository.spValidarDisponibilidadHorario()`
  vía `@Procedure(procedureName = ..., outputParameterName = "p_disponible")`
  (JPA 2.1), nunca con `createNativeQuery` + concatenación.
- **Endpoint que lo usa:** `POST /api/horarios/validar-disponibilidad`
- **Nota de implementación:** se diseñó primero como `FUNCTION`, invocada
  igual con `@Procedure`, y falló en vivo con
  `ERROR: syntax error at or near "=>"`. Hibernate 6.6.x siempre emite
  `CALL nombre(parametro => ?, ...)` para `@Procedure` contra PostgreSQL,
  sintaxis que el motor solo acepta contra `PROCEDURE`s (PostgreSQL 11+),
  nunca contra `FUNCTION`s. Los tres procedimientos nuevos de esta entrega
  se implementaron como `PROCEDURE` con parámetros `OUT` por este motivo.

---

## sp_generar_codigo_tarea

- **Archivo:** `db/procs/sp_generar_codigo_tarea.sql`
- **Categoría funcional:** Generación de códigos secuenciales
- **Tipo:** Procedimiento (`CALL`, un parámetro `OUT`)
- **Propósito:** Genera un folio de seguimiento legible para una tarea
  recién creada (ej. `TAR-2026-000042`), distinto de la clave primaria
  `SERIAL` interna (`id_tarea`).
- **Parámetros de entrada:** Ninguno.
- **Parámetros de salida:** `p_codigo VARCHAR` (`OUT`), formato
  `TAR-{año}-{secuencia de 6 dígitos con ceros a la izquierda}`.
- **Cursores devueltos:** Ninguno.
- **Tablas afectadas:** Ninguna tabla de negocio directamente; usa la
  secuencia dedicada `seq_codigo_tarea` (creada por el mismo archivo).
- **Motivo de SP (no ORM):** usar una `SEQUENCE` nativa de PostgreSQL vía
  `nextval()` es atómico bajo concurrencia por diseño del motor; un
  equivalente en Java (`SELECT COUNT(*)+1`) tendría una condición de
  carrera real entre el `SELECT` y el `INSERT` si dos estudiantes crean
  una tarea al mismo tiempo.
- **Invocado desde:** `TareaRepository.spGenerarCodigoTarea()` vía
  `@Procedure(procedureName = ..., outputParameterName = "p_codigo")`
  (JPA 2.1). `TareaController.crear()` lo llama siempre en el servidor;
  cualquier `"codigo"` que envíe el cliente en el body se ignora.
- **Endpoint que lo usa:** `POST /api/tareas`
- **Nota de esquema:** requirió agregar la columna `tareas.codigo`
  (`VARCHAR(20)`, nullable) vía `V2__add_codigo_tarea.sql` (Flyway) y su
  espejo en `db/schema.sql` (Docker).

---

## sp_reporte_calificaciones

- **Archivo:** `db/procs/sp_reporte_calificaciones.sql`
- **Categoría funcional:** Reportes
- **Tipo:** Procedimiento (`CALL`, cuatro parámetros `OUT`)
- **Propósito:** Boletín de calificaciones de un usuario — promedio
  general, cantidad de materias calificadas, y las materias donde obtuvo
  su mejor y peor nota.
- **Parámetros de entrada:** `p_id_usuario INTEGER`
- **Parámetros de salida:** `p_promedio NUMERIC(4,2)`,
  `p_total_materias INTEGER`, `p_mejor_materia VARCHAR`,
  `p_peor_materia VARCHAR` (los cuatro `OUT`; quedan en `NULL` si el
  usuario no tiene calificaciones).
- **Cursores devueltos:** Ninguno.
- **Tablas afectadas:** `calificaciones`, `materia` (solo lectura).
- **Motivo de SP (no ORM):** combina tres consultas agregadas distintas
  (promedio+conteo, mejor materia, peor materia) sobre un JOIN
  calificaciones-materia en una sola proyección de "boletín" que no
  corresponde a ninguna entidad JPA.
- **Invocado desde:** `CalificacionRepository.reporteCalificaciones()` vía
  `@Procedure(name = "Calificacion.reporteCalificaciones")`, que referencia
  el `@NamedStoredProcedureQuery` declarado sobre la entidad
  `Calificacion` (mecanismo formal completo de JPA 2.1, con los cuatro
  parámetros tipados vía `@StoredProcedureParameter`).
- **Endpoint que lo usa:** `GET /api/calificaciones/reporte`

---

## Convenciones generales

- Todos los parámetros son **nombrados y tipados** (`p_id_usuario
  INTEGER`); ninguno construye SQL dinámico ni concatena entrada de
  usuario.
- Nomenclatura: `fn_<verbo>_<sustantivo>.sql` para funciones,
  `sp_<verbo>_<sustantivo>.sql` para procedimientos.
- Los tres procedimientos originales (`fn_obtener_resumen_academico`,
  `fn_listar_tareas_pendientes`, `fn_marcar_avisos_leidos`) son
  `FUNCTION`s invocadas vía `@Query(nativeQuery = true)` con parámetros
  nombrados — seguras (sin concatenación), pero no usan el mecanismo
  formal `@Procedure`/`@NamedStoredProcedureQuery` de JPA 2.1 que exige
  A.2.1. Los tres procedimientos agregados en la Entrega Final
  (`sp_validar_disponibilidad_horario`, `sp_generar_codigo_tarea`,
  `sp_reporte_calificaciones`) sí lo usan, implementados como
  `PROCEDURE` con parámetros `OUT` — ver la nota de implementación en
  `sp_validar_disponibilidad_horario` para el motivo técnico.
- Todos están versionados en `db/procs/` y se aplican junto con el
  esquema base al levantar el contenedor de PostgreSQL (Bloque B), o
  manualmente contra el Postgres local en desarrollo nativo sin Docker.

---

## Limitación conocida: mecanismo de invocación de los 3 procedimientos originales

Los tres procedimientos preexistentes a esta entrega —
`fn_obtener_resumen_academico`, `fn_listar_tareas_pendientes` y
`fn_marcar_avisos_leidos` — se invocan desde `DashboardRepository` con
`@Query(nativeQuery = true)` y parámetros nombrados (`:idUsuario`), **no**
con `@Procedure` o `@NamedStoredProcedureQuery` como pide la letra estricta
del Bloque A.2.1.

Esto no es un riesgo de seguridad: los tres reciben el parámetro como
valor vinculado por Spring Data (`:idUsuario`), nunca por concatenación de
cadenas, por lo que no hay superficie de inyección SQL distinta a la de
cualquier consulta parametrizada del proyecto.

Es, sí, un incumplimiento de la forma exacta que exige el rubro. Se deja
documentado aquí como limitación conocida en lugar de refactorizarlos hoy,
por el tiempo disponible en esta entrega y porque funcionan correctamente
tal como están — refactorizarlos implica el mismo problema técnico
resuelto en los tres procedimientos nuevos (Hibernate 6.6.x exige que sea
un `PROCEDURE` con parámetros `OUT`, no una `FUNCTION`, para que
`@Procedure` funcione contra PostgreSQL), lo cual a su vez requiere
cambiar su tipo de objeto SQL y su firma, no solo la anotación Java.

Los tres procedimientos agregados en esta entrega
(`sp_validar_disponibilidad_horario`, `sp_generar_codigo_tarea`,
`sp_reporte_calificaciones`) sí cumplen la especificación exacta del
Bloque A.2.1 desde el primer commit.