#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APP_DIR="${ROOT_DIR}/arena-tui-mordant/build/install/arena-tui-mordant"
APP_BIN="${APP_DIR}/bin/arena-tui-mordant"

cd "${ROOT_DIR}"

"${ROOT_DIR}/gradlew" --console=plain :arena-tui-mordant:installDist

if [ ! -x "${APP_BIN}" ]; then
  printf "Expected launcher not found: %s\n" "${APP_BIN}" >&2
  exit 1
fi

exec "${APP_BIN}" "$@"
