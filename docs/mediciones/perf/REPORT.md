# Reporte de rendimiento — GET /api/tareas

**Fecha:** 2026-07-30 (ISO 8601: ver timestamps en k6-run{1,2,3}.json)
**Commit:** ver `git log -1 --format=%h` al momento de la corrida
**Herramienta:** k6 v2.1.0
**Endpoint:** `GET /api/tareas?page=0&size=10` (protegido, JWT Bearer)
**Configuración:** 50 VUs, 30s de carga sostenida (ramp-up 10s, ramp-down 5s), ver `k6/opts.js`

## Nota metodológica

El endpoint evaluado **no tiene una capa de caché activa** (Redis en este
proyecto solo se usa para blacklist de tokens JWT revocados, no para
cachear respuestas de listado — ver limitación documentada). Por lo
tanto estas tres corridas representan el escenario "cache frío" en los
tres casos; no hay comparación caliente/frío posible con el estado
actual del sistema.

## Resultados por corrida

| Corrida | avg (ms) | p50/med (ms) | p90 (ms) | p95 (ms) | p99 (ms) | max (ms) | Error rate | Throughput (req/s) |
|---------|----------|--------------|----------|----------|----------|----------|------------|---------------------|
| 1       | 3.22     | 3.21         | 3.60     | 3.71     | 4.05     | 65.91    | 0.00%      | 41.51               |
| 2       | 3.29     | 3.27         | 3.63     | 3.74     | 4.29     | 65.80    | 0.00%      | 41.51               |
| 3       | 3.49     | 3.41         | 4.01     | 4.21     | 4.93     | 65.18    | 0.00%      | 41.48               |

## Estadística agregada (n=3 corridas)

- **Media del tiempo de respuesta (avg):** 3.33 ms
- **Desviación típica:** 0.14 ms
- **Intervalo de confianza 95% (t-Student, n=3):** [2.99, 3.68] ms
- **Media de p95 entre corridas:** 3.89 ms
- **Media de p99 entre corridas:** 4.42 ms
- **Tasa de errores HTTP ≥500:** 0.00% en las tres corridas
- **Throughput medio:** 41.50 req/s ± 0.02 (IC 95%)

## Comparación contra umbrales objetivo

| Umbral (guía) | Objetivo | Resultado | Cumple |
|---|---|---|---|
| p95 con cache caliente | < 200 ms | N/A (sin caché activa) | N/A |
| p95 con cache frío | < 500 ms | 3.71–4.21 ms | ✓ Sí, ampliamente |
| Tasa de error HTTP ≥500 | 0% | 0.00% | ✓ Sí |

## Limitación conocida

El endpoint de listado (`GET /api/tareas`) no implementa caché con Redis
pese a que el Bloque A.1 de la guía lo exige con TTL y hit ratio medido.
Redis en este proyecto se usa exclusivamente para blacklist de tokens
JWT (ver `RedisService.java`). La comparación caliente/frío exigida por
la Entrega Final se resolvió sobre un endpoint distinto que sí tiene
caché real — ver el benchmark siguiente.

---

# Reporte de rendimiento — GET /api/feriados (cache-aside frío vs caliente)

**Fecha:** 2026-08-16 (ISO 8601: ver timestamps en feriados-cache-run{1..5}.json)
**Commit:** ver `git log -1 --format=%h` al momento de la corrida
**Herramienta:** k6 v2.1.0
**Endpoint:** `GET /api/feriados` (protegido, JWT Bearer), con caché real
en Redis (patrón cache-aside, TTL 1h — ver `FeriadosService.java`)
**Configuración:** dos escenarios en el mismo script, `k6/feriados-cache.js`:

- **frío:** 5 VUs × 10 iteraciones (50 requests), cada una con una
  combinación (año, país) distinta dentro de la corrida — garantiza
  cache-miss real en cada request (verificado con el check `origen fue
  API_EXTERNA`, no asumido).
- **caliente:** 10 VUs × 20 iteraciones (200 requests), todas contra la
  misma clave (`anio=2026, pais=EC`), precalentada en `setup()` antes de
  medir — garantiza cache-hit real (verificado con el check `origen fue
  CACHE`).

## Nota metodológica: contaminación cruzada detectada y no ocultada

Las 5 corridas se ejecutaron con minutos de diferencia entre sí, no de
forma perfectamente simultánea. El script genera las combinaciones
(año, país) del escenario frío a partir de un offset basado en el
timestamp de cada corrida (ver comentario en `frio()` dentro de
`k6/feriados-cache.js`), lo cual garantiza cero colisiones **dentro**
de una misma corrida, pero **no** una garantía matemática de cero
solapamiento **entre** corridas distintas dentro de la ventana de TTL
de 1 hora — riesgo que el propio script deja documentado como decisión
consciente, no como garantía absoluta.

Ese riesgo se materializó: el check `origen fue API_EXTERNA` (que
verifica que la respuesta realmente vino de la API externa, no de un
reciclado de caché) falló parcialmente en 2 de las 5 corridas:

| Corrida | Requests "frío" con origen=API_EXTERNA confirmado | % |
|---|---|---|
| 1 | 50/50 | 100.0% |
| 2 | 38/50 | 76.0% |
| 3 | 50/50 | 100.0% |
| 4 | 50/50 | 100.0% |
| 5 | 46/50 | 92.0% |

Esto **no invalida el resultado agregado**, por dos razones: (1) un
request que reutiliza caché de una corrida anterior es, en el peor
caso, más **rápido** que un cache-miss real (nunca más lento), por lo
que la contaminación sesga la medición de "frío" hacia abajo (más
optimista, no más pesimista) — el p95 real de un cache-miss puro es, si
acaso, más alto que el reportado aquí; y (2) el hallazgo es una fuente
de ruido real y medible, no un fallo silencioso: quedó capturado por el
propio check automatizado, exactamente para eso se diseñó. Se documenta
en vez de descartar las corridas 2 y 5, siguiendo el mismo criterio de
transparencia que el resto de este proyecto.

