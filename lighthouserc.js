// lighthouserc.js
// Bloque C.5 - Auditoria de accesibilidad y calidad web con Lighthouse CI.
// Perfil movil, throttling Slow 4G, umbrales minimos exigidos por la guia.
module.exports = {
    ci: {
        collect: {
            url: ['http://localhost/index.html'],
            numberOfRuns: 1,
            settings: {
                throttlingMethod: 'simulate',
                throttling: {
                    rttMs: 150,
                    throughputKbps: 1638.4,
                    cpuSlowdownMultiplier: 4,
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
            outputDir: './docs/mediciones/lighthouse',
        },
    },
};