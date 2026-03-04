#!/usr/bin/env bash

set -euo pipefail

if [ "$#" -lt 3 ]; then
  printf "Usage: %s <registry-url> <subject> <schema-file>\n" "$0"
  exit 1
fi

REGISTRY_URL="$1"
SUBJECT="$2"
SCHEMA_FILE="$3"

if [ ! -f "$SCHEMA_FILE" ]; then
  printf "Schema file not found: %s\n" "$SCHEMA_FILE"
  exit 1
fi

SCHEMA_CONTENT=$(python3 -c 'import json,sys; print(json.dumps(open(sys.argv[1]).read()))' "$SCHEMA_FILE")

curl -sS -X POST \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  --data "{\"schemaType\":\"JSON\",\"schema\":${SCHEMA_CONTENT}}" \
  "${REGISTRY_URL}/subjects/${SUBJECT}/versions"

printf "\nRegistered schema %s under subject %s\n" "$SCHEMA_FILE" "$SUBJECT"
