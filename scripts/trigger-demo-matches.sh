#!/usr/bin/env bash

set -euo pipefail

ENGINE_URL="${1:-http://localhost:8080}"
COUNT="${2:-1}"
INTERVAL_MS="${3:-500}"

is_integer() {
  case "$1" in
    ''|*[!0-9]*) return 1 ;;
    *) return 0 ;;
  esac
}

if ! is_integer "${COUNT}" || ! is_integer "${INTERVAL_MS}"; then
  printf "COUNT and INTERVAL_MS must be positive integers\n"
  exit 1
fi

for i in $(seq 1 "${COUNT}"); do
  curl -sS -X POST "${ENGINE_URL}/matches" \
    -H "Content-Type: application/json" \
    -d '{}' \
    | python3 -m json.tool

  if [ "${i}" -lt "${COUNT}" ]; then
    seconds=$((INTERVAL_MS / 1000))
    millis=$((INTERVAL_MS % 1000))
    sleep "${seconds}.$(printf "%03d" "${millis}")"
  fi
done
