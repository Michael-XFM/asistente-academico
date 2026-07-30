.PHONY: up down test bench audit clean

# Levanta el sistema completo (postgres, redis, backend, frontend) desde
# una clonacion limpia, sin intervencion humana adicional (Bloque B.1).
up:
	docker compose up -d --build
	@echo "Sistema arriba. Backend: https://localhost:8443 | Frontend: http://localhost:80"

# Detiene los contenedores sin borrar el volumen de datos.
down:
	docker compose down

# Corre la suite de pruebas JUnit 5 y regenera el reporte JaCoCo en
# docs/mediciones/jacoco (Bloque C.4).
test:
	./mvnw clean test

# Corre los benchmarks de carga con k6 (Bloque C.1) contra el sistema
# ya levantado con `make up`. Requiere k6 instalado localmente.
bench:
	k6 run k6/script.js

# Corre las validaciones de auditoria: trazabilidad de requisitos
# (Bloque A.3.3) y ausencia de SQL dinamico por concatenacion (Bloque A.2.3).
audit:
	bash scripts/validate-traceability.sh
	bash scripts/audit-sql-dynamic.sh

# Apaga los contenedores y borra el volumen de datos (reinicio total).
clean:
	docker compose down -v
	./mvnw clean