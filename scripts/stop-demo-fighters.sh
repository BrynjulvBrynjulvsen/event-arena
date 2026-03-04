#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PID_FILE="${ROOT_DIR}/.demo/fighters.pids"

if [ ! -f "${PID_FILE}" ]; then
  printf "No fighter pid file found at %s\n" "${PID_FILE}"
  exit 0
fi

while IFS= read -r pid; do
  if [ -n "${pid}" ] && kill -0 "${pid}" 2>/dev/null; then
    kill "${pid}" 2>/dev/null || true
    printf "Stopped fighter process %s\n" "${pid}"
  fi
done < "${PID_FILE}"

rm -f "${PID_FILE}"
