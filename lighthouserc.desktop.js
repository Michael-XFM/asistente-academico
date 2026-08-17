// lighthouserc.desktop.js
// Bloque C.5 - Auditoria de calidad web con Lighthouse CI, perfil desktop.
// Complementa lighthouserc.js (perfil movil): la Entrega Final exige
// >=3 corridas POR PERFIL (mobile y desktop por separado, 6 en total),
// no una corrida general. formFactor + screenEmulation + throttling son
// los valores estandar del preset "desktop" de Lighthouse (sin
// emulacion movil, red rapida, sin desaceleracion de CPU).
//
// NOTA: preparado para automatizacion via lhci, pero las 6 corridas
// reales de esta entrega (docs/mediciones/lighthouse/{mobile,desktop}/)
// se generaron manualmente desde Chrome DevTools por un bug EPERM de
// lhci en Windows sin solucion encontrada a tiempo. Config archivado
// para cuando se resuelva ese bug.
module.exports = {
    ci: {
        collect: {
            url: ['http://localhost/index.html'],
            numberOfRuns: 3,
            settings: {
                formFactor: 'desktop',
                screenEmulation: {
                    mobile: false,
                    width: 1350,
                    height: 940,
                    deviceScaleFactor: 1,
                    disabled: false,
                },
                throttlingMethod: 'simulate',
                throttling: {
                    rttMs: 40,
                    throughputKbps: 10240,
                    cpuSlowdownMultiplier: 1,
                },
            },
        },
        assert: {
            assertions: {
                'categories:performance': ['error', { minScore: 0.8 }],
                'categories:accessibility': ['error', { minScore: 0.9 }],
                'categories:best-practices': ['error', { minScore: 0.9 }],
                'categories:seo': ['error', { minScore: 0.9 }],
            },
        },
        upload: {
            target: 'filesystem',
            outputDir: './docs/mediciones/lighthouse/desktop',
        },
    },
};
