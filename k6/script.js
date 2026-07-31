// k6/script.js
// Bloque C.1 - Benchmark de carga contra el endpoint de listado
// protegido GET /api/tareas. Corre con: k6 run k6/script.js
// (o `make bench`, que llama exactamente este comando).

import http from 'k6/http';
import { check, sleep } from 'k6';
import { options } from './opts.js';

export { options };

const BASE_URL = 'https://localhost:8443';

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

    return { token: JSON.parse(loginRes.body).token };
}

export default function (data) {
    const res = http.get(`${BASE_URL}/api/tareas?page=0&size=10`, {
        headers: { Authorization: `Bearer ${data.token}` },
    });

    check(res, {
        'status 200': (r) => r.status === 200,
        'respuesta no vacia': (r) => r.body && r.body.length > 0,
    });

    sleep(1);
}