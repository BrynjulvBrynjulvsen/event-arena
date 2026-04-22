#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT_DIR}"

contains() {
  local needle="$1"
  shift

  for item in "$@"; do
    if [ "${item}" = "${needle}" ]; then
      return 0
    fi
  done

  return 1
}

usage() {
  cat <<'OUT'
Usage: ./scripts/rebuild-and-restart.sh [service...]

Services:
  engine
  fighter
  fighter-balanced
  fighter-glass-cannon
  replay
  observer-gateway
  all

Examples:
  ./scripts/rebuild-and-restart.sh
  ./scripts/rebuild-and-restart.sh engine
  ./scripts/rebuild-and-restart.sh fighter-balanced
  ./scripts/rebuild-and-restart.sh fighter
OUT
}

build_args=()
compose_services=()

if [ "$#" -eq 0 ]; then
  compose_services=(
    engine
    fighter-balanced
    fighter-glass-cannon
    replay
    observer-gateway
  )
else
  for raw_target in "$@"; do
    case "${raw_target}" in
      all)
        compose_services=(
          engine
          fighter-balanced
          fighter-glass-cannon
          replay
          observer-gateway
        )
        build_args=(all)
        break
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      engine|fighter|fighter-balanced|fighter-glass-cannon|replay|observer-gateway)
        build_args+=("${raw_target}")
        ;;
      *)
        printf "Unknown service: %s\n" "${raw_target}" >&2
        usage >&2
        exit 1
        ;;
    esac

    case "${raw_target}" in
      engine)
        if ! contains "engine" "${compose_services[@]-}"; then
          compose_services+=("engine")
        fi
        ;;
      fighter)
        if ! contains "fighter-balanced" "${compose_services[@]-}"; then
          compose_services+=("fighter-balanced")
        fi
        if ! contains "fighter-glass-cannon" "${compose_services[@]-}"; then
          compose_services+=("fighter-glass-cannon")
        fi
        ;;
      fighter-balanced)
        if ! contains "fighter-balanced" "${compose_services[@]-}"; then
          compose_services+=("fighter-balanced")
        fi
        ;;
      fighter-glass-cannon)
        if ! contains "fighter-glass-cannon" "${compose_services[@]-}"; then
          compose_services+=("fighter-glass-cannon")
        fi
        ;;
      replay)
        if ! contains "replay" "${compose_services[@]-}"; then
          compose_services+=("replay")
        fi
        ;;
      observer-gateway)
        if ! contains "observer-gateway" "${compose_services[@]-}"; then
          compose_services+=("observer-gateway")
        fi
        ;;
    esac
  done
fi

if [ "${#build_args[@]}" -eq 0 ]; then
  "${ROOT_DIR}/scripts/build-images.sh"
else
  "${ROOT_DIR}/scripts/build-images.sh" "${build_args[@]}"
fi

docker compose up -d --force-recreate "${compose_services[@]}"

"${ROOT_DIR}/scripts/set-compatibility.sh"

printf "Rebuilt and restarted services: %s\n" "${compose_services[*]}"
