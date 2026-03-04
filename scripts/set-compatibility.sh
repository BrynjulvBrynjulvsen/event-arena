#!/usr/bin/env bash

set -euo pipefail

REGISTRY_URL="${1:-http://localhost:8081}"
LEVEL="${2:-BACKWARD_TRANSITIVE}"

curl -sS -X PUT \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  --data "{\"compatibility\":\"${LEVEL}\"}" \
  "${REGISTRY_URL}/config" >/dev/null

printf "Schema Registry compatibility set to %s\n" "${LEVEL}"
