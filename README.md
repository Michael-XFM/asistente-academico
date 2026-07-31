# Asistente Virtual Académico con Chatbot Híbrido

[![DOI](https://zenodo.org/badge/DOI/PENDIENTE.svg)](PENDIENTE)
[![Licencia](https://img.shields.io/badge/licencia-MIT-green)](LICENSE)
[![Entrega](https://img.shields.io/badge/entrega-v0.9.0--rc-blue)](https://github.com/Michael-XFM/asistente-academico/releases/tag/v0.9.0-rc)

Sistema web académico para estudiantes y docentes de la UTEQ: gestión de tareas,
horarios, calificaciones, avisos y un chatbot híbrido. Proyecto Fin de Curso —
asignatura Aplicaciones Web, quinto nivel, carrera de Ingeniería de Software.

## Arranque rápido

Requisitos: Docker Desktop y `make` instalados.

```bash
git clone https://github.com/Michael-XFM/asistente-academico.git
cd asistente-academico
cp .env.example .env
make up
```

El sistema queda disponible en:
- Backend (HTTPS, certificado autofirmado): `https://localhost:8443`
- Frontend: `http://localhost`

## Credenciales de prueba

| Campo | Valor |
|---|---|
| Email | `admin@uteq.edu.ec` |
| Contraseña | `Admin123!` |

## Comandos disponibles

| Comando | Descripción |
|---|---|
| `make up` | Levanta el sistema completo desde cero |
| `make down` | Detiene los contenedores (conserva los datos) |
| `make test` | Corre la suite de pruebas JUnit y regenera el reporte JaCoCo |
| `make bench` | Corre el benchmark de carga con k6 |
| `make audit` | Corre las validaciones de trazabilidad y ausencia de SQL dinámico |
| `make clean` | Apaga los contenedores y borra los datos |

## Stack técnico

Spring Boot 3.5 (Java 21) · PostgreSQL 16 · Redis 7 · JWT · Flyway · Docker Compose ·
frontend HTML/CSS/JS estático.

## Documentación

- Informe técnico completo: [`docs/informe-entrega-3.pdf`](docs/informe-entrega-3.pdf)
- SRS: [`docs/requisitos/`](docs/requisitos/)
- Arquitectura (ADRs + diagramas C4): [`docs/adr/`](docs/adr/), [`docs/arquitectura/`](docs/arquitectura/)
- Matriz de trazabilidad: [`docs/trazabilidad/matriz.csv`](docs/trazabilidad/matriz.csv)
- Evidencia empírica (rendimiento, seguridad, cobertura, accesibilidad): [`docs/mediciones/`](docs/mediciones/)

## Licencia

MIT — ver [LICENSE](LICENSE).

## Cómo citar

Ver [CITATION.cff](CITATION.cff). DOI: *pendiente de asignación en Zenodo*.