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
JWT (ver `RedisService.java`). Pendiente de implementar antes de la
Entrega Final si el tiempo lo permite.