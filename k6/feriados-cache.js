// k6/feriados-cache.js
// Bloque C.1 - Benchmark de carga contra GET /api/feriados (endpoint con
// cache-aside en Redis, Bloque 3 de la entrega de hoy), separando dos
// escenarios:
//   - frio: cada request pide una combinacion (anio,pais) que nunca se
//     repite dentro de la corrida -> garantiza cache-miss real
//     (FeriadosService llama a la API externa Nager.Date en cada una).
//   - caliente: todas las requests piden el mismo (anio,pais), ya
//     precalentado en setup() -> garantiza cache-hit real contra Redis.
//
// Corre con: k6 run k6/feriados-cache.js --summary-export=archivo.json
// (ver comando completo en la respuesta que acompaña este script).

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = 'https://localhost:8443';

// Escenario CALIENTE: (anio,pais) fijo. 2026 se eligio a proposito FUERA
// del rango de anios que usa el escenario FRIO (ver FRIO_ANIOS mas
// abajo), para que las dos claves de cache nunca puedan coincidir.
const CALIENTE_ANIO = 2026;
const CALIENTE_PAIS = 'EC';

// Escenario FRIO: 5 anios historicos x 204 paises reales soportados por
// Nager.Date (verificado contra GET /api/v3/AvailableCountries antes de
// escribir este script) = 1020 combinaciones posibles. Con 5 VUs x 10
// iteraciones (50 requests) por corrida, se usa menos del 5% de la
// grilla, dejando margen amplio para que la construccion de indices de
// mas abajo garantice cero colisiones DENTRO de una misma corrida.
const FRIO_ANIOS = [2015, 2016, 2017, 2018, 2019];
const FRIO_PAISES = [
    'AD', 'AG', 'AI', 'AL', 'AM', 'AO', 'AR', 'AT', 'AU', 'AW',
    'AX', 'BA', 'BB', 'BD', 'BE', 'BF', 'BG', 'BH', 'BI', 'BJ',
    'BL', 'BM', 'BO', 'BQ', 'BR', 'BS', 'BW', 'BY', 'BZ', 'CA',
    'CC', 'CD', 'CF', 'CG', 'CH', 'CI', 'CK', 'CL', 'CM', 'CN',
    'CO', 'CR', 'CU', 'CV', 'CW', 'CX', 'CY', 'CZ', 'DE', 'DJ',
    'DK', 'DM', 'DO', 'DZ', 'EC', 'EE', 'EG', 'ER', 'ES', 'ET',
    'FI', 'FK', 'FM', 'FO', 'FR', 'GA', 'GB', 'GD', 'GE', 'GF',
    'GG', 'GH', 'GI', 'GL', 'GM', 'GN', 'GP', 'GQ', 'GR', 'GT',
    'GW', 'GY', 'HK', 'HN', 'HR', 'HT', 'HU', 'ID', 'IE', 'IM',
    'IQ', 'IS', 'IT', 'JE', 'JM', 'JP', 'KE', 'KH', 'KI', 'KM',
    'KN', 'KR', 'KY', 'KZ', 'LC', 'LI', 'LR', 'LS', 'LT', 'LU',
    'LV', 'LY', 'MA', 'MC', 'MD', 'ME', 'MF', 'MG', 'MH', 'MK',
    'ML', 'MN', 'MP', 'MQ', 'MR', 'MS', 'MT', 'MW', 'MX', 'MZ',
    'NA', 'NC', 'NE', 'NF', 'NG', 'NI', 'NL', 'NO', 'NR', 'NU',
    'NZ', 'PA', 'PE', 'PF', 'PG', 'PH', 'PL', 'PM', 'PN', 'PR',
    'PT', 'PW', 'PY', 'RO', 'RS', 'RU', 'RW', 'SB', 'SC', 'SD',
    'SE', 'SG', 'SH', 'SI', 'SJ', 'SK', 'SL', 'SM', 'SN', 'SO',
    'SR', 'SS', 'ST', 'SV', 'SX', 'SY', 'SZ', 'TC', 'TD', 'TG',
    'TK', 'TN', 'TO', 'TR', 'TT', 'TV', 'TZ', 'UA', 'UG', 'US',
    'UY', 'VA', 'VC', 'VE', 'VG', 'VI', 'VN', 'VU', 'WF', 'WS',
    'YE', 'ZA', 'ZM', 'ZW',
];
const GRILLA_TOTAL = FRIO_ANIOS.length * FRIO_PAISES.length; // 1020

