#!/usr/bin/env bash
# validate-traceability.sh
# Bloque A.3.3 - Valida que docs/trazabilidad/matriz.csv no tenga
# requisitos sin correspondencia en al menos una historia de usuario,
# un caso de uso o una prueba automatizada, y que cada id_requisito
# siga el formato REQ-F-NNN / REQ-NF-NNN.
#
# Uso: bash scripts/validate-traceability.sh
# Salida: 0 si todo esta bien, 1 si encuentra problemas.

set -euo pipefail

MATRIZ="docs/trazabilidad/matriz.csv"

if [ ! -f "$MATRIZ" ]; then
    echo "ERROR: no se encontro $MATRIZ"
    exit 1
fi

echo "Validando trazabilidad en $MATRIZ ..."

fallos=0

# Revisa formato de ID (columna 1)
while IFS=',' read -r id _; do
    [ "$id" = "id_requisito" ] && continue
    [ -z "$id" ] && continue
    if ! [[ "$id" =~ ^REQ-(F|NF)-[0-9]{3}$ ]]; then
        echo "  [ID INVALIDO] '$id' no sigue el formato REQ-F-NNN / REQ-NF-NNN."
        fallos=1
    fi
done < <(tail -n +2 "$MATRIZ")

# Revisa que cada requisito tenga al menos historia_usuario (col 4),
# caso_de_uso (col 5) o prueba_automatizada (col 8).
while IFS=',' read -r id tipo prioridad historia caso modulo endpoint prueba resto; do
    [ -z "$id" ] && continue
    if [ -z "$historia" ] && [ -z "$caso" ] && [ -z "$prueba" ]; then
        echo "  [FALTA TRAZABILIDAD] $id no tiene historia_usuario, caso_de_uso ni prueba_automatizada."
        fallos=1
    fi
done < <(tail -n +2 "$MATRIZ")

if [ "$fallos" -eq 0 ]; then
    echo "OK: todos los requisitos tienen ID valido y trazabilidad minima."
    exit 0
else
    echo "FALLO: revisa los problemas listados arriba antes de continuar."
    exit 1
fi