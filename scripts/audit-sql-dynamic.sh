#!/usr/bin/env bash
# audit-sql-dynamic.sh
# Bloque A.2.3 - Audita que no exista SQL dinamico construido por
# concatenacion de entrada de usuario, ni en el codigo Java ni dentro
# de los procedimientos/funciones almacenados. Uso: `make audit`.
#
# Segun la rubrica (regla 7): cualquier evidencia de esto califica
# automaticamente C1 y C6 como Insuficiente o menos.
#
# Salida: 0 si no encuentra nada sospechoso, 1 si encuentra patrones
# de riesgo (falsos positivos posibles: revisar manualmente cada hit).

set -uo pipefail

fallos=0

echo "== Auditando codigo Java (src/main/java) =="

if grep -rnE "createNativeQuery\s*\(.*\+|createQuery\s*\(.*\+" src/main/java 2>/dev/null; then
    echo "  ^ posible concatenacion dentro de createNativeQuery/createQuery"
    fallos=1
fi

if grep -rnE '"(SELECT|INSERT|UPDATE|DELETE)[^"]*"\s*\+' src/main/java 2>/dev/null; then
    echo "  ^ posible concatenacion de SQL literal con '+'"
    fallos=1
fi

if grep -rnE 'String\.format\s*\(\s*"(SELECT|INSERT|UPDATE|DELETE)' src/main/java 2>/dev/null; then
    echo "  ^ SQL construido con String.format (revisar origen de los parametros)"
    fallos=1
fi

echo ""
echo "== Auditando procedimientos/funciones (db/procs) =="

if [ -d db/procs ]; then
    if grep -rniE "EXECUTE IMMEDIATE|sp_executesql" db/procs/*.sql 2>/dev/null; then
        echo "  ^ uso de SQL dinamico (EXECUTE IMMEDIATE / sp_executesql) encontrado"
        fallos=1
    fi
    if grep -rnE "\|\|.*(SELECT|INSERT|UPDATE|DELETE)|(SELECT|INSERT|UPDATE|DELETE).*\|\|" db/procs/*.sql 2>/dev/null; then
        echo "  ^ posible concatenacion de SQL con || dentro de un procedimiento"
        fallos=1
    fi
else
    echo "  AVISO: no existe la carpeta db/procs"
fi

echo ""
if [ "$fallos" -eq 0 ]; then
    echo "OK: no se encontraron patrones de SQL dinamico por concatenacion."
    exit 0
else
    echo "FALLO: revisa manualmente los hits marcados arriba antes de continuar."
    exit 1
fi