export const options = {
    insecureSkipTLSVerify: true, // certificado autofirmado de desarrollo
    scenarios: {
        frio: {
            executor: 'per-vu-iterations',
            vus: 5,
            iterations: 10,
            exec: 'frio',
            startTime: '0s',
            maxDuration: '45s',
        },
        caliente: {
            executor: 'per-vu-iterations',
            vus: 10,
            iterations: 20,
            exec: 'caliente',
            // Arranca despues de que el escenario frio termine (con
            // margen), para que ninguno de los dos compita por CPU/red
            // con el otro mientras se mide.
            startTime: '50s',
            maxDuration: '30s',
        },
    },
    thresholds: {
        // Umbrales literales de la Entrega Final. Ver advertencia sobre
        // el de "frio" en la respuesta que acompaña este script: es
        // esperable que NO se cumpla, por una razon de arquitectura, no
        // un bug.
        'http_req_duration{scenario:caliente}': ['p(95)<200'],
        'http_req_duration{scenario:frio}': ['p(95)<500'],
        'http_req_failed': ['rate<0.01'],
    },
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
    const loginRes = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: 'admin@uteq.edu.ec', contrasena: 'Admin123!' }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    check(loginRes, {
        'login exitoso (200)': (r) => r.status === 200,
        'token presente': (r) => JSON.parse(r.body).token !== undefined,
    });

    if (loginRes.status !== 200) {
        throw new Error(`No se pudo autenticar en setup(): status ${loginRes.status}`);
    }

    const token = JSON.parse(loginRes.body).token;

    // Precalienta la clave fija del escenario "caliente" ANTES de medir.
    // Esta request no se mide (no corre dentro de ningun escenario de
    // k6): su unico proposito es garantizar que las 200 iteraciones de
    // "caliente" sean siempre cache-hit.
    const warmup = http.get(
        `${BASE_URL}/api/feriados?anio=${CALIENTE_ANIO}&pais=${CALIENTE_PAIS}`,
        { headers: { Authorization: `Bearer ${token}` } }
    );
    check(warmup, {
        'calentamiento de cache exitoso (200)': (r) => r.status === 200,
    });

    // offset fijo para TODA la corrida (se calcula una sola vez aca, no
    // dentro de frio() en cada iteracion). Cambia entre corridas
    // separadas (Date.now() distinto cada vez que se invoca k6 run),
    // rotando la ventana de combinaciones (anio,pais) que se usa en cada
    // ejecucion. Dentro de una misma corrida queda fijo, lo que permite
    // garantizar cero colisiones vía la combinacion (VU, ITER) — ver
    // comentario en frio().
    const offset = Math.floor(Date.now() / 1000);

    return { token, offset };
}

export function frio(data) {
    // (__VU, __ITER) es unico dentro de la corrida por diseño de k6
    // (ningun VU repite su propio contador de iteracion, y cada VU tiene
    // un numero distinto). Multiplicar __VU por 100 (> iteraciones por
    // VU configuradas) y sumar __ITER produce 50 enteros TODOS distintos
    // entre si, sin importar el offset. Ese offset (constante durante
    // toda la corrida, ver setup()) se sub-listancia sobre la grilla de
    // 1020 combinaciones sin alterar esa propiedad: sigue habiendo cero
    // colisiones dentro de esta corrida. Entre corridas separadas no hay
    // garantia matematica absoluta de cero solapamiento (dos corridas
    // podrian, en teoria, terminar usando la misma ventana de la
    // grilla), pero con offset basado en tiempo real y solo 50/1020
    // combinaciones consumidas por corrida, la probabilidad practica es
    // minima. Documentado como decision consciente, no como garantia
    // matematica perfecta.
    const indice = data.offset + (__VU - 1) * 100 + __ITER;
    const anio = FRIO_ANIOS[indice % FRIO_ANIOS.length];
    const pais = FRIO_PAISES[Math.floor(indice / FRIO_ANIOS.length) % FRIO_PAISES.length];

    const res = http.get(
        `${BASE_URL}/api/feriados?anio=${anio}&pais=${pais}`,
        { headers: { Authorization: `Bearer ${data.token}` } }
    );

    check(res, {
        'status 200': (r) => r.status === 200,
        'origen fue API_EXTERNA (cache-miss real, no reciclado)': (r) => {
            try {
                return JSON.parse(r.body).origen === 'API_EXTERNA';
            } catch (e) {
                return false;
            }
        },
    });

    // Cortesia con la API publica gratuita de terceros: no la satura.
    sleep(0.5);
}

export function caliente(data) {
    const res = http.get(
        `${BASE_URL}/api/feriados?anio=${CALIENTE_ANIO}&pais=${CALIENTE_PAIS}`,
        { headers: { Authorization: `Bearer ${data.token}` } }
    );

    check(res, {
        'status 200': (r) => r.status === 200,
        'origen fue CACHE (cache-hit real)': (r) => {
            try {
                return JSON.parse(r.body).origen === 'CACHE';
            } catch (e) {
                return false;
            }
        },
    });

    sleep(0.2);
}
