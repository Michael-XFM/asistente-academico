// k6/opts.js
// Configuracion de carga para el Bloque C.1 (rendimiento).
// 50 VUs sostenidos durante 30s, con ramp-up/ramp-down declarados
// explicitamente para que la corrida sea reproducible entre ejecuciones
// (Bloque B.2 - determinismo de mediciones).

export const options = {
    insecureSkipTLSVerify: true, // certificado autofirmado de desarrollo (Bloque C.2 / A02)
    stages: [
        { duration: '10s', target: 50 }, // ramp-up
        { duration: '30s', target: 50 }, // carga sostenida (medida real)
        { duration: '5s', target: 0 },   // ramp-down
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'],
        http_req_failed: ['rate<0.01'],
    },
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};