#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PID_DIR="${ROOT_DIR}/.demo"
LOG_DIR="${PID_DIR}/logs"
PID_FILE="${PID_DIR}/fighters.pids"

FIGHTER_A="${1:-balanced}"
FIGHTER_B="${2:-glass-cannon}"

mkdir -p "${LOG_DIR}"

if [ -f "${PID_FILE}" ]; then
  while IFS= read -r pid; do
    if [ -n "${pid}" ] && kill -0 "${pid}" 2>/dev/null; then
      kill "${pid}" 2>/dev/null || true
    fi
  done < "${PID_FILE}"
  rm -f "${PID_FILE}"
fi

nohup "${ROOT_DIR}/gradlew" :arena-fighter:bootRun --args="--arena.fighter.id=${FIGHTER_A}" > "${LOG_DIR}/${FIGHTER_A}.log" 2>&1 &
PID_A=$!

nohup "${ROOT_DIR}/gradlew" :arena-fighter:bootRun --args="--arena.fighter.id=${FIGHTER_B}" > "${LOG_DIR}/${FIGHTER_B}.log" 2>&1 &
PID_B=$!

printf "%s\n%s\n" "${PID_A}" "${PID_B}" > "${PID_FILE}"

printf "Started fighters: %s (pid=%s), %s (pid=%s)\n" "${FIGHTER_A}" "${PID_A}" "${FIGHTER_B}" "${PID_B}"
printf "Logs: %s and %s\n" "${LOG_DIR}/${FIGHTER_A}.log" "${LOG_DIR}/${FIGHTER_B}.log"