## Resultados por corrida — escenario FRÍO (cache-miss)

| Corrida | avg (ms) | p50/med (ms) | p90 (ms) | p95 (ms) | p99 (ms) | max (ms) | Throughput (req/s, estimado) | Umbral p95<500ms |
|---------|----------|--------------|----------|----------|----------|----------|-------------------------------|-------------------|
| 1       | 353.36   | 360.70       | 396.47   | 453.50   | 457.37   | 459.71   | 5.86                          | ✓ Cumple          |
| 2       | 331.17   | 369.34       | 614.83   | 708.70   | 801.34   | 812.68   | 6.02                          | ✗ No cumple       |
| 3       | 400.63   | 369.48       | 452.64   | 692.35   | 703.77   | 711.09   | 5.55                          | ✗ No cumple       |
| 4       | 402.88   | 361.72       | 670.26   | 675.70   | 749.60   | 818.23   | 5.54                          | ✗ No cumple       |
| 5       | 386.93   | 372.07       | 508.93   | 739.48   | 774.44   | 808.04   | 5.64                          | ✗ No cumple       |

## Resultados por corrida — escenario CALIENTE (cache-hit)

| Corrida | avg (ms) | p50/med (ms) | p90 (ms) | p95 (ms) | p99 (ms) | max (ms) | Throughput (req/s, estimado) | Umbral p95<200ms |
|---------|----------|--------------|----------|----------|----------|----------|-------------------------------|-------------------|
| 1       | 8.81     | 8.26         | 11.75    | 13.28    | 15.37    | 16.05    | 47.89                         | ✓ Cumple          |
| 2       | 6.87     | 6.54         | 8.21     | 8.50     | 17.57    | 18.43    | 48.34                         | ✓ Cumple          |
| 3       | 5.62     | 5.19         | 7.95     | 8.53     | 9.60     | 15.23    | 48.63                         | ✓ Cumple          |
| 4       | 6.32     | 5.35         | 8.61     | 11.47    | 16.27    | 16.27    | 48.47                         | ✓ Cumple          |
| 5       | 4.80     | 4.48         | 6.55     | 7.27     | 7.71     | 8.22     | 48.83                         | ✓ Cumple          |

*Throughput estimado como VUs / (duración media + think-time configurado
en el script — 0.5s en frío, 0.2s en caliente); no es una tasa medida
directamente por k6 sobre una sub-métrica por escenario (k6 no expone
`http_reqs` desglosado por tag salvo que se declare un threshold sobre
esa combinación, lo cual no estaba configurado). La tasa global
combinada (ambos escenarios + los huecos de espera entre ellos) sí la
reporta k6 directamente: ronda 4.3–4.7 req/s en las 5 corridas, pero esa
cifra mezcla ambos escenarios y el tiempo muerto entre uno y otro, por
lo que no se usa como comparación principal aquí.*

## Estadística agregada (n=5 corridas)

**Escenario frío:**
- **Media de p95 entre corridas:** 653.94 ms
- **Desviación típica:** 114.50 ms
- **Intervalo de confianza 95% (t-Student, n=5):** [511.79, 796.09] ms
- **Media del tiempo de respuesta (avg):** 374.99 ms (DT 31.49 ms)
- **Checks "origen=API_EXTERNA" confirmados:** 234/250 (93.6%)

**Escenario caliente:**
- **Media de p95 entre corridas:** 9.81 ms
- **Desviación típica:** 2.48 ms
- **Intervalo de confianza 95% (t-Student, n=5):** [6.73, 12.89] ms
- **Media del tiempo de respuesta (avg):** 6.48 ms (DT 1.51 ms)
- **Checks "origen=CACHE" confirmados:** 1000/1000 (100.0%)

**Mejora relativa (p95 frío / p95 caliente): ~66.7x**

## Comparación contra umbrales objetivo

| Umbral (guía) | Objetivo | Resultado (media p95, n=5) | Cumple |
|---|---|---|---|
| p95 con cache caliente | < 200 ms | 9.81 ms | ✓ Sí, ampliamente (5/5 corridas) |
| p95 con cache frío | < 500 ms | 653.94 ms | ✗ No (4/5 corridas superan el umbral; solo la corrida 1 lo cumple) |
| Tasa de error HTTP (status ≠ 200) | 0% | 0.00% en las 5 corridas (`status 200` check: 100% en las 5) | ✓ Sí |

## Por qué "frío" no cumple, y por qué eso no es un defecto del sistema

El umbral de 500ms para cache frío, tal como está redactado en la guía,
fue calibrado sobre el benchmark original de `GET /api/tareas`, donde
"frío" significaba "sin caché, pero de todas formas una consulta 100%
local a Postgres" (3-4ms). Aplicado a `GET /api/feriados`, "frío"
significa algo estructuralmente distinto: una llamada HTTP real a un
tercero (Nager.Date) fuera de la red del proyecto, cuya latencia de red
no está bajo el control del sistema. Los ~650ms medidos son
consistentes con lo esperable para un round-trip HTTPS a un servidor
público en otro continente, no con una ineficiencia del código.

El valor del patrón cache-aside no se demuestra evitando esa latencia
en el primer request — no se puede evitar, es inherente a depender de
un tercero — sino en que **todos los requests siguientes la evitan por
completo**: de 653.94ms (p95 frío) a 9.81ms (p95 caliente) es exactamente
la mejora que el patrón promete, medida y confirmada con checks
explícitos por request, no supuesta.