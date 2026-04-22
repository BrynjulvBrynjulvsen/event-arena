#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "${ROOT_DIR}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf "Missing required command: %s\n" "$1" >&2
    exit 1
  fi
}

require_cmd docker
require_cmd curl

if ! docker compose version >/dev/null 2>&1; then
  printf "Docker Compose plugin is required (docker compose).\n" >&2
  exit 1
fi

"${ROOT_DIR}/scripts/build-images.sh"

echo "Starting Event Arena core stack..."
docker compose up -d

echo "Waiting for Schema Registry..."
for i in $(seq 1 40); do
  if curl -fsS http://localhost:8081/subjects >/dev/null 2>&1; then
    break
  fi

  if [ "${i}" -eq 40 ]; then
    printf "Schema Registry did not become ready in time.\n" >&2
    exit 1
  fi

  sleep 2
done

"${ROOT_DIR}/scripts/set-compatibility.sh"

enable_observability="no"
if [ -n "${EVENT_ARENA_ENABLE_OBSERVABILITY:-}" ]; then
  enable_observability="${EVENT_ARENA_ENABLE_OBSERVABILITY}"
elif [ -t 0 ]; then
  read -r -p "Enable observability stack (Prometheus + Grafana)? [y/N]: " answer
  case "${answer}" in
    y|Y|yes|YES)
      enable_observability="yes"
      ;;
  esac
fi

case "${enable_observability}" in
  y|Y|yes|YES|true|TRUE)
    docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d
    ;;
  *)
    ;;
esac

cat <<'OUT'

Event Arena is running.
- Engine: http://localhost:8080
- Observer UI: http://localhost:8090
- Kafbat UI: http://localhost:8085
OUT

case "${enable_observability}" in
  y|Y|yes|YES|true|TRUE)
  cat <<'OUT'
- Prometheus: http://localhost:9090/targets
- Grafana: http://localhost:3000 (admin/admin)
OUT
  ;;
esac

cat <<'OUT'

Trigger a match:
curl -X POST http://localhost:8080/matches -H "Content-Type: application/json" -d '{"seed":42}'
OUT
