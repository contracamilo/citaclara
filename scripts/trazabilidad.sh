#!/usr/bin/env bash
# Trazabilidad spec ↔ tests (constitución, Principio VI; puerta de merge 2).
#
# Lista los requisitos (FR-xxx), criterios de éxito (SC-xxx) y escenarios de aceptación (USn-ACm)
# de una spec que no tienen ningún test cuyo nombre visible lleve la referencia [NNN][…].
# Sale con código 1 si falta alguno.
#
# Uso: scripts/trazabilidad.sh [carpeta-de-la-spec]   (por defecto specs/001-nucleo-agenda)
set -euo pipefail

raiz="$(cd "$(dirname "$0")/.." && pwd)"
carpeta="${1:-specs/001-nucleo-agenda}"
spec="$raiz/$carpeta/spec.md"
numero="$(basename "$carpeta" | cut -d- -f1)"

# Criterios que no se pueden automatizar: se miden con recepcionistas reales (SC-004, SC-005,
# SC-006 son resultados de uso, no comportamiento del producto).
excluidos="SC-004 SC-005 SC-006"

[ -f "$spec" ] || { echo "No existe $spec" >&2; exit 2; }

# 1. Identificadores de la spec.
requisitos="$(grep -oE '\*\*(FR|SC)-[0-9]{3}' "$spec" | tr -d '*' | sort -u)"

# Escenarios: se numeran por su posición dentro de cada «### Historia de usuario N».
escenarios="$(awk '
  /^### Historia de usuario [0-9]+/ { match($0, /[0-9]+/); us = substr($0, RSTART, RLENGTH); dentro = 1; next }
  /^### / || /^## / { dentro = 0 }
  dentro && /^\*\*Escenarios de aceptaci/ { esc = 1; next }
  dentro && esc && /^[0-9]+\. / { split($0, p, "."); printf "US%s-AC%s\n", us, p[1] }
  /^---/ { esc = 0 }
' "$spec" | sort -u)"

# 2. Referencias en los tests (solo líneas que citan esta spec).
referencias="$(grep -rhE "\[$numero\]" "$raiz/backend/src/test" "$raiz/frontend/tests" 2>/dev/null \
  | grep -oE '\[(FR|SC)-[0-9]{3}\]|\[US[0-9]+-AC[0-9]+\]' | tr -d '[]' | sort -u || true)"

faltan=()
for id in $requisitos $escenarios; do
  case " $excluidos " in *" $id "*) continue ;; esac
  if ! grep -qx "$id" <<<"$referencias"; then
    faltan+=("$id")
  fi
done

total=$(( $(wc -w <<<"$requisitos $escenarios") ))
if [ ${#faltan[@]} -eq 0 ]; then
  echo "Trazabilidad completa: $total identificadores de $carpeta (excluidos: $excluidos)."
  exit 0
fi

echo "Sin test referenciado en $carpeta (${#faltan[@]} de $total):"
printf '  - %s\n' "${faltan[@]}"
exit 1
