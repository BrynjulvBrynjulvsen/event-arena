#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT_DIR}"

image_tag="local"
repo_prefix="event-arena"

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
Usage: ./scripts/build-images.sh [service...]

Services:
  engine
  fighter
  fighter-balanced
  fighter-glass-cannon
  replay
  observer-gateway
  all

Examples:
  ./scripts/build-images.sh
  ./scripts/build-images.sh engine
  ./scripts/build-images.sh fighter-balanced
  ./scripts/build-images.sh engine replay
OUT
}

build_targets=()

if [ "$#" -eq 0 ]; then
  build_targets=(engine fighter replay observer-gateway)
else
  for raw_target in "$@"; do
    case "${raw_target}" in
      all)
        build_targets=(engine fighter replay observer-gateway)
        break
        ;;
      engine)
        canonical="engine"
        ;;
      fighter|fighter-balanced|fighter-glass-cannon)
        canonical="fighter"
        ;;
      replay)
        canonical="replay"
        ;;
      observer-gateway)
        canonical="observer-gateway"
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        printf "Unknown service: %s\n" "${raw_target}" >&2
        usage >&2
        exit 1
        ;;
    esac

    if ! contains "${canonical}" "${build_targets[@]-}"; then
      build_targets+=("${canonical}")
    fi
  done
fi

gradle_tasks=()
for target in "${build_targets[@]}"; do
  case "${target}" in
    engine)
      gradle_tasks+=(":arena-engine:bootJar")
      ;;
    fighter)
      gradle_tasks+=(":arena-fighter:bootJar")
      ;;
    replay)
      gradle_tasks+=(":arena-replay-cli:bootJar")
      ;;
    observer-gateway)
      gradle_tasks+=(":arena-observer-gateway:bootJar")
      ;;
  esac
done

printf "Building JVM artifacts with Gradle for: %s\n" "${build_targets[*]}"
./gradlew "${gradle_tasks[@]}"

resolve_boot_jar() {
  local module="$1"
  local jar

  jar="$(find "${ROOT_DIR}/${module}/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n1)"
  if [ -z "${jar}" ]; then
    printf "No boot jar found for module %s\n" "${module}" >&2
    exit 1
  fi

  printf "%s" "${jar#${ROOT_DIR}/}"
}

build_image() {
  local module="$1"
  local image_name="$2"
  local jar_path

  jar_path="$(resolve_boot_jar "${module}")"

  printf "Building image %s/%s:%s from %s\n" "${repo_prefix}" "${image_name}" "${image_tag}" "${jar_path}"
  docker build \
    -f "${module}/Dockerfile" \
    --build-arg "JAR_FILE=${jar_path}" \
    -t "${repo_prefix}/${image_name}:${image_tag}" \
    .
}

for target in "${build_targets[@]}"; do
  case "${target}" in
    engine)
      build_image "arena-engine" "arena-engine"
      ;;
    fighter)
      build_image "arena-fighter" "arena-fighter"
      ;;
    replay)
      build_image "arena-replay-cli" "arena-replay-cli"
      ;;
    observer-gateway)
      build_image "arena-observer-gateway" "arena-observer-gateway"
      ;;
  esac
done

printf "Built requested workshop images with tag '%s': %s\n" "${image_tag}" "${build_targets[*]}"
