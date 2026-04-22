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

RESPONSE=$(curl -sS -X POST \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  --data "{\"schemaType\":\"JSON\",\"schema\":${SCHEMA_CONTENT}}" \
  "${REGISTRY_URL}/compatibility/subjects/${SUBJECT}/versions/latest")

IS_COMPATIBLE=$(python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get("is_compatible", False))' "$RESPONSE")

if [ "$IS_COMPATIBLE" = "True" ]; then
  printf "Compatible: %s against subject %s\n" "$SCHEMA_FILE" "$SUBJECT"
  exit 0
fi

printf "Incompatible: %s against subject %s\n" "$SCHEMA_FILE" "$SUBJECT"
printf "Response: %s\n" "$RESPONSE"
exit 2